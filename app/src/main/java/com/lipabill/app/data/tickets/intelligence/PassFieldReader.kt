package com.lipabill.app.data.tickets.intelligence

import com.lipabill.app.data.model.TicketSource
import com.lipabill.app.data.model.firstNotesValue
import com.lipabill.app.data.model.notesValue
import com.lipabill.app.data.tickets.EventPresets
import com.lipabill.app.data.tickets.EventTicketSms
import com.lipabill.app.data.tickets.TicketImport

/** How the text behind a draft was obtained. Counts only — no ticket content. */
data class ReadEvidence(
    val fromPdfText: Boolean,
    val barcodeFromPixels: Boolean,
    /** Fields that came from an explicit label ("Venue: X") rather than inference. */
    val labeledFields: Set<PassField> = emptySet(),
    /** Mean OCR line confidence 0–1 when the read was OCR; null for PDF text / unknown. */
    val ocrLineConfidence: Float? = null
)

/**
 * Attaches confidence and provenance to the fields an existing parser produced.
 * Deterministic: printed text from a PDF's text layer beats OCR, a code decoded
 * from pixels is exact, and anything missing stays null rather than guessed.
 */
object PassFieldReader {

    fun read(
        draft: TicketImport,
        evidence: ReadEvidence,
        classification: ClassificationResult
    ): PassExtraction {
        val textSource = when {
            draft.source == TicketSource.PKPASS -> ExtractionSource.WALLET_PASS
            evidence.fromPdfText -> ExtractionSource.PDF_TEXT
            else -> ExtractionSource.OCR
        }
        val base = when (textSource) {
            ExtractionSource.WALLET_PASS -> 0.95f
            ExtractionSource.PDF_TEXT -> 0.85f
            else -> {
                val ocr = evidence.ocrLineConfidence
                if (ocr != null) (0.55f + ocr * 0.35f).coerceIn(0.50f, 0.85f) else 0.72f
            }
        }
        fun field(
            key: PassField,
            value: String?,
            confidence: Float = base,
            source: ExtractionSource = textSource
        ): ExtractedField {
            val labeled = key in evidence.labeledFields
            val conf = when {
                value.isNullOrBlank() -> 0f
                labeled -> confidence
                else -> (confidence - 0.12f).coerceAtLeast(0.40f)
            }
            return ExtractedField(value?.trim()?.ifBlank { null }, conf, source)
        }

        val code = draft.barcodeValue.takeUnless { it.isBlank() || EventTicketSms.isPending(it) }
        val codeField = when {
            code == null -> ExtractedField(null, 0f, ExtractionSource.BARCODE)
            draft.source == TicketSource.PKPASS -> ExtractedField(code, 0.99f, ExtractionSource.WALLET_PASS)
            evidence.barcodeFromPixels -> ExtractedField(code, 0.99f, ExtractionSource.BARCODE)
            // A code typed out in text (no symbol found) is easy to misread.
            else -> ExtractedField(code, base - 0.1f, textSource)
        }
        // Dates are parsed from loose text ("Sat 14 Dec"), so they always get a second look.
        val dateConfidence = minOf(base, 0.8f)
        val title = draft.title.takeUnless { EventPresets.isGenericTitle(it) }
        return PassExtraction(
            classification = classification,
            fields = mapOf(
                PassField.TITLE to field(PassField.TITLE, title),
                PassField.VENUE to field(PassField.VENUE, draft.venue),
                PassField.STARTS_AT to field(PassField.STARTS_AT, draft.startsAtMillis?.toString(), dateConfidence),
                PassField.ENDS_AT to field(PassField.ENDS_AT, draft.notes.notesValue("Ends"), dateConfidence),
                PassField.SEAT_OR_TIER to field(PassField.SEAT_OR_TIER, draft.seatOrTier),
                PassField.TIER to field(PassField.TIER, draft.notes.firstNotesValue("Tier")),
                PassField.GUEST to field(PassField.GUEST, draft.notes.firstNotesValue("Guest", "Attendee", "Name")),
                // Booking references must be exact — a misread one is worse than none.
                PassField.ORDER_ID to field(PassField.ORDER_ID, draft.orderId, base - 0.05f),
                PassField.BARCODE to codeField
            )
        )
    }

    /**
     * After review: any field the user changed is theirs (USER_INPUT, fully trusted);
     * untouched fields keep how they were read.
     */
    fun withEdits(before: PassExtraction, edited: TicketImport): PassExtraction {
        val after = read(edited, ReadEvidence(fromPdfText = false, barcodeFromPixels = false), before.classification).fields
        return before.copy(
            fields = before.fields.mapValues { (key, old) ->
                val value = after[key]?.value
                if (value == old.value) {
                    old
                } else {
                    ExtractedField(value, if (value == null) 0f else 1f, ExtractionSource.USER_INPUT, requiresReview = false)
                }
            }
        )
    }
}
