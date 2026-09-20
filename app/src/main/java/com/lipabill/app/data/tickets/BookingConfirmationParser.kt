package com.lipabill.app.data.tickets

import com.lipabill.app.data.model.TicketBarcodeFormat
import com.lipabill.app.data.model.TicketSource
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Parses booking-confirmation SMS / messages (SGR Madaraka and similar).
 *
 * Example:
 * `Booking Confirmed. Ref No: KRCX1Y2Z3. Train: Nairobi to Mombasa E2.
 *  Date: 20/09/2026. Time: 08:00 AM. Seat: Coach 5, Seat 42. Amount Paid: KES 1,500.`
 */
object BookingConfirmationParser {

    fun looksLikeConfirmation(text: String): Boolean {
        val lower = text.lowercase(Locale.US)
        return (lower.contains("booking confirmed") || lower.contains("ref no") ||
            lower.contains("booking confirmation") || lower.contains("amount paid")) &&
            (lower.contains("train") || lower.contains("seat") || lower.contains("coach") ||
                lower.contains("krc") || lower.contains("madaraka") || lower.contains("sgr"))
    }

    fun parse(raw: String): TicketImport {
        val text = raw.replace('\u00A0', ' ').trim()
        if (text.isBlank()) {
            throw IllegalArgumentException("Paste your booking confirmation message.")
        }

        val ref = firstMatch(
            text,
            Regex("""(?i)\bRef(?:erence)?\s*No\.?\s*[:\-]?\s*([A-Z0-9]{5,16})\b""")
        ) ?: firstMatch(
            text,
            Regex("""(?i)\b(?:Booking|PNR|Confirmation)\s+(?:Ref|No\.?|Number)\s*[:\-]?\s*([A-Z0-9]{5,16})\b""")
        ) ?: throw IllegalArgumentException("Couldn’t find a booking Ref No in that message.")

        if (ref.equals("Confirmed", ignoreCase = true) ||
            ref.equals("Confirmation", ignoreCase = true)
        ) {
            throw IllegalArgumentException("Couldn’t find a booking Ref No in that message.")
        }

        val trainCode = firstMatch(text, Regex("""(?i)\bTrain\b[^.]{0,80}?\b([EI]\d{1,2})\b"""))
            ?: firstMatch(text, Regex("""(?i)\b([EI]\d{1,2})\b"""))

        val fromTo = Regex(
            """(?i)\bTrain\s*[:\-]?\s*([A-Za-z][A-Za-z .'-]{2,40}?)\s+to\s+([A-Za-z][A-Za-z .'-]{2,40}?)(?:\s+[EI]\d{1,2})?\b"""
        ).find(text)
        val from = fromTo?.groupValues?.getOrNull(1)?.trim()?.trimEnd(',', '.')
            ?.let { normalizeStation(it) }
        val to = fromTo?.groupValues?.getOrNull(2)?.trim()?.trimEnd(',', '.')
            ?.let { normalizeStation(it) }

        val date = parseDate(text)
        val time = parseTime(text) ?: LocalTime.of(0, 0)
        val startsAt = date?.let {
            LocalDateTime.of(it, time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }

        val coach = firstMatch(text, Regex("""(?i)\bCoach\s*([A-Z0-9]+)\b"""))
        val seat = firstMatch(text, Regex("""(?i)\bSeat\s*([A-Z0-9]+)\b"""))
        val seatOrTier = listOfNotNull(
            coach?.let { "Coach $it" },
            seat?.let { "Seat $it" }
        ).joinToString(" · ").ifBlank { null }

        val fare = firstMatch(
            text,
            Regex("""(?i)\bAmount\s*Paid\s*[:\-]?\s*(?:KES|KSh|Ksh)?\s*([0-9][0-9,]*(?:\.[0-9]{2})?)\b""")
        ) ?: firstMatch(
            text,
            Regex("""(?i)\b(?:KES|KSh)\s*([0-9][0-9,]*(?:\.[0-9]{2})?)\b""")
        )

        val title = buildString {
            append("SGR")
            if (trainCode != null) append(" $trainCode")
        }
        val venue = when {
            from != null && to != null -> "$from → $to"
            else -> "Madaraka Express"
        }

        val notes = listOfNotNull(
            from?.let { "Origin: $it" },
            to?.let { "Destination: $it" },
            fare?.let { "Fare: KES $it" },
            "Booking confirmation — add boarding pass when you have it"
        ).joinToString(" · ")

        return TicketImport(
            title = title,
            venue = venue,
            startsAtMillis = startsAt,
            seatOrTier = seatOrTier,
            // Placeholder until a real gate QR is attached
            barcodeValue = confirmationBarcode(ref),
            barcodeFormat = TicketBarcodeFormat.QR_CODE,
            orderId = ref.uppercase(Locale.US),
            notes = notes,
            source = TicketSource.BOOKING_CONFIRMATION,
            expectsBoardingPass = true,
            hasBoardingPass = false
        )
    }

    fun confirmationBarcode(ref: String): String =
        "REF:${ref.trim().uppercase(Locale.US)}"

    fun isConfirmationPlaceholder(barcodeValue: String): Boolean =
        barcodeValue.startsWith("REF:", ignoreCase = true)

    private fun normalizeStation(name: String): String {
        val trimmed = name.trim()
        return when {
            trimmed.equals("Nairobi", true) -> "Nairobi Terminus"
            trimmed.equals("Mombasa", true) -> "Mombasa Terminus"
            else -> trimmed
        }
    }

    private fun firstMatch(text: String, regex: Regex): String? =
        regex.find(text)?.groupValues?.getOrNull(1)?.trim()

    private fun parseDate(text: String): LocalDate? {
        val patterns = listOf("dd/MM/yyyy", "d/M/yyyy", "dd-MM-yyyy", "d-M-yyyy", "yyyy-MM-dd")
        val raw = firstMatch(
            text,
            Regex("""(?i)\bDate\s*[:\-]?\s*(\d{1,2}[/-]\d{1,2}[/-]\d{2,4})\b""")
        ) ?: return null
        for (p in patterns) {
            runCatching {
                return LocalDate.parse(raw, DateTimeFormatter.ofPattern(p, Locale.US))
            }
        }
        return null
    }

    private fun parseTime(text: String): LocalTime? {
        val m = Regex(
            """(?i)\bTime\s*[:\-]?\s*(\d{1,2}[:.]\d{2})\s*(am|pm)?\b"""
        ).find(text) ?: return null
        val raw = m.groupValues[1].replace('.', ':')
        val ampm = m.groupValues.getOrNull(2)?.lowercase(Locale.US)
        val parts = raw.split(':')
        var hour = parts[0].toIntOrNull() ?: return null
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        if (ampm == "pm" && hour < 12) hour += 12
        if (ampm == "am" && hour == 12) hour = 0
        return runCatching { LocalTime.of(hour, minute) }.getOrNull()
    }
}
