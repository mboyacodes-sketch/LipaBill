package com.lipabill.app.data.tickets

import android.content.Context
import android.net.Uri
import com.lipabill.app.data.repository.PassDocumentRepository
import com.lipabill.app.data.tickets.intelligence.DocumentClassifier
import com.lipabill.app.data.tickets.intelligence.PassExtraction
import com.lipabill.app.data.tickets.intelligence.PassFieldReader
import com.lipabill.app.data.tickets.intelligence.PassIssue
import com.lipabill.app.data.tickets.intelligence.PassKind
import com.lipabill.app.data.tickets.intelligence.PassLifecycleResolver
import com.lipabill.app.data.tickets.intelligence.PassValidator
import com.lipabill.app.data.tickets.intelligence.ReadEvidence

/** A read ticket waiting for review: the draft, where it came from, and what to check. */
data class PassDraft(
    val draft: TicketImport,
    /** Stored original, or null for Wallet passes (their file is the pass itself). */
    val documentId: String?,
    val extraction: PassExtraction,
    val issues: List<PassIssue>,
    /** Other gate codes in the same file (one ticket per page); review can save each as a pass. */
    val extraCodes: List<ETicketTextParser.FoundBarcode> = emptyList()
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
            return finish(draft, null, extraction, classification.kind, text = "", extraCodes = emptyList())
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
        return finish(
            analyzed.draft,
            documentId,
            extraction,
            classification.kind,
            analyzed.read.text,
            extraCodes = extraCodes(analyzed)
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
            finish(
                analyzed.draft,
                documentId,
                extraction,
                classification.kind,
                analyzed.read.text,
                extraCodes = extraCodes(analyzed)
            )
        } finally {
            opened.file.delete()
        }
    }

    private fun finish(
        draft: TicketImport,
        documentId: String?,
        extraction: PassExtraction,
        kind: PassKind,
        text: String,
        extraCodes: List<ETicketTextParser.FoundBarcode>
    ): PassDraft {
        val lifecycle = PassLifecycleResolver.resolve(text, draft.startsAtMillis)
        val withStatus = draft.copy(status = lifecycle.status)
        val issues = PassValidator.validate(
            withStatus,
            kind,
            sourceText = text,
            distinctTicketCodes = 1 + extraCodes.size
        ).toMutableList()
        lifecycle.issue?.let { if (it !in issues) issues += it }
        return PassDraft(withStatus, documentId, extraction, issues, extraCodes)
    }

    /** Only codes of the gate code's own kind count, so an app-download QR is not a second ticket. */
    private fun extraCodes(analyzed: ETicketAnalyzer.AnalyzedTicket): List<ETicketTextParser.FoundBarcode> =
        ETicketTextParser.extraTicketCodes(analyzed.read.barcodes, analyzed.draft.barcodeValue)
}
