package com.lipabill.app.data.tickets

import com.lipabill.app.data.model.TicketBarcodeFormat
import com.lipabill.app.data.model.TicketSource
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Physical airline boarding passes (Kenya Airways and similar IATA layouts).
 *
 * Typical fields: flight, FROM/TO city+IATA, seat, gate, zone, boarding/departure
 * times, ETKT, security no., and a PDF417 BCBP barcode on the stub.
 */
object FlightBoardingPassParser {

    fun looksLikeBoardingPass(text: String, barcode: ETicketTextParser.FoundBarcode?): Boolean {
        val lower = text.lowercase(Locale.US)
        if (lower.contains("boarding pass")) return true
        if (barcode != null && BcbpParser.parse(barcode.value) != null) return true
        if (lower.contains("etkt") || lower.contains("e-ticket no") || lower.contains("electronic ticket")) {
            if (lower.contains("gate") && lower.contains("seat")) return true
        }
        return lower.contains("zone") && lower.contains("gate") &&
            Regex("""(?i)\b(?:from|to)\b.{0,20}\b[A-Z]{3}\b""").containsMatchIn(text)
    }

    /**
     * @param barcode gate PDF417 when scanned; if null we fall back to ETKT / flight
     * so OCR fields still save (user can replace the barcode later).
     */
    fun parse(text: String, barcode: ETicketTextParser.FoundBarcode?): TicketImport {
        val bcbp = barcode?.let { BcbpParser.parse(it.value) }

        val flightNo = bcbp?.flightNumber
            ?: firstMatch(text, Regex("""(?i)\bFlight\s*(?:No\.?|Number)?\s*[:\-]?\s*([A-Z0-9]{2}\s?\d{1,4})\b"""))
                ?.replace(" ", "")?.uppercase(Locale.US)
            ?: firstMatch(text, Regex("""(?i)\b((?:KQ|ET|BA|EK|QR|5H|OW|SA)\s?\d{2,4})\b"""))
                ?.replace(" ", "")?.uppercase(Locale.US)

        val fromPair = cityIata(text, from = true)
        val toPair = cityIata(text, from = false)
        val fromCode = listOfNotNull(fromPair?.second, bcbp?.from)
            .firstOrNull { isPlausibleIata(it) }
        val toCode = listOfNotNull(toPair?.second, bcbp?.to)
            .firstOrNull { isPlausibleIata(it) }
        val fromCity = fromPair?.first?.takeIf { isPlausibleCity(it) }
        val toCity = toPair?.first?.takeIf { isPlausibleCity(it) }

        val passenger = bcbp?.passengerName ?: parsePassengerName(text)

        val seat = bcbp?.seat
            ?: firstMatch(text, Regex("""(?i)\bSeat\s*[:\-]?\s*([0-9]{1,2}[A-Z])\b"""))

        // Prefer numeric gates (04, 5) over OCR noise from "GATE CLOSES"
        val gate = Regex("""(?i)\bGate\s*[:\-]?\s*(\d{1,3})\b""")
            .find(text)?.groupValues?.getOrNull(1)
            ?: Regex("""(?i)\bGate\s*[:\-]?\s*([A-Z]\d{0,2}|\d{1,2}[A-Z]?)\b""")
                .findAll(text)
                .map { it.groupValues[1] }
                .firstOrNull { v ->
                    val u = v.uppercase(Locale.US)
                    u !in setOf("CLO", "NUM", "FOR", "YOU", "THE") &&
                        !text.contains(Regex("""(?i)Gate\s+$v\s*CLOSES"""))
                }

        val zone = firstMatch(text, Regex("""(?i)\bZone\s*[:\-]?\s*([A-Z0-9])\b"""))

        val bookedClass = firstMatch(
            text,
            Regex("""(?i)\b(?:Booked\s*)?Class\s*[:\-]?\s*([A-Z])\b""")
        )
        val cabin = firstMatch(
            text,
            Regex("""(?i)\b(Economy|Business|First|Premium\s*Economy)\b""")
        ) ?: firstMatch(
            text,
            Regex("""(?i)\bCabin\s*[:\-]?\s*([A-Z])\b""")
        )

        val boardingTime = parseClock(
            firstMatch(text, Regex("""(?i)\bBoarding(?:\s*Time)?\s*[:\-]?\s*(\d{1,2}[:.]\d{2})\b"""))
        )
        val labeledDepart = parseClock(
            firstMatch(text, Regex("""(?i)\bDepart(?:ure)?(?:\s*Time)?\s*[:\-]?\s*(\d{1,2}[:.]\d{2})\b"""))
        )
        val allClocks = Regex("""\b([01]?\d|2[0-3])[:.]([0-5]\d)\b""")
            .findAll(text)
            .mapNotNull { parseClock(it.value) }
            .distinct()
            .toList()
        val departTime = labeledDepart
            ?: allClocks.firstOrNull { boardingTime == null || it != boardingTime }

        val travelDate = parseDayMonth(
            firstMatch(text, Regex("""(?i)\bDate\s*[:\-]?\s*(\d{1,2}[A-Z]{3})\b"""))
                ?: firstMatch(text, Regex("""(?i)\b(\d{1,2}[A-Z]{3})\b"""))
        ) ?: bcbp?.departDateMillis?.let {
            java.time.Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
        }

        val etkt = firstMatch(
            text,
            Regex("""(?i)\b(?:ETKT|E-?TKT|E-?Ticket(?:\s*No\.?)?)\s*[:\-]?\s*(\d{13,15})\b""")
        ) ?: firstMatch(text, Regex("""\b(706\d{12})\b"""))

        val security = firstMatch(
            text,
            Regex("""(?i)\bSecurity\s*(?:No\.?|Number|#)?\s*[:\-]?\s*(\d{3,6})\b""")
        )
        val agent = Regex(
            """(?i)\bAgent(?:\s*I\.?D\.?)?\s*[:\-]?\s*([A-Z]\d{3,10})\b"""
        ).find(text)?.groupValues?.getOrNull(1)?.takeIf { isPlausibleAgentId(it) }

        val airline = when {
            text.contains("Kenya Airways", ignoreCase = true) -> "Kenya Airways"
            text.contains("Pride of Africa", ignoreCase = true) -> "Kenya Airways"
            text.contains("Fly540", ignoreCase = true) -> "Fly540"
            flightNo?.startsWith("KQ") == true -> "Kenya Airways"
            flightNo?.startsWith("5H") == true -> "Fly540"
            else -> null
        }

        val timeForStart = departTime ?: boardingTime ?: LocalTime.of(0, 0)
        val startsAt = when {
            travelDate != null -> LocalDateTime.of(travelDate, timeForStart)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            bcbp?.departDateMillis != null -> bcbp.departDateMillis
            else -> null
        }

        val route = when {
            fromCode != null && toCode != null -> "$fromCode → $toCode"
            fromCity != null && toCity != null -> "$fromCity → $toCity"
            else -> null
        }

        val title = when {
            flightNo != null && route != null -> "$flightNo · $route"
            flightNo != null -> flightNo
            else -> "Boarding pass"
        }

        val seatOrTier = listOfNotNull(
            seat?.let { "Seat $it" },
            gate?.let { "Gate $it" },
            zone?.let { "Zone $it" },
            cabin?.let { c -> if (c.length == 1) "Cabin $c" else c }
                ?: bookedClass?.let { "Class $it" }
        ).joinToString(" · ").ifBlank { null }

        // Exact stub payload when ML Kit reads PDF417/Aztec (IATA BCBP), else rebuild
        // the same BCBP string from OCR fields. Always store as Aztec — never QR/ETKT stubs.
        val scannedBcbp = barcode?.value?.trim()?.takeIf { raw ->
            BcbpParser.parse(raw) != null ||
                (raw.startsWith("M1") || raw.startsWith("M2") || raw.startsWith("M3"))
        }
        val builtBcbp = BcbpParser.build(
            passengerName = passenger,
            pnr = bcbp?.pnr ?: etkt?.takeLast(7),
            from = fromCode,
            to = toCode,
            flightNumber = flightNo,
            seat = seat,
            travelDate = travelDate,
            compartment = bookedClass ?: cabin?.takeIf { it.length == 1 }
        )
        val barcodeValue = scannedBcbp ?: builtBcbp
            ?: throw IllegalArgumentException(
                "Couldn’t build an Aztec boarding code from that photo. " +
                    "Use a clearer shot of the full pass (include the stub barcode)."
            )
        val usedScanned = scannedBcbp != null

        val notes = buildList {
            airline?.let { add("Airline: $it") }
            passenger?.let { add("Passenger: $it") }
            fromCity?.let { city ->
                add("Origin: $city${fromCode?.let { " ($it)" } ?: ""}")
            } ?: fromCode?.let { add("Origin: $it") }
            toCity?.let { city ->
                add("Destination: $city${toCode?.let { " ($it)" } ?: ""}")
            } ?: toCode?.let { add("Destination: $it") }
            boardingTime?.let {
                add("Boarding: ${it.format(DateTimeFormatter.ofPattern("HH:mm"))}")
            }
            departTime?.let {
                add("Departure time: ${it.format(DateTimeFormatter.ofPattern("HH:mm"))}")
            }
            gate?.let { add("Gate: $it") }
            zone?.let { add("Zone: $it") }
            bookedClass?.let { add("Class: $it") }
            cabin?.let { add("Cabin: $it") }
            etkt?.let { add("Ticket no: $it") }
            security?.let { add("Security: $it") }
            agent?.let { add("Agent: $it") }
            if (!usedScanned) {
                add("Aztec from pass fields — re-scan stub barcode for airline original")
            }
            add("Boarding pass")
        }.joinToString(" · ")

        return TicketImport(
            title = title,
            venue = route,
            startsAtMillis = startsAt,
            seatOrTier = seatOrTier,
            barcodeValue = barcodeValue,
            barcodeFormat = TicketBarcodeFormat.PDF_417,
            orderId = etkt ?: bcbp?.pnr?.trim()?.ifBlank { null },
            notes = notes,
            source = TicketSource.E_TICKET,
            expectsBoardingPass = true,
            hasBoardingPass = true
        )
    }

    /** Prefer the gate PDF417 / BCBP barcode over incidental QR noise. */
    fun pickBoardingBarcode(barcodes: List<ETicketTextParser.FoundBarcode>): ETicketTextParser.FoundBarcode? {
        val cleaned = barcodes.map { it.copy(value = it.value.trim()) }.filter { it.value.isNotEmpty() }
        if (cleaned.isEmpty()) return null
        cleaned.firstOrNull { BcbpParser.parse(it.value) != null }?.let { return it }
        val preferred = listOf(
            TicketBarcodeFormat.PDF_417,
            TicketBarcodeFormat.AZTEC,
            TicketBarcodeFormat.QR_CODE,
            TicketBarcodeFormat.CODE_128,
            TicketBarcodeFormat.OTHER
        )
        return preferred.firstNotNullOfOrNull { fmt -> cleaned.firstOrNull { it.format == fmt } }
            ?: cleaned.first()
    }

    /**
     * FROM MOMBASA/MBA · TO NAIROBI/NBO · also MOMBASA (MBA) / NAIROBI(NBO).
     * Skips instruction text ("…TO DEPARTURE") by scanning all candidates.
     */
    private fun cityIata(text: String, from: Boolean): Pair<String?, String>? {
        val labels = if (from) {
            listOf("FROM", "ORIGIN")
        } else {
            listOf("DESTINATION", "DEST", "TO")
        }

        // CITY / IATA  or  CITY(IATA)  or  CITY (MBA) — spaces around / or (
        val cityCodeBody = """([A-Z][A-Z .'-]{1,24}?)\s*(?:/\s*|\(\s*)([A-Z]{3})\s*\)?"""

        for (label in labels) {
            Regex(
                """(?i)\b${Regex.escape(label)}\b\s*[:\-]?\s*$cityCodeBody"""
            ).findAll(text).forEach { m ->
                val city = m.groupValues[1].trim().trimEnd(',', '.')
                val code = m.groupValues[2].uppercase(Locale.US)
                if (isPlausibleCity(city) && isPlausibleIata(code)) {
                    return city to code
                }
            }
        }

        for (label in labels) {
            Regex(
                """(?i)\b${Regex.escape(label)}\b\s*[:\-]?\s*([A-Z]{3})(?![A-Za-z])"""
            ).findAll(text).forEach { m ->
                val code = m.groupValues[1].uppercase(Locale.US)
                if (isPlausibleIata(code)) return null to code
            }
        }

        val pairs = Regex("""(?i)$cityCodeBody""")
            .findAll(text)
            .map { it.groupValues[1].trim().trimEnd(',', '.') to it.groupValues[2].uppercase(Locale.US) }
            .filter { (city, code) -> isPlausibleCity(city) && isPlausibleIata(code) }
            .distinct()
            .toList()
        return when {
            from && pairs.isNotEmpty() -> pairs[0].first to pairs[0].second
            !from && pairs.size >= 2 -> pairs[1].first to pairs[1].second
            else -> null
        }
    }

    private fun parsePassengerName(text: String): String? {
        firstMatch(
            text,
            Regex("""(?i)\b(?:Passenger\s*Name|Name)\s*[:\-]?\s*([A-Z][A-Z /.-]{1,40})""")
        )?.trim()?.trimEnd(',', '.')
            ?.replace(Regex("""\s*/\s*"""), "/")
            ?.let { return it }

        // HASSAN/A · HASSAN / A · MATHENGE/LILIAN MU — not CITY/IATA
        return Regex("""(?i)\b([A-Z]{2,})\s*/\s*([A-Z](?:[A-Z ]{0,30})?)""")
            .findAll(text)
            .map {
                "${it.groupValues[1].trim()}/${it.groupValues[2].trim()}"
                    .replace(Regex("""\s*/\s*"""), "/")
            }
            .firstOrNull { candidate ->
                val left = candidate.substringBefore('/')
                val right = candidate.substringAfter('/').trim()
                val looksLikeAirport = right.length == 3 &&
                    isPlausibleIata(right) &&
                    isPlausibleCity(left)
                !looksLikeAirport
            }
    }

    private fun isPlausibleIata(code: String): Boolean {
        val c = code.uppercase(Locale.US)
        if (c.length != 3) return false
        val reject = setOf(
            "DEP", "ARR", "FOR", "THE", "AND", "YOU", "ARE", "WITH", "GATE",
            "SEAT", "ZONE", "DATE", "CLASS", "CABIN", "ETKT", "FLT", "SEC",
            "MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN", "JUN", "JUL",
            "AUG", "SEP", "OCT", "NOV", "DEC", "JAN", "FEB", "MAR", "APR",
            "MAY", "AIR", "WAY", "PASS", "ECO", "BUS", "YES", "NOT", "MIN",
            "CLO", "NUM"
        )
        return c !in reject
    }

    private fun isPlausibleCity(city: String): Boolean {
        val c = city.trim().uppercase(Locale.US)
        if (c.length < 3) return false
        val reject = setOf(
            "DEPARTURE", "ARRIVAL", "BOARDING", "MONITORS", "MINUTES",
            "NUMBER", "CLOSES", "CHECK", "YOUR", "GATE", "SEAT", "ZONE",
            "CLASS", "CABIN", "ECONOMY", "BUSINESS", "FLIGHT", "PASSENGER",
            "SECURITY", "AGENT", "TICKET", "DATE", "TIME", "BOOKED"
        )
        return c !in reject && !c.startsWith("DEPART") && !c.startsWith("ARRIV")
    }

    private fun isPlausibleAgentId(raw: String): Boolean {
        val v = raw.trim().uppercase(Locale.US)
        // C2797, S917169 — letter then digits
        return Regex("""^[A-Z]\d{3,10}$""").matches(v)
    }

    private fun parseClock(raw: String?): LocalTime? {
        if (raw.isNullOrBlank()) return null
        val parts = raw.replace('.', ':').split(':')
        val h = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return runCatching { LocalTime.of(h, m) }.getOrNull()
    }

    /** 23JUN → LocalDate (year inferred: current, or next year if already past). */
    private fun parseDayMonth(raw: String?): LocalDate? {
        if (raw.isNullOrBlank()) return null
        val m = Regex("""(?i)^(\d{1,2})([A-Z]{3})$""").matchEntire(raw.trim()) ?: return null
        val day = m.groupValues[1].toIntOrNull() ?: return null
        val month = parseMonth(m.groupValues[2]) ?: return null
        val today = LocalDate.now(ZoneId.systemDefault())
        var date = runCatching { LocalDate.of(today.year, month, day) }.getOrNull() ?: return null
        // If more than ~2 days in the past, assume next year (boarding passes are near-term)
        if (date.isBefore(today.minusDays(2))) {
            date = date.plusYears(1)
        }
        return date
    }

    private fun parseMonth(raw: String): Month? {
        val key = raw.trim().lowercase(Locale.ENGLISH).take(3)
        return mapOf(
            "jan" to Month.JANUARY, "feb" to Month.FEBRUARY, "mar" to Month.MARCH,
            "apr" to Month.APRIL, "may" to Month.MAY, "jun" to Month.JUNE,
            "jul" to Month.JULY, "aug" to Month.AUGUST, "sep" to Month.SEPTEMBER,
            "oct" to Month.OCTOBER, "nov" to Month.NOVEMBER, "dec" to Month.DECEMBER
        )[key]
    }

    private fun firstMatch(text: String, regex: Regex): String? =
        regex.find(text)?.groupValues?.getOrNull(1)?.trim()
}
