package com.lipabill.app.data.tickets

import android.content.Context
import android.net.Uri
import com.lipabill.app.data.repository.PassDocumentRepository
import com.lipabill.app.data.tickets.intelligence.DocumentClassifier
import com.lipabill.app.data.tickets.intelligence.PassExtraction
import com.lipabill.app.data.tickets.intelligence.PassFieldReader
import com.lipabill.app.data.tickets.intelligence.PassIssue
import com.lipabill.app.data.tickets.intelligence.PassValidator
import com.lipabill.app.data.tickets.intelligence.ReadEvidence

/** A read ticket waiting for review: the draft, where it came from, and what to check. */
data class PassDraft(
    val draft: TicketImport,
    /** Stored original, or null for Wallet passes (their file is the pass itself). */
    val documentId: String?,
    val extraction: PassExtraction,
    val issues: List<PassIssue>
)

/** The same file was saved before; open that pass instead of saving a twin. */
class AlreadySavedException(val ticketId: Long) :
    IllegalArgumentException("That ticket is already saved.")

/**
 * Import pipeline: keep the original → read it (PDF text, OCR, barcodes) → classify →
 * score each field → validate. Nothing here saves a pass; review does that.
 */
class PassDocumentReader(private val documents: PassDocumentRepository) {

    suspend fun read(context: Context, uri: Uri, kind: TicketDocumentKind): PassDraft {
        if (kind == TicketDocumentKind.PKPASS || TicketDocumentImporter.isPkPass(context, uri)) {
            val draft = TicketDocumentImporter.import(context, uri, TicketDocumentKind.PKPASS)
            val classification = DocumentClassifier.classify("", hasBarcode = true, hint = TicketDocumentKind.PKPASS)
            val extraction = PassFieldReader.read(draft, ReadEvidence(fromPdfText = false, barcodeFromPixels = false), classification)
            return PassDraft(draft, null, extraction, PassValidator.validate(draft, classification.kind))
        }

        val stored = documents.store(uri)
        val linked = stored.document.ticketId
        if (stored.duplicate && linked != null) throw AlreadySavedException(linked)
        val documentId = stored.document.id
        val analyzed = try {
            ETicketAnalyzer.analyzeDetailed(context, uri, kind)
        } catch (t: Throwable) {
            // Unreadable or unsupported (e.g. a flight): don't keep a file nobody will use.
            if (!stored.duplicate) documents.discardDraft(documentId)
            throw t
        }
        val classification = DocumentClassifier.classify(
            text = analyzed.read.text,
            hasBarcode = analyzed.read.barcodes.isNotEmpty(),
            hint = kind
        )
        val extraction = PassFieldReader.read(
            analyzed.draft,
            ReadEvidence(fromPdfText = analyzed.read.fromPdfText, barcodeFromPixels = analyzed.codeFromPixels),
            classification
        )
        documents.recordExtraction(documentId, extraction)
        return PassDraft(
            draft = analyzed.draft,
            documentId = documentId,
            extraction = extraction,
            issues = PassValidator.validate(analyzed.draft, classification.kind)
        )
    }

    /**
     * Re-reads a stored original. Uses the saved MIME so a decrypted
     * `file://…/pass_previews/<id>.pdf|.img` still routes PDF vs image correctly.
     */
    suspend fun rereadStored(
        context: Context,
        documentId: String,
        kind: TicketDocumentKind = TicketDocumentKind.AUTO
    ): PassDraft {
        val opened = documents.openDocumentCopy(documentId)
            ?: throw IllegalArgumentException("That original is no longer on this phone.")
        return try {
            val uri = Uri.fromFile(opened.file)
            val analyzed = ETicketAnalyzer.analyzeDetailed(context, uri, kind, opened.mimeType)
            val classification = DocumentClassifier.classify(
                text = analyzed.read.text,
                hasBarcode = analyzed.read.barcodes.isNotEmpty(),
                hint = kind
            )
            val extraction = PassFieldReader.read(
                analyzed.draft,
                ReadEvidence(
                    fromPdfText = analyzed.read.fromPdfText,
                    barcodeFromPixels = analyzed.codeFromPixels
                ),
                classification
            )
            documents.recordExtraction(documentId, extraction)
            PassDraft(
                draft = analyzed.draft,
                documentId = documentId,
                extraction = extraction,
                issues = PassValidator.validate(analyzed.draft, classification.kind)
            )
        } finally {
            opened.file.delete()
        }
    }
}
