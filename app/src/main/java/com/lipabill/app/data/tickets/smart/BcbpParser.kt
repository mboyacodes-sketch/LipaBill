package com.lipabill.app.data.tickets.smart

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

/** First leg of an IATA Bar Coded Boarding Pass (Resolution 792), the barcode on airline boarding passes. */
data class BoardingPassBarcode(
    val passengerName: String,
    val bookingRef: String,
    val origin: String,
    val destination: String,
    val carrier: String,
    val flightNumber: String,
    val date: LocalDate,
    val cabin: String,
    val seat: String?,
    val checkInSequence: String?
) {
    /** "KQ 100". */
    val flight: String get() = "$carrier $flightNumber"
}

/**
 * Decodes the mandatory first-leg block of a BCBP string. Fixed widths, so no guessing:
 * `M` + legs + name(20) + e-ticket flag + PNR(7) + from(3) + to(3) + carrier(3) + flight(5)
 * + julian date(3) + compartment(1) + seat(4) + check-in seq(5) + status(1) + size(2).
 */
object BcbpParser {

    private const val MANDATORY_LENGTH = 60
    private val AIRPORT = Regex("[A-Z]{3}")
    private val CARRIER = Regex("[A-Z0-9]{2,3}")
    private val TITLES = setOf("MR", "MRS", "MS", "MISS", "MSTR", "DR", "PROF", "CHD", "INF")

    fun looksLikeBcbp(raw: String?): Boolean {
        val text = raw ?: return false
        return text.length >= MANDATORY_LENGTH && text[0] == 'M' && text[1] in '1'..'4'
    }

    /**
     * @param today picks the year: the Julian day only carries day-of-year, so the
     *   flight date is the one closest to [today].
     */
    fun parse(raw: String, today: LocalDate): BoardingPassBarcode? {
        if (!looksLikeBcbp(raw)) return null
        val name = raw.substring(2, 22)
        val pnr = raw.substring(23, 30).trim()
        val from = raw.substring(30, 33).trim()
        val to = raw.substring(33, 36).trim()
        val carrier = raw.substring(36, 39).trim()
        val flightRaw = raw.substring(39, 44).trim()
        val julian = raw.substring(44, 47).trim().toIntOrNull() ?: return null
        val compartment = raw[47]
        val seatRaw = raw.substring(48, 52).trim()
        val sequenceRaw = raw.substring(52, 57).trim()

        if (!AIRPORT.matches(from) || !AIRPORT.matches(to)) return null
        if (!CARRIER.matches(carrier)) return null
        if (pnr.isEmpty() || julian !in 1..366) return null
        val flightNumber = flightRaw.trimStart('0').ifEmpty { return null }
        val date = julianDate(julian, today) ?: return null

        return BoardingPassBarcode(
            passengerName = displayName(name),
            bookingRef = pnr,
            origin = from,
            destination = to,
            carrier = carrier,
            flightNumber = flightNumber,
            date = date,
            cabin = cabinName(compartment),
            seat = seatRaw.trimStart('0').takeIf { it.isNotEmpty() },
            checkInSequence = sequenceRaw.trimStart('0').takeIf { it.isNotEmpty() }
        )
    }

    /** "KAMAU/JOHN MR" → "John Kamau". */
    internal fun displayName(raw: String): String {
        val trimmed = raw.trim()
        val surname = trimmed.substringBefore('/').trim()
        val given = trimmed.substringAfter('/', "").trim()
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() && it.uppercase(Locale.US) !in TITLES }
            .joinToString(" ")
        return listOf(given, surname)
            .filter { it.isNotEmpty() }
            .joinToString(" ") { part -> part.lowercase(Locale.US).split(" ").joinToString(" ") { titleCase(it) } }
    }

    private fun titleCase(word: String): String =
        word.split('-').joinToString("-") { piece -> piece.replaceFirstChar { it.uppercase(Locale.US) } }

    internal fun cabinName(code: Char): String = when (code.uppercaseChar()) {
        'F', 'A', 'P' -> "First"
        'J', 'C', 'D', 'I', 'Z', 'R' -> "Business"
        'W' -> "Premium economy"
        else -> "Economy"
    }

    internal fun julianDate(dayOfYear: Int, today: LocalDate): LocalDate? =
        (today.year - 1..today.year + 1)
            .mapNotNull { year ->
                runCatching { LocalDate.ofYearDay(year, dayOfYear) }.getOrNull()
            }
            .minByOrNull { abs(ChronoUnit.DAYS.between(today, it)) }
}
