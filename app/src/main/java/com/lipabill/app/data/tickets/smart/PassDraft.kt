package com.lipabill.app.data.tickets.smart

import com.lipabill.app.data.model.TicketBarcodeFormat
import com.lipabill.app.data.model.TicketSource
import com.lipabill.app.data.tickets.TicketImport
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class PassKind(val label: String) {
    AIRLINE("Airline boarding pass"),
    RAIL("SGR train ticket"),
    EVENT("Event ticket")
}

/** Fields the review screen shows, in display order. */
enum class PassField(val label: String) {
    TITLE("Title"),
    HOLDER("Passenger"),
    ORIGIN("From"),
    DESTINATION("To"),
    FLIGHT("Airline · Flight"),
    VENUE("Venue"),
    DATE("Date"),
    TIME("Time"),
    SEAT("Seat"),
    CABIN("Class"),
    BOOKING_REF("Booking ref");

    companion object {
        fun wantedFor(kind: PassKind): List<PassField> = when (kind) {
            PassKind.AIRLINE -> listOf(HOLDER, ORIGIN, DESTINATION, FLIGHT, DATE, TIME, SEAT, CABIN, BOOKING_REF)
            PassKind.RAIL -> listOf(HOLDER, ORIGIN, DESTINATION, DATE, TIME, SEAT, CABIN, BOOKING_REF)
            PassKind.EVENT -> listOf(TITLE, VENUE, DATE, TIME, SEAT, BOOKING_REF)
        }
    }
}

/** Where a value came from; the review screen shows it as a chip. */
enum class FieldSource(val label: String) {
    PKPASS("Pass file"),
    BARCODE("Barcode"),
    TEXT("Text"),
    AI("On-device AI"),
    USER("You")
}

data class DraftField(
    val value: String,
    val source: FieldSource,
    /** Why the person should double-check it; null when nothing looks off. */
    val warning: String? = null
)

enum class NameCheck { MATCH, MISMATCH, UNKNOWN }

/**
 * A pass the phone has read but not saved. Nothing is stored until the person
 * confirms on the review screen.
 */
data class PassDraft(
    val kind: PassKind,
    val fields: Map<PassField, DraftField>,
    val barcodeValue: String?,
    val barcodeFormat: TicketBarcodeFormat = TicketBarcodeFormat.QR_CODE,
    val source: TicketSource,
    /** The existing parser's result, kept so saving behaves exactly like today's import. */
    val base: TicketImport? = null,
    val nameCheck: NameCheck = NameCheck.UNKNOWN,
    /** Problems that block saving (e.g. no scannable code). */
    val blockers: List<String> = emptyList(),
    /** True when Gemini Nano filled at least one field. */
    val usedAi: Boolean = false
) {
    operator fun get(field: PassField): String? = fields[field]?.value?.takeIf { it.isNotBlank() }

    val canSave: Boolean get() = blockers.isEmpty() && !barcodeValue.isNullOrBlank()

    fun withUserValue(field: PassField, value: String): PassDraft {
        val next = if (value.isBlank()) fields - field else fields + (field to DraftField(value.trim(), FieldSource.USER))
        return copy(fields = next)
    }

    fun countBySource(): Map<FieldSource, Int> = fields.values.groupingBy { it.source }.eachCount()
}

private val SHORT_SEAT = Regex("[0-9]{1,3}[A-Za-z]?")
private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

fun formatDraftDate(date: LocalDate): String = date.format(DATE_FORMAT)
fun formatDraftTime(time: LocalTime): String = time.format(TIME_FORMAT)

fun parseDraftDate(value: String?): LocalDate? =
    value?.trim()?.let { runCatching { LocalDate.parse(it, DATE_FORMAT) }.getOrNull() }

fun parseDraftTime(value: String?): LocalTime? =
    value?.trim()?.let { runCatching { LocalTime.parse(it, TIME_FORMAT) }.getOrNull() }

/** Title shown in Passes, built from what the draft knows. */
fun PassDraft.displayTitle(): String {
    this[PassField.TITLE]?.let { return it }
    val route = listOfNotNull(this[PassField.ORIGIN], this[PassField.DESTINATION])
        .takeIf { it.size == 2 }
        ?.joinToString(" → ")
    return when (kind) {
        PassKind.AIRLINE -> listOfNotNull(route, this[PassField.FLIGHT]).joinToString(" · ").ifEmpty { kind.label }
        PassKind.RAIL -> route?.let { "SGR $it" } ?: kind.label
        PassKind.EVENT -> kind.label
    }
}

/**
 * The draft as a normal import. When an existing parser produced it ([PassDraft.base]),
 * only the fields the person could edit are overridden, so saving matches today's flow.
 */
fun PassDraft.toTicketImport(zone: ZoneId): TicketImport? {
    val code = barcodeValue?.takeIf { it.isNotBlank() } ?: return null
    val date = parseDraftDate(this[PassField.DATE])
    val time = parseDraftTime(this[PassField.TIME])
    val startsAt = date?.let { (time?.let(it::atTime) ?: it.atStartOfDay()).atZone(zone).toInstant().toEpochMilli() }
    val route = listOfNotNull(this[PassField.ORIGIN], this[PassField.DESTINATION])
        .takeIf { it.size == 2 }
        ?.joinToString(" → ")
    // "14A" reads better as "Seat 14A"; "Coach 5 · Seat 42" already says what it is.
    val seatLabel = this[PassField.SEAT]?.let { if (SHORT_SEAT.matches(it)) "Seat $it" else it }
    val seat = listOfNotNull(seatLabel, this[PassField.CABIN])
        .joinToString(" · ")
        .ifEmpty { null }
    val holder = this[PassField.HOLDER]
    val notes = listOfNotNull(
        holder?.let { "Passenger: $it" },
        this[PassField.FLIGHT]?.takeIf { kind == PassKind.AIRLINE }?.let { "Flight: $it" }
    ).joinToString("\n").ifEmpty { null }

    val start = base ?: TicketImport(
        title = displayTitle(),
        barcodeValue = code,
        barcodeFormat = barcodeFormat,
        source = source,
        expectsBoardingPass = false,
        hasBoardingPass = true
    )
    return start.copy(
        title = this[PassField.TITLE] ?: base?.title ?: displayTitle(),
        venue = this[PassField.VENUE] ?: route ?: start.venue,
        startsAtMillis = startsAt ?: start.startsAtMillis,
        seatOrTier = seat ?: start.seatOrTier,
        orderId = this[PassField.BOOKING_REF] ?: start.orderId,
        notes = listOfNotNull(start.notes, notes).distinct().joinToString("\n").ifEmpty { null },
        barcodeValue = code
    )
}
