package com.lipabill.app.data.tickets.smart

import com.lipabill.app.data.model.TicketBarcodeFormat
import com.lipabill.app.data.model.TicketSource
import com.lipabill.app.data.tickets.BookingConfirmationParser
import com.lipabill.app.data.tickets.ETicketTextParser
import com.lipabill.app.data.tickets.TicketDocumentKind
import com.lipabill.app.data.tickets.TicketImport
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/** What the phone collected before reading: text, barcodes, or a parsed .pkpass. */
data class PassInput(
    val text: String = "",
    val barcodes: List<ETicketTextParser.FoundBarcode> = emptyList(),
    val pkpass: TicketImport? = null,
    /** How the person added it; becomes the saved ticket's source. */
    val source: TicketSource = TicketSource.E_TICKET
)

/**
 * Reads a pass in a fixed order, most reliable first:
 * 1. .pkpass data, 2. airline boarding-pass barcode (BCBP), 3. the existing SGR /
 * booking-SMS / e-ticket parsers, 4. Gemini Nano for fields still missing.
 * Earlier steps always win; AI never overwrites a value.
 */
class SmartPassReader(
    private val ai: PassAi = NoPassAi,
    private val zone: ZoneId = ZoneId.systemDefault()
) {

    suspend fun read(input: PassInput, today: LocalDate, holderName: String?): PassDraft {
        var draft = rulesDraft(input, today)
        val text = input.text.trim()
        val missing = PassField.wantedFor(draft.kind).filter { draft[it] == null }
        if (text.isNotEmpty() && missing.isNotEmpty() && ai.isReady()) {
            val answer = ai.answer(PassAiPrompt.build(text, draft.kind, missing))
            val found = PassAiPrompt.parse(answer, text, missing)
            if (found.isNotEmpty()) {
                draft = draft.copy(
                    fields = draft.fields + found.mapValues { (_, v) -> DraftField(v, FieldSource.AI) },
                    usedAi = true
                )
            }
        }
        return PassValidator.validate(draft, today, holderName)
    }

    /** Everything the deterministic steps can read. No AI, no Android: unit-testable. */
    fun rulesDraft(input: PassInput, today: LocalDate): PassDraft {
        input.pkpass?.let { pass ->
            val bcbp = BcbpParser.parse(pass.barcodeValue, today)
            val fields = fieldsFrom(pass, FieldSource.PKPASS) + (bcbp?.let { fieldsFrom(it, FieldSource.BARCODE) } ?: emptyMap())
            return PassDraft(
                kind = if (bcbp != null) PassKind.AIRLINE else PassKind.EVENT,
                fields = fields,
                barcodeValue = pass.barcodeValue,
                barcodeFormat = pass.barcodeFormat,
                source = TicketSource.PKPASS,
                base = pass
            )
        }

        val boarding = input.barcodes.firstNotNullOfOrNull { code ->
            BcbpParser.parse(code.value, today)?.let { code to it }
        }
        if (boarding != null) {
            val (code, pass) = boarding
            return PassDraft(
                kind = PassKind.AIRLINE,
                fields = fieldsFrom(pass, FieldSource.BARCODE),
                barcodeValue = code.value,
                barcodeFormat = code.format.takeIf { it != TicketBarcodeFormat.QR_CODE } ?: TicketBarcodeFormat.PDF_417,
                source = input.source
            )
        }

        val text = input.text.trim()
        if (text.isNotEmpty() && BookingConfirmationParser.looksLikeConfirmation(text)) {
            val parsed = runCatching { BookingConfirmationParser.parse(text) }.getOrNull()
            if (parsed != null) {
                return PassDraft(
                    kind = PassKind.RAIL,
                    fields = fieldsFrom(parsed, FieldSource.TEXT),
                    barcodeValue = parsed.barcodeValue,
                    barcodeFormat = parsed.barcodeFormat,
                    source = parsed.source,
                    base = parsed
                )
            }
        }

        val kind = guessKind(text)
        val parsed = if (text.isNotEmpty() || input.barcodes.isNotEmpty()) {
            // EVENT is what today's "Event ticket" import uses: SGR layout first, then generic.
            // SGR_TICKET means "attach to an existing booking", which this path never does.
            runCatching {
                ETicketTextParser.parse(text, input.barcodes, kind = TicketDocumentKind.EVENT)
            }.getOrNull()
        } else {
            null
        }
        val fallbackCode = input.barcodes.firstOrNull()
        return PassDraft(
            kind = kind,
            fields = parsed?.let { fieldsFrom(it, FieldSource.TEXT) } ?: emptyMap(),
            barcodeValue = parsed?.barcodeValue ?: fallbackCode?.value,
            barcodeFormat = parsed?.barcodeFormat ?: fallbackCode?.format ?: TicketBarcodeFormat.QR_CODE,
            source = input.source,
            base = parsed
        )
    }

    internal fun guessKind(text: String): PassKind {
        val lower = text.lowercase(Locale.US)
        return when {
            listOf("madaraka", "sgr", "terminus", "coach").any { it in lower } -> PassKind.RAIL
            listOf("boarding pass", "flight", "gate", "pnr", "airline").any { it in lower } -> PassKind.AIRLINE
            else -> PassKind.EVENT
        }
    }

    private fun fieldsFrom(pass: BoardingPassBarcode, source: FieldSource): Map<PassField, DraftField> = buildMap {
        put(PassField.HOLDER, DraftField(pass.passengerName, source))
        put(PassField.ORIGIN, DraftField(pass.origin, source))
        put(PassField.DESTINATION, DraftField(pass.destination, source))
        put(PassField.FLIGHT, DraftField(pass.flight, source))
        put(PassField.DATE, DraftField(formatDraftDate(pass.date), source))
        put(PassField.CABIN, DraftField(pass.cabin, source))
        put(PassField.BOOKING_REF, DraftField(pass.bookingRef, source))
        pass.seat?.let { put(PassField.SEAT, DraftField(it, source)) }
    }

    private fun fieldsFrom(ticket: TicketImport, source: FieldSource): Map<PassField, DraftField> = buildMap {
        ticket.title.takeIf { it.isNotBlank() }?.let { put(PassField.TITLE, DraftField(it, source)) }
        ticket.venue?.takeIf { it.isNotBlank() }?.let { venue ->
            // Existing parsers write routes as "From → To"; anything else is a venue name.
            val stops = venue.split("→").map { it.trim() }.filter { it.isNotEmpty() }
            if (stops.size == 2) {
                put(PassField.ORIGIN, DraftField(stops[0], source))
                put(PassField.DESTINATION, DraftField(stops[1], source))
            } else {
                put(PassField.VENUE, DraftField(venue, source))
            }
        }
        ticket.startsAtMillis?.let { millis ->
            val at = Instant.ofEpochMilli(millis).atZone(zone)
            put(PassField.DATE, DraftField(formatDraftDate(at.toLocalDate()), source))
            if (at.toLocalTime().let { it.hour != 0 || it.minute != 0 }) {
                put(PassField.TIME, DraftField(formatDraftTime(at.toLocalTime()), source))
            }
        }
        ticket.seatOrTier?.takeIf { it.isNotBlank() }?.let { put(PassField.SEAT, DraftField(it, source)) }
        ticket.orderId?.takeIf { it.isNotBlank() }?.let { put(PassField.BOOKING_REF, DraftField(it, source)) }
    }
}

/** What happens to pasted text: SGR booking SMS saves straight away, as the SGR button does. */
enum class PasteRoute { SGR_CONFIRMATION, REVIEW }

fun pasteRoute(text: String): PasteRoute =
    if (BookingConfirmationParser.looksLikeConfirmation(text)) PasteRoute.SGR_CONFIRMATION else PasteRoute.REVIEW
