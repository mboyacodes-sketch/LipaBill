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
 * Pure text/barcode heuristics for e-tickets (SGR Kenya, flights, generic).
 * Layout can be messy — we tolerate OCR noise and missing labels.
 *
 * Physical Madaraka Express tickets typically look like:
 *  `0299823` … `Sold at Nairobi Terminus`
 *  `Nairobi Terminus`  `E2 →`  `Mombasa Terminus`
 *  `09:00` / `the 13th of September, 2017` / `KSH 700.00`
 *  `Seat47` `Coach7` `SecondClass`
 *  `Name:` … `ID/P NO:` … QR + serial `0908100299823`
 */
object ETicketTextParser {

    data class FoundBarcode(
        val value: String,
        val format: TicketBarcodeFormat
    )

    /** Station name with horizontal position from OCR (left = origin on Madaraka tickets). */
    data class StationHint(
        val name: String,
        val centerX: Int
    )

    fun parse(
        ocrText: String,
        barcodes: List<FoundBarcode>,
        stationHints: List<StationHint> = emptyList(),
        kind: TicketDocumentKind = TicketDocumentKind.AUTO
    ): TicketImport {
        val text = normalizeOcr(ocrText.replace('\u00A0', ' ').trim())

        when (kind) {
            TicketDocumentKind.EVENT -> {
                val barcode = pickBarcode(barcodes, text)
                    ?: throw IllegalArgumentException(
                        "No scannable code found. Use a clearer PDF/photo, or paste the ticket code."
                    )
                val draft = parseSgr(text, barcode, stationHints)
                    ?: parseFlight(text, barcode)
                    ?: parseGeneric(text, barcode)
                return draft.copy(expectsBoardingPass = false, hasBoardingPass = true)
            }
            TicketDocumentKind.BOARDING_PASS -> {
                val bc = FlightBoardingPassParser.pickBoardingBarcode(barcodes)
                return FlightBoardingPassParser.parse(text, bc)
            }
            TicketDocumentKind.FLIGHT_E_TICKET ->
                return FlightElectronicTicketParser.parse(text)
            TicketDocumentKind.SGR_TICKET -> {
                val barcode = pickBarcode(barcodes, text)
                    ?: throw IllegalArgumentException(
                        "No ticket code found on that SGR ticket. Try a clearer photo."
                    )
                return parseSgr(text, barcode, stationHints)
                    ?.copy(expectsBoardingPass = true, hasBoardingPass = true)
                    ?: throw IllegalArgumentException(
                        "Couldn’t read that as an SGR ticket. Check the photo and try again."
                    )
            }
            TicketDocumentKind.PKPASS -> {
                // Handled upstream; fall through to auto if we land here
            }
            TicketDocumentKind.AUTO -> Unit
        }

        // AUTO: boarding pass first when the header is visible (avoids e-ticket mis-route)
        val boardingBarcode = FlightBoardingPassParser.pickBoardingBarcode(barcodes)
        if (FlightBoardingPassParser.looksLikeBoardingPass(text, boardingBarcode)) {
            return FlightBoardingPassParser.parse(text, boardingBarcode)
        }

        if (FlightElectronicTicketParser.looksLikeElectronicTicketReceipt(text)) {
            runCatching { return FlightElectronicTicketParser.parse(text) }
        }

        val barcode = pickBarcode(barcodes, text)
            ?: throw IllegalArgumentException(
                "No scannable code found. Use a clearer PDF/photo, or paste the ticket code."
            )

        val sgr = parseSgr(text, barcode, stationHints)
        if (sgr != null) return sgr

        val flight = parseFlight(text, barcode)
        if (flight != null) return flight

        return parseGeneric(text, barcode)
    }

    /**
     * Clean OCR quirks from Madaraka physical tickets:
     * Nairobi / E2 / Terminus on separate lines, "KSH 700. 00", "Ticket No" then digits.
     */
    internal fun normalizeOcr(text: String): String {
        var t = text
        t = Regex(
            """(?i)\b(Nairobi|Mombasa|Syokimau|Emali|Voi|Mariakani|Suswa)\s*\R\s*([EI]\d{1,2})\s*\R\s*Terminus\b"""
        ).replace(t) { m ->
            "${m.groupValues[1]} Terminus\n${m.groupValues[2]}"
        }
        t = Regex(
            """(?i)\b(Nairobi|Mombasa|Syokimau|Emali|Voi|Mariakani|Suswa)\s*\R\s*Terminus\b"""
        ).replace(t) { m ->
            "${m.groupValues[1]} Terminus"
        }
        // Only fix spaced decimals like "700. 00" / "700, 00" — not thousands "10,000.00"
        t = Regex("""(?i)K\s*SH\.?\s*([0-9]{1,6})\s+[.,]\s*([0-9]{2})\b""").replace(t) { m ->
            "KSH ${m.groupValues[1]}.${m.groupValues[2]}"
        }
        t = Regex("""(?i)Ticket\s*No\.?\s*\R\s*(\d{6,8})\b""").replace(t) { m ->
            "Ticket No: ${m.groupValues[1]}"
        }
        t = Regex("""(?i)Departure\s*Time\s*:""").replace(t, "Departure:")
        t = Regex("""(?i)\bSerial\s*N?\s*\R\s*(\d{10,16})\b""").replace(t) { m ->
            "Serial: ${m.groupValues[1]}"
        }
        // Serial often appears alone at the bottom
        if (!Regex("""(?i)\bSerial\s*:""").containsMatchIn(t)) {
            Regex("""(?m)^\s*(0\d{10,15})\s*$""").find(t)?.let { m ->
                t = t.replace(m.value, "Serial: ${m.groupValues[1]}")
            }
        }
        return t
    }

    private fun pickBarcode(barcodes: List<FoundBarcode>, text: String): FoundBarcode? {
        val cleaned = barcodes
            .map { it.copy(value = it.value.trim()) }
            .filter { it.value.isNotEmpty() }
        if (cleaned.isNotEmpty()) {
            FlightBoardingPassParser.pickBoardingBarcode(cleaned)?.let { pick ->
                if (BcbpParser.parse(pick.value) != null ||
                    pick.format == TicketBarcodeFormat.PDF_417
                ) {
                    return pick
                }
            }
            val preferred = listOf(
                TicketBarcodeFormat.QR_CODE,
                TicketBarcodeFormat.PDF_417,
                TicketBarcodeFormat.AZTEC,
                TicketBarcodeFormat.CODE_128,
                TicketBarcodeFormat.OTHER
            )
            return preferred.firstNotNullOfOrNull { fmt -> cleaned.firstOrNull { it.format == fmt } }
                ?: cleaned.first()
        }
        // No barcode image — fall back to printed ticket / booking / serial numbers
        firstMatch(
            text,
            Regex(
                """(?i)\b(?:booking\s*ref(?:erence)?|ticket\s*(?:no|number|#)?|pnr|confirmation(?:\s*(?:no|number|code))?|ref(?:erence)?)\s*[:.#\-]*\s*([A-Z0-9]{6,14})\b"""
            )
        )?.let { return FoundBarcode(it, TicketBarcodeFormat.QR_CODE) }

        firstMatch(text, Regex("""(?i)\bSerial\s*N?\s*[:.#\-]?\s*(\d{10,16})\b"""))
            ?.let { return FoundBarcode(it, TicketBarcodeFormat.QR_CODE) }

        // Madaraka red ticket number (top-left), e.g. 0299823
        firstMatch(text, Regex("""(?m)^\s*(\d{6,8})\b"""))
            ?.let { return FoundBarcode(it, TicketBarcodeFormat.QR_CODE) }

        // Long serial near QR, e.g. 0908100299823
        Regex("""\b(\d{11,16})\b""").find(text)?.groupValues?.getOrNull(1)
            ?.let { return FoundBarcode(it, TicketBarcodeFormat.QR_CODE) }

        return null
    }

    private fun parseSgr(
        text: String,
        barcode: FoundBarcode,
        stationHints: List<StationHint>
    ): TicketImport? {
        val lower = text.lowercase(Locale.US)
        val looksSgr = listOf(
            "madaraka", "sgr", "kenya railways", "krc", "metickets",
            "inter-county", "intercounty", "nairobi terminus", "mombasa terminus",
            "sold at", "not transferable", "only valid for carriage"
        ).any { lower.contains(it) } ||
            Regex("""(?i)\bCoach\s*\d+\b""").containsMatchIn(text) &&
            kenyaStations.any { lower.contains(it.lowercase(Locale.US)) }
        if (!looksSgr) return null

        val (from, to) = resolveSgrRoute(text, stationHints)

        val train = firstMatch(
            text,
            Regex("""(?i)\b([EI]\d{1,2})\b""")
        )

        val title = buildString {
            append("SGR")
            if (train != null) append(" $train")
            else append(" Madaraka Express")
        }

        val coach = firstMatch(text, Regex("""(?i)\bCoach\s*([A-Z0-9]+)\b"""))
            ?: labeled(text, "coach", "carriage")
        val seat = firstMatch(text, Regex("""(?i)\bSeat\s*([A-Z0-9]+)\b"""))
            ?: labeled(text, "seat", "seat no", "seat number")
        val clazzRaw = firstMatch(
            text,
            Regex("""(?i)\b((?:First|Second|Third|Economy|Business|Premium)\s*Class)\b""")
        ) ?: labeled(text, "class", "cabin")
        val clazz = clazzRaw?.let { normalizeClass(it) }

        val seatOrTier = listOfNotNull(
            clazz,
            coach?.let { "Coach $it" },
            seat?.let { "Seat $it" }
        ).joinToString(" · ").ifBlank { null }

        val ticketNo = firstMatch(text, Regex("""(?i)Ticket\s*No\.?\s*[:.#\-]?\s*(\d{6,8})\b"""))
            ?: firstMatch(text, Regex("""(?m)^\s*(\d{6,8})\b"""))
            ?: labeled(text, "ticket no", "ticket number", "ticket")
        val serial = firstMatch(text, Regex("""(?i)\bSerial\s*:?\s*(\d{10,16})\b"""))
            ?: Regex("""\b(0\d{10,15})\b""").find(text)?.groupValues?.getOrNull(1)
        val fare = firstMatch(
            text,
            Regex("""(?i)\bK\s*SH\.?\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\.[0-9]{2})?|[0-9]+(?:\.[0-9]{2})?)\b""")
        )?.replace(",", "")?.replace(Regex("""\s+"""), "")
            ?: firstMatch(
                text,
                Regex("""(?i)\bKES\.?\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\.[0-9]{2})?|[0-9]+(?:\.[0-9]{2})?)\b""")
            )?.replace(",", "")?.replace(Regex("""\s+"""), "")
        val passenger = labeledAfter(text, "Name", "Passenger", "Passenger Name")
            ?.takeIf { it.length >= 2 && !it.equals("ID", ignoreCase = true) }
        val idNo = labeledAfter(text, "ID/P NO", "ID/P No", "ID No", "Passport")
        val soldAt = firstMatch(text, Regex("""(?i)Sold at\s+([A-Za-z][A-Za-z .'-]{2,40})"""))

        val booking = ticketNo
            ?: labeled(text, "booking", "booking ref", "booking reference", "pnr", "confirmation")
            ?: barcode.value.takeIf { it.length in 6..24 && it.all { ch -> ch.isLetterOrDigit() || ch == '-' } }

        val startsAt = parseTravelDateTime(text)
        val venue = when {
            from != null && to != null -> "$from → $to"
            from != null -> from
            to != null -> to
            else -> "Madaraka Express"
        }

        val notes = listOfNotNull(
            from?.let { "Origin: $it" },
            to?.let { "Destination: $it" },
            passenger?.let { "Passenger: $it" },
            idNo?.let { "ID: $it" },
            fare?.let { "Fare: KSH $it" },
            serial?.let { "Serial: $it" },
            soldAt?.let { "Sold at $it" },
            "Imported SGR ticket"
        ).joinToString(" · ")

        return TicketImport(
            title = title,
            venue = venue,
            startsAtMillis = startsAt,
            seatOrTier = seatOrTier,
            barcodeValue = barcode.value,
            // SGR gate / stub codes are QR — never promote PDF417 flight barcodes
            barcodeFormat = TicketBarcodeFormat.QR_CODE,
            orderId = booking,
            notes = notes,
            source = TicketSource.E_TICKET,
            expectsBoardingPass = true,
            hasBoardingPass = true
        )
    }

    private fun normalizeClass(raw: String): String {
        val compact = raw.replace(Regex("""\s+"""), "")
        val m = Regex(
            """(?i)(First|Second|Third|Economy|Business|Premium)Class"""
        ).matchEntire(compact)
        return if (m != null) {
            m.groupValues[1].replaceFirstChar { it.titlecase(Locale.US) } + " Class"
        } else {
            raw.replace(Regex("""(?i)\s+"""), " ")
                .replaceFirstChar { it.titlecase(Locale.US) }
        }
    }

    private fun parseFlight(text: String, barcode: FoundBarcode): TicketImport? {
        val bcbp = BcbpParser.parse(barcode.value)
        val lower = text.lowercase(Locale.US)
        val looksFlight = bcbp != null || listOf(
            "boarding pass", "flight", "gate", "terminal",
            "e-ticket", "eticket", "airline", "passenger name", "seq"
        ).any { lower.contains(it) } ||
            Regex("""\b[A-Z]{2}\s?\d{1,4}\b""").containsMatchIn(text)

        if (!looksFlight) return null

        val flightNo = bcbp?.flightNumber
            ?: firstMatch(text, Regex("""(?i)\b([A-Z]{2}\s?\d{1,4})\b"""))
                ?.replace(" ", "")
        val from = bcbp?.from
            ?: labeled(text, "from", "origin", "departure")
            ?: iataNear(text, "from")
        val to = bcbp?.to
            ?: labeled(text, "to", "destination", "arrival")
            ?: iataNear(text, "to")
        val passenger = bcbp?.passengerName
            ?: labeledAfter(text, "Passenger Name", "Passenger", "Name")
        val seat = bcbp?.seat
            ?: firstMatch(text, Regex("""(?i)\bSeat\s*([A-Z0-9]+)\b"""))
            ?: labeled(text, "seat", "seat no")
        val gate = labeled(text, "gate")
        val startsAt = bcbp?.departDateMillis
            ?: parseTravelDateTime(text)

        val route = when {
            from != null && to != null -> "$from → $to"
            else -> null
        }
        val title = when {
            flightNo != null && route != null -> "$flightNo · $route"
            flightNo != null -> "Flight $flightNo"
            route != null -> "Flight · $route"
            else -> "Flight e-ticket"
        }
        val seatOrTier = listOfNotNull(
            seat?.let { "Seat $it" },
            gate?.let { "Gate $it" }
        ).joinToString(" · ").ifBlank { null }

        return TicketImport(
            title = title,
            venue = route ?: passenger,
            startsAtMillis = startsAt,
            seatOrTier = seatOrTier,
            barcodeValue = barcode.value,
            barcodeFormat = barcode.format,
            orderId = bcbp?.pnr ?: labeled(text, "pnr", "booking ref", "confirmation"),
            notes = listOfNotNull("Imported flight e-ticket", passenger).joinToString(" · "),
            source = TicketSource.E_TICKET,
            expectsBoardingPass = false,
            hasBoardingPass = true
        )
    }

    private fun parseGeneric(text: String, barcode: FoundBarcode): TicketImport {
        val title = firstNonEmptyLine(text)
            ?.take(64)
            ?.ifBlank { null }
            ?: "E-ticket"
        val startsAt = parseTravelDateTime(text)
        val venue = labeled(text, "venue", "station", "location", "place")
        val seat = labeled(text, "seat", "coach", "row", "gate")
        val order = labeled(text, "booking", "pnr", "ticket no", "reference", "confirmation")
        return TicketImport(
            title = title,
            venue = venue,
            startsAtMillis = startsAt,
            seatOrTier = seat,
            barcodeValue = barcode.value,
            barcodeFormat = barcode.format,
            orderId = order,
            notes = "Imported e-ticket",
            source = TicketSource.E_TICKET,
            expectsBoardingPass = false,
            hasBoardingPass = true
        )
    }

    private val kenyaStations = listOf(
        "Nairobi Terminus", "Mombasa Terminus", "Syokimau", "Athi River", "Emali",
        "Mtito Andei", "Voi", "Miasenyi", "Mariakani", "Suswa", "Naivasha", "Mai Mahiu",
        "Nairobi", "Mombasa"
    )

    private val kenyaTermini = listOf(
        "Nairobi Terminus", "Mombasa Terminus", "Syokimau", "Athi River", "Emali",
        "Mtito Andei", "Voi", "Miasenyi", "Mariakani", "Suswa", "Naivasha", "Mai Mahiu"
    )

    /**
     * Resolve Madaraka origin → destination.
     *
     * Priority:
     * 1. "Sold at <station>" is almost always the departure station; the other terminus is destination
     * 2. OCR bounding boxes: leftmost station = origin, rightmost = destination
     * 3. Train code (E2) as anchor between stations in the text stream
     * 4. First / second distinct station in reading order
     */
    private fun resolveSgrRoute(
        text: String,
        stationHints: List<StationHint>
    ): Pair<String?, String?> {
        val soldAtStation = firstMatch(
            text,
            Regex("""(?i)Sold\s+at\s+([A-Za-z][A-Za-z .'-]{2,40})""")
        )?.let { raw ->
            kenyaStations.firstOrNull { raw.contains(it, ignoreCase = true) }
                ?.let { normalizeStation(it) }
        }

        val cleaned = text.replace(Regex("(?i)Sold\\s+at[^\\n\\r]*"), " ")
        val mentioned = findStationMentions(cleaned).map { it.third }.distinct()

        if (soldAtStation != null) {
            val other = mentioned.firstOrNull { it != soldAtStation }
            if (other != null) return soldAtStation to other
        }

        val spatial = stationHints
            .map { it.copy(name = normalizeStation(it.name)) }
            .distinctBy { it.name }
        if (spatial.size >= 2) {
            val origin = spatial.minBy { it.centerX }.name
            val destination = spatial.maxBy { it.centerX }.name
            if (origin != destination) return origin to destination
        }

        val stations = findStationMentions(cleaned)
        if (stations.isEmpty()) return null to null

        val train = Regex("""(?i)\b([EI]\d{1,2})\b""").find(cleaned)
        if (train != null) {
            val at = train.range.first
            val origin = stations.filter { it.first + it.second <= at }
                .maxByOrNull { it.first }
                ?.third
            val destination = stations.filter { it.first >= train.range.last }
                .minByOrNull { it.first }
                ?.third
            if (origin != null && destination != null && origin != destination) {
                return origin to destination
            }
        }

        val arrow = Regex(
            """(?i)(${kenyaTermini.joinToString("|") { Regex.escape(it) }})\s*(?:→|->|–|—|to)\s*(${kenyaTermini.joinToString("|") { Regex.escape(it) }})"""
        ).find(cleaned)
        if (arrow != null) {
            return normalizeStation(arrow.groupValues[1]) to normalizeStation(arrow.groupValues[2])
        }

        val uniqueOrdered = stations
            .sortedBy { it.first }
            .map { it.third }
            .distinct()
        return when {
            uniqueOrdered.size >= 2 -> uniqueOrdered[0] to uniqueOrdered[1]
            uniqueOrdered.size == 1 -> uniqueOrdered[0] to null
            else -> null to null
        }
    }

    /** (startIndex, matchedLength, normalizedName) — longer names preferred at same index. */
    private fun findStationMentions(text: String): List<Triple<Int, Int, String>> {
        val hits = mutableListOf<Triple<Int, Int, String>>()
        // Prefer full terminus names before bare city names
        for (station in kenyaStations.sortedByDescending { it.length }) {
            var start = 0
            while (true) {
                val idx = text.indexOf(station, start, ignoreCase = true)
                if (idx < 0) break
                // Skip if this span is already covered by a longer hit
                val covered = hits.any { idx >= it.first && idx < it.first + it.second }
                if (!covered) {
                    hits += Triple(idx, station.length, normalizeStation(station))
                }
                start = idx + station.length
            }
        }
        return hits.sortedBy { it.first }
    }

    private fun kenyaStationIn(text: String, preferFirst: Boolean): String? {
        val found = findStationMentions(text)
        if (found.isEmpty()) return null
        return if (preferFirst) found.first().third else found.last().third
    }

    private fun stationNear(text: String, vararg labels: String): String? {
        for (label in labels) {
            val m = Regex(
                """(?i)\b${Regex.escape(label)}\b\s*[:\-]?\s*([A-Za-z][A-Za-z .'-]{2,40})"""
            ).find(text)
            val raw = m?.groupValues?.getOrNull(1)?.trim()?.substringBefore("\n")
            if (!raw.isNullOrBlank()) {
                val station = kenyaStations.firstOrNull { raw.contains(it, ignoreCase = true) }
                if (station != null) return normalizeStation(station)
                if (raw.length in 3..32) return raw.trim().trimEnd(',', '.', ';')
            }
        }
        return null
    }

    private fun normalizeStation(name: String): String = when {
        name.equals("Nairobi", true) -> "Nairobi Terminus"
        name.equals("Mombasa", true) -> "Mombasa Terminus"
        else -> name
    }

    private fun iataNear(text: String, label: String): String? {
        val m = Regex(
            """(?i)\b${Regex.escape(label)}\b\s*[:\-]?\s*([A-Z]{3})\b"""
        ).find(text)
        return m?.groupValues?.getOrNull(1)
    }

    private fun labeled(text: String, vararg labels: String): String? {
        for (label in labels) {
            val m = Regex(
                """(?i)\b${Regex.escape(label)}\b\s*[:.#\-]?\s*([A-Z0-9][A-Z0-9 /.\-]{0,40})"""
            ).find(text)
            val v = m?.groupValues?.getOrNull(1)?.trim()?.substringBefore('\n')
                ?.trimEnd(',', '.', ';', ':')
                ?.trim()
            if (!v.isNullOrBlank() && v.length <= 40) return v
        }
        return null
    }

    /** Label may be followed by a person name (letters/spaces), not only alphanumerics. */
    private fun labeledAfter(text: String, vararg labels: String): String? {
        for (label in labels) {
            val m = Regex(
                """(?i)\b${Regex.escape(label)}\b\s*[:.#\-]?\s*([A-Za-z][A-Za-z0-9 .'/,-]{1,48})"""
            ).find(text)
            val v = m?.groupValues?.getOrNull(1)?.trim()?.substringBefore('\n')
                ?.trimEnd(',', '.', ';', ':')
                ?.trim()
            if (!v.isNullOrBlank() &&
                !v.equals("ID", true) &&
                !v.startsWith("ID/", true) &&
                v.length <= 48
            ) {
                return v
            }
        }
        return null
    }

    private fun firstMatch(text: String, regex: Regex): String? =
        regex.find(text)?.groupValues?.getOrNull(1)?.trim()

    private fun firstNonEmptyLine(text: String): String? =
        text.lineSequence().map { it.trim() }.firstOrNull { it.length >= 4 }

    fun parseTravelDateTime(text: String): Long? {
        val date = parseDate(text) ?: return null
        val time = parseTime(text) ?: LocalTime.of(0, 0)
        return LocalDateTime.of(date, time)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }

    private fun parseDate(text: String): LocalDate? {
        // Madaraka physical: "the 13th of September, 2017"
        Regex(
            """(?i)(?:the\s+)?(\d{1,2})(?:st|nd|rd|th)?\s+of\s+([A-Za-z]+),?\s+(\d{4})"""
        ).find(text)?.let { m ->
            val day = m.groupValues[1].toIntOrNull()
            val month = parseMonthName(m.groupValues[2])
            val year = m.groupValues[3].toIntOrNull()
            if (day != null && month != null && year != null) {
                return runCatching { LocalDate.of(year, month, day) }.getOrNull()
            }
        }

        val patterns = listOf(
            "d MMM yyyy", "dd MMM yyyy", "d MMMM yyyy", "dd/MM/yyyy", "d/M/yyyy",
            "yyyy-MM-dd", "dd-MM-yyyy", "MMM d, yyyy", "d MMM yy"
        )
        val candidates = Regex(
            """(?i)\b(\d{1,2}[/-]\d{1,2}[/-]\d{2,4}|\d{4}-\d{2}-\d{2}|\d{1,2}\s+[A-Za-z]{3,9}\s+\d{2,4}|[A-Za-z]{3,9}\s+\d{1,2},?\s+\d{2,4})\b"""
        ).findAll(text).map { it.value }.toList()
        for (raw in candidates) {
            for (p in patterns) {
                runCatching {
                    return LocalDate.parse(
                        raw.trim(),
                        DateTimeFormatter.ofPattern(p, Locale.ENGLISH)
                    )
                }
            }
        }
        return null
    }

    private fun parseMonthName(raw: String): Month? {
        val key = raw.trim().lowercase(Locale.ENGLISH)
        val months = mapOf(
            "january" to Month.JANUARY, "jan" to Month.JANUARY,
            "february" to Month.FEBRUARY, "feb" to Month.FEBRUARY,
            "march" to Month.MARCH, "mar" to Month.MARCH,
            "april" to Month.APRIL, "apr" to Month.APRIL,
            "may" to Month.MAY,
            "june" to Month.JUNE, "jun" to Month.JUNE,
            "july" to Month.JULY, "jul" to Month.JULY,
            "august" to Month.AUGUST, "aug" to Month.AUGUST,
            "september" to Month.SEPTEMBER, "sept" to Month.SEPTEMBER, "sep" to Month.SEPTEMBER,
            "october" to Month.OCTOBER, "oct" to Month.OCTOBER,
            "november" to Month.NOVEMBER, "nov" to Month.NOVEMBER,
            "december" to Month.DECEMBER, "dec" to Month.DECEMBER
        )
        return months[key]
    }

    private fun parseTime(text: String): LocalTime? {
        // Prefer standalone HH:mm near fare/date block (Madaraka prints 09:00)
        val candidates = Regex("""\b([01]?\d|2[0-3])[:.]([0-5]\d)\b""").findAll(text).toList()
        for (m in candidates) {
            val hour = m.groupValues[1].toIntOrNull() ?: continue
            val minute = m.groupValues[2].toIntOrNull() ?: continue
            // Skip years mistaken as times — not needed for HH:mm
            runCatching { return LocalTime.of(hour, minute) }
        }
        val m = Regex(
            """(?i)\b(?:dep(?:arture)?|depart|time|at)?\s*[:\-]?\s*(\d{1,2}[:.]\d{2})\s*(am|pm)?\b"""
        ).find(text) ?: Regex("""\b(\d{1,2}[:.]\d{2})\s*(am|pm)\b""", RegexOption.IGNORE_CASE)
            .find(text)
        val raw = m?.groupValues?.getOrNull(1)?.replace('.', ':') ?: return null
        val ampm = m.groupValues.getOrNull(2)?.lowercase(Locale.US)
        val parts = raw.split(':')
        var hour = parts[0].toIntOrNull() ?: return null
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        if (ampm == "pm" && hour < 12) hour += 12
        if (ampm == "am" && hour == 12) hour = 0
        return runCatching { LocalTime.of(hour, minute) }.getOrNull()
    }
}
