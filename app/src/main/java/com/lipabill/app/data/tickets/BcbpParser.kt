package com.lipabill.app.data.tickets

import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

/**
 * Minimal IATA BCBP (boarding pass barcode) parser + builder for PDF417 / Aztec payloads.
 * Spec is large; we only pull / emit passenger, route, flight, date, seat, PNR.
 */
object BcbpParser {

    data class BoardingPass(
        val passengerName: String?,
        val pnr: String?,
        val from: String?,
        val to: String?,
        val flightNumber: String?,
        val seat: String?,
        val departDateMillis: Long?
    )

    fun parse(raw: String): BoardingPass? {
        val s = raw.trim().uppercase(Locale.US)
        if (s.length < 60) return null
        if (!(s.startsWith("M1") || s.startsWith("M2") || s.startsWith("M3"))) return null

        // Fixed columns for the mandatory unique section (approx IATA Resolution 792)
        fun slice(start: Int, len: Int): String? {
            if (start + len > s.length) return null
            return s.substring(start, start + len).trim().ifBlank { null }
        }

        val name = slice(2, 20)?.replace("/", ", ")
        val pnr = slice(23, 7)
        val from = slice(30, 3)
        val to = slice(33, 3)
        val carrier = slice(36, 3)?.trimEnd()
        val flight = slice(39, 5)?.trimStart('0')?.trim()
        val julian = slice(44, 3)?.toIntOrNull()
        val seat = slice(48, 4)?.trimStart('0')?.trim()

        val flightNumber = when {
            carrier != null && flight != null -> "${carrier.take(2)}$flight"
            else -> null
        }
        val departDateMillis = julian?.let { day ->
            val year = LocalDate.now(ZoneOffset.UTC).year
            runCatching {
                LocalDate.ofYearDay(year, day)
                    .atStartOfDay(ZoneOffset.UTC)
                    .toInstant()
                    .toEpochMilli()
            }.getOrNull()
        }

        return BoardingPass(
            passengerName = name,
            pnr = pnr,
            from = from,
            to = to,
            flightNumber = flightNumber,
            seat = seat,
            departDateMillis = departDateMillis
        )
    }

    /**
     * Builds a mandatory-unique BCBP string (60 chars) suitable for Aztec encoding.
     * Returns null when route + flight cannot be filled.
     */
    fun build(
        passengerName: String?,
        pnr: String?,
        from: String?,
        to: String?,
        flightNumber: String?,
        seat: String?,
        travelDate: LocalDate?,
        compartment: String? = null
    ): String? {
        val fromCode = from?.trim()?.uppercase(Locale.US)?.takeIf { it.length == 3 } ?: return null
        val toCode = to?.trim()?.uppercase(Locale.US)?.takeIf { it.length == 3 } ?: return null
        val flightRaw = flightNumber?.replace(" ", "")?.uppercase(Locale.US) ?: return null
        val carrierMatch = Regex("""^([A-Z0-9]{2})(\d{1,4})$""").matchEntire(flightRaw) ?: return null
        val carrier = carrierMatch.groupValues[1].padEnd(3, ' ')
        val flightDigits = carrierMatch.groupValues[2].padStart(5, '0')

        val nameField = formatPassengerName(passengerName)
        val pnrField = (pnr?.uppercase(Locale.US)?.filter { it.isLetterOrDigit() } ?: "XXXXXX")
            .padEnd(7, ' ')
            .take(7)
        val julian = (travelDate ?: LocalDate.now(ZoneOffset.UTC)).dayOfYear
            .toString()
            .padStart(3, '0')
            .take(3)
        val seatField = formatSeat(seat)
        val compartmentCode = (compartment?.trim()?.uppercase(Locale.US)?.firstOrNull() ?: 'Y')
            .toString()

        return buildString(60) {
            append('M')
            append('1')
            append(nameField) // 20
            append('E')
            append(pnrField) // 7
            append(fromCode)
            append(toCode)
            append(carrier) // 3
            append(flightDigits) // 5
            append(julian) // 3
            append(compartmentCode) // 1
            append(seatField) // 4
            append("00001") // check-in sequence
            append('0') // passenger status
            append("00") // no variable field
        }
    }

    /** LAST/FIRST padded/truncated to 20 chars. */
    private fun formatPassengerName(raw: String?): String {
        val cleaned = raw.orEmpty()
            .uppercase(Locale.US)
            .replace(",", "/")
            .replace(Regex("""\s*/\s*"""), "/")
            .replace(Regex("""[^A-Z/ ]"""), "")
            .trim()
            .ifBlank { "PASSENGER/UNKNOWN" }
        val normalized = if (cleaned.contains('/')) cleaned else "$cleaned/"
        return normalized.padEnd(20, ' ').take(20)
    }

    /** e.g. 22A → 022A */
    private fun formatSeat(raw: String?): String {
        val s = raw.orEmpty().uppercase(Locale.US).filter { it.isLetterOrDigit() }
        if (s.isEmpty()) return "0000"
        val m = Regex("""^(\d{1,3})([A-Z])$""").matchEntire(s)
        return if (m != null) {
            (m.groupValues[1].padStart(3, '0') + m.groupValues[2]).padStart(4, '0').take(4)
        } else {
            s.padStart(4, '0').take(4)
        }
    }
}
