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
 * Parses airline electronic ticket / itinerary PDFs & photos
 * (Kenya Airways receipt, Fly540 itinerary, and similar).
 *
 * These are booking confirmations — boarding pass QR attaches later.
 */
object FlightElectronicTicketParser {

    /** IATA-ish carriers common in Kenya + region (incl. Fly540 = 5H). */
    private val CARRIER = Regex(
        """(?i)\b((?:KQ|ET|BA|EK|QR|TK|KL|AF|SA|PW|WB|JM|UY|TC|5H|OW|P2|TM|KQA?)\s?\d{2,4})\b"""
    )

    /** Receipt (fare/ticket formal) vs itinerary (schedule-first) layouts. */
    enum class DocumentKind { RECEIPT, ITINERARY }

    /** Outbound-only vs round-trip (and open-jaw / multi-city). */
    enum class TripType { ONE_WAY, RETURN, MULTI_CITY }

    data class Segment(
        val flightNumber: String?,
        val from: String?,
        val to: String?,
        val fromTerminal: String?,
        val toTerminal: String?,
        val departAtMillis: Long?,
        val arriveAtMillis: Long?,
        val bookingClass: String?,
        val status: String?,
        val baggage: String?,
        val fareBasis: String?,
        val duration: String?,
        val operatedBy: String?
    )

    fun looksLikeElectronicTicketReceipt(text: String): Boolean {
        val lower = text.lowercase(Locale.US)
        // Physical boarding passes also mention airlines / ETKT — never treat those as receipts
        if (lower.contains("boarding pass")) return false
        if (lower.contains("boarding time") && lower.contains("gate") && lower.contains("seat")) {
            return false
        }
        val header = lower.contains("electronic ticket receipt") ||
            lower.contains("e-ticket receipt") ||
            (lower.contains("electronic ticket") && !lower.contains("etkt")) ||
            (lower.contains("itinerary") && (
                lower.contains("booking ref") || lower.contains("booking reference") ||
                    lower.contains("flight ticket")
                ))
        val airlineish = lower.contains("kenya airways") ||
            lower.contains("fly540") ||
            lower.contains("fly 540") ||
            lower.contains("your local airline") ||
            lower.contains("passenger name") ||
            lower.contains("fare calculation") ||
            lower.contains("issuing office") ||
            lower.contains("total fare") ||
            lower.contains("total taxes") ||
            CARRIER.containsMatchIn(text)
        return header || (airlineish && (
            lower.contains("booking ref") || lower.contains("booking reference") ||
                lower.contains("ticket number") || lower.contains("ticket numbers") ||
                lower.contains("itinerary") || lower.contains("form of payment") ||
                lower.contains("fare calculation") || lower.contains("issuing office")
            ))
    }

    fun parse(raw: String): TicketImport {
        val text = raw.replace('\u00A0', ' ').trim()
        if (text.isBlank()) {
            throw IllegalArgumentException("Couldn’t read flight e-ticket text.")
        }

        val pnr = extractPnr(text)
            ?: throw IllegalArgumentException("Couldn’t find a Booking Ref (PNR) on that e-ticket.")
        val email = extractEmail(text)
        val phone = extractPhone(text)
        val passenger = cleanPassengerName(extractPassenger(text), email)
        val ticketNumber = extractTicketNumber(text)
        val airline = detectAirline(text)
        val seat = extractSeat(text)
        val baggageFromRules = extractBaggage(text)

        val issueDate = parseAirlineDate(
            labeledValue(text, "Issuing Date", "Issue Date", pattern = """(\d{1,2}[A-Za-z]{3}\d{2,4})""")
                ?: firstMatch(text, Regex("""(?i)\bIssuing?\s*Date\s*[:\-]?\s*(\d{1,2}[A-Za-z]{3}\d{2,4})\b"""))
        )
        val issuingOffice = labeledValue(
            text,
            "Issuing Office",
            pattern = """([^\n\r]{8,90})"""
        )?.trim()?.take(80)

        val totalPaid = firstMatch(
            text,
            Regex("""(?i)\bTotal\s*(?:Amount|Fare)\s*[:\-]?\s*(?:KES|K\.?\s*Shs\.?|KSh|Ksh)?\s*([0-9][0-9,]*(?:\.[0-9]{2})?)\b""")
        ) ?: firstMatch(
            text,
            Regex("""(?i)\bTotal\s*(?:Amount|Fare)\s*[:\-]?\s*([0-9][0-9,]*(?:\.[0-9]{2})?)\s*(?:KES|KSh)\b""")
        )
        val baseFareUsd = firstMatch(
            text,
            Regex("""(?i)\bFare\s*[:\-]?\s*USD\s*([0-9]+(?:\.[0-9]{2})?)\b""")
        )
        val fareKes = firstMatch(
            text,
            Regex("""(?i)\bEquiv(?:alent)?(?:\s*Fare(?:\s*Amount)?)?\s*[:\-]?\s*(?:KES|KSh)?\s*([0-9][0-9,]*(?:\.[0-9]{2})?)\b""")
        ) ?: firstMatch(
            // Fly540: "Fare 9,880.00 KES" — avoid matching "Total Fare"
            text,
            Regex("""(?i)(?:^|[\n\r])\s*Fare\s*[:\-]?\s*([0-9][0-9,]*(?:\.[0-9]{2})?)\s*(?:KES|KSh)\b""")
        ) ?: firstMatch(
            text,
            Regex("""(?i)(?:^|[\n\r])\s*Fare\s*[:\-]?\s*(?:KES|KSh)\s*([0-9][0-9,]*(?:\.[0-9]{2})?)\b""")
        )
        val taxes = firstMatch(
            text,
            Regex("""(?i)\bTotal\s*Taxes?\s*[:\-]?\s*(?:KES|KSh)?\s*([0-9][0-9,]*(?:\.[0-9]{2})?)\b""")
        )
        val formOfPayment = labeledValue(text, "Form of Payment", "Payment", pattern = """([^\n\r]{4,50})""")
            ?.trim()?.take(40)
            ?: firstMatch(text, Regex("""(?i)\b(Paid\s*Invoice|Paid)\b"""))

        val segments = parseSegments(text)
        val primary = segments.firstOrNull()
        val documentKind = detectDocumentKind(text)
        val tripType = detectTripType(text, segments)

        var fromShort = shortenAirport(primary?.from)
        var toShort = shortenAirport(primary?.to)
        // Document-wide IATA codes as last-resort route (layout-independent)
        if (fromShort == null || toShort == null) {
            val iatas = findIataCodes(text)
            if (iatas.size >= 2) {
                fromShort = fromShort ?: iatas[0]
                toShort = toShort ?: iatas[1]
            }
        }
        val route = when {
            fromShort != null && toShort != null -> "$fromShort → $toShort"
            else -> null
        }

        val title = when {
            primary?.flightNumber != null && route != null ->
                "${primary.flightNumber} · $route"
            primary?.flightNumber != null -> primary.flightNumber!!
            airline != null && route != null -> "$airline · $route"
            else -> when (documentKind) {
                DocumentKind.ITINERARY -> "Flight itinerary"
                DocumentKind.RECEIPT -> "Flight e-ticket"
            }
        }

        val seatOrTier = listOfNotNull(
            seat?.let { "Seat $it" },
            primary?.bookingClass?.let { cabin ->
                if (cabin.length == 1) "Class $cabin" else cabin
            },
            (primary?.baggage ?: baggageFromRules)?.let { "Bag $it" }
        ).joinToString(" · ").ifBlank { null }

        val depTerminal = normalizeTerminal(primary?.fromTerminal)
        val arrTerminal = normalizeTerminal(primary?.toTerminal)

        val returnSeg = segments.getOrNull(1)?.takeIf { tripType == TripType.RETURN }
        val returnRoute = returnSeg?.let { seg ->
            val f = shortenAirport(seg.from)
            val t = shortenAirport(seg.to)
            when {
                f != null && t != null -> "$f → $t"
                else -> null
            }
        }

        val notes = buildList {
            airline?.let { add("Airline: $it") }
            add("Doc: ${documentKind.label()}")
            add("Trip: ${tripType.label()}")
            fromShort?.let { add("Origin: $it") }
            toShort?.let { add("Destination: $it") }
            passenger?.let { add("Passenger: ${it.take(48)}") }
            email?.let { add("Email: ${it.take(64)}") }
            phone?.let { add("Phone: $it") }
            ticketNumber?.let { add("Ticket no: ${it.take(28)}") }
            issueDate?.let {
                add("Issued: ${it.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))}")
            }
            issuingOffice?.let { add("Issued at: ${it.take(60)}") }
            depTerminal?.let { add("Dep terminal: $it") }
            arrTerminal?.let { add("Arr terminal: $it") }
            primary?.arriveAtMillis?.let { add("Arrival: ${formatWhen(it)}") }
            primary?.status?.let { add("Status: $it") }
            primary?.fareBasis?.let { add("Fare basis: $it") }
            primary?.duration?.let { add("Duration: $it") }
            primary?.operatedBy?.let { add("Operated by: $it") }
            baseFareUsd?.let { add("Base fare: USD $it") }
            fareKes?.let { add("Fare equiv: KES $it") }
            taxes?.let { add("Taxes: KES $it") }
            totalPaid?.let { add("Total: KES $totalPaid") }
            formOfPayment?.let { add("Payment: ${it.take(40)}") }
            when (tripType) {
                TripType.RETURN -> {
                    returnSeg?.let { seg ->
                        val r = listOfNotNull(
                            seg.flightNumber,
                            returnRoute,
                            seg.departAtMillis?.let { formatWhenCompact(it) },
                            seg.bookingClass?.let { c -> if (c.length == 1) "Class $c" else c }
                        ).joinToString(" / ")
                        add("Return: $r")
                    }
                    segments.drop(2).forEachIndexed { index, seg ->
                        add("Leg ${index + 3}: ${formatLegSummary(seg)}")
                    }
                }
                TripType.MULTI_CITY -> {
                    segments.drop(1).forEachIndexed { index, seg ->
                        add("Leg ${index + 2}: ${formatLegSummary(seg)}")
                    }
                }
                TripType.ONE_WAY -> Unit
            }
            add(
                when (documentKind) {
                    DocumentKind.RECEIPT -> "E-ticket receipt — add boarding pass at check-in"
                    DocumentKind.ITINERARY -> "Itinerary — add boarding pass at check-in"
                }
            )
        }.joinToString(" · ")

        return TicketImport(
            title = title,
            venue = route,
            startsAtMillis = primary?.departAtMillis,
            seatOrTier = seatOrTier,
            barcodeValue = BookingConfirmationParser.confirmationBarcode(pnr),
            barcodeFormat = TicketBarcodeFormat.QR_CODE,
            orderId = pnr.uppercase(Locale.US),
            notes = notes,
            source = TicketSource.BOOKING_CONFIRMATION,
            expectsBoardingPass = true,
            hasBoardingPass = false
        )
    }

    internal fun detectDocumentKind(text: String): DocumentKind {
        val lower = text.lowercase(Locale.US)
        val receiptScore = listOf(
            "electronic ticket receipt",
            "e-ticket receipt",
            "ticket number",
            "ticket numbers",
            "issuing office",
            "issuing date",
            "fare calculation",
            "form of payment",
            "equiv fare",
            "total amount"
        ).count { lower.contains(it) }
        val itineraryScore = listOf(
            "itinerary",
            "departing",
            "returning",
            "your local airline",
            "flight ticket numbers",
            "cabin status",
            "rules flight"
        ).count { lower.contains(it) }
        // Explicit receipt header wins
        if (lower.contains("electronic ticket receipt") || lower.contains("e-ticket receipt")) {
            return DocumentKind.RECEIPT
        }
        // Explicit itinerary without receipt header
        if (lower.contains("itinerary") && receiptScore < 3) {
            return DocumentKind.ITINERARY
        }
        return if (itineraryScore > receiptScore) DocumentKind.ITINERARY else DocumentKind.RECEIPT
    }

    internal fun detectTripType(text: String, segments: List<Segment>): TripType {
        val lower = text.lowercase(Locale.US)
        val hasReturningLabel = Regex("""(?i)\bReturning\b""").containsMatchIn(text) ||
            Regex("""(?i)\bReturn(?:ing)?\s*(?:Flight|Leg|Journey)\b""").containsMatchIn(text)
        val hasDepartingLabel = Regex("""(?i)\bDeparting\b""").containsMatchIn(text)

        if (segments.size >= 3) return TripType.MULTI_CITY

        if (segments.size == 2) {
            val a = segments[0]
            val b = segments[1]
            val aFrom = shortenAirport(a.from)
            val aTo = shortenAirport(a.to)
            val bFrom = shortenAirport(b.from)
            val bTo = shortenAirport(b.to)
            val isMirror = aFrom != null && aTo != null && aFrom == bTo && aTo == bFrom
            return when {
                isMirror || hasReturningLabel -> TripType.RETURN
                else -> TripType.MULTI_CITY
            }
        }

        if (segments.size <= 1) {
            // Fare calc like NBO … MBA … NBO without parsed second flight
            val iatas = findIataCodes(text)
            if (iatas.size >= 3 && iatas.first() == iatas.last() && iatas.distinct().size >= 2) {
                return TripType.RETURN
            }
            if (hasReturningLabel && hasDepartingLabel) return TripType.RETURN
            if (lower.contains("round trip") || lower.contains("round-trip")) return TripType.RETURN
            return TripType.ONE_WAY
        }

        return TripType.ONE_WAY
    }

    private fun formatLegSummary(seg: Segment): String =
        listOfNotNull(
            seg.flightNumber,
            shortenAirport(seg.from)?.let { f ->
                shortenAirport(seg.to)?.let { t -> "$f → $t" }
            },
            seg.departAtMillis?.let { formatWhenCompact(it) },
            seg.bookingClass?.let { c -> if (c.length == 1) "Class $c" else c }
        ).joinToString(" / ")

    private fun DocumentKind.label(): String = when (this) {
        DocumentKind.RECEIPT -> "Receipt"
        DocumentKind.ITINERARY -> "Itinerary"
    }

    private fun TripType.label(): String = when (this) {
        TripType.ONE_WAY -> "One-way"
        TripType.RETURN -> "Return"
        TripType.MULTI_CITY -> "Multi-city"
    }

    // ── Content-based extractors (label-agnostic / multi-layout) ─────────

    private fun extractPnr(text: String): String? =
        firstMatch(text, Regex("""(?i)\bBooking\s*Ref(?:erence)?\s*[:\-]?\s*([A-Z0-9]{5,8})\b"""))
            ?: firstMatch(text, Regex("""(?i)\b(?:PNR|Record\s*Locator|Confirmation(?:\s*Code)?)\s*[:\-]?\s*([A-Z0-9]{5,8})\b"""))
            ?: firstMatch(text, Regex("""(?i)\bRef(?:erence)?\s*(?:No\.?|Number)?\s*[:\-]?\s*([A-Z0-9]{6})\b"""))

    private fun extractTicketNumber(text: String): String? =
        firstMatch(
            text,
            Regex("""(?i)\b(?:Flight\s*)?Ticket\s*Numbers?\s*[:\-]?\s*([0-9]{3}\s*[0-9]{8,12}/\d{2})""")
        )?.replace(Regex("""\s+"""), " ")?.trim()
            ?: firstMatch(text, Regex("""(?i)\bTicket\s*Number\s*[:\-]?\s*([0-9][0-9\s]{10,16})\b"""))
                ?.replace(Regex("""\s+"""), " ")?.trim()
            ?: firstMatch(text, Regex("""(?i)\bTicket\s*(?:No|Number|#)\s*[:\-]?\s*(\d{3}\s*\d{10})\b"""))
                ?.replace(Regex("""\s+"""), " ")?.trim()
            ?: firstMatch(text, Regex("""\b(\d{3}\s+\d{10}/\d{2})\b"""))
            ?: firstMatch(text, Regex("""\b(\d{3}\s+\d{10})\b"""))

    private fun extractPassenger(text: String): String? {
        // KQ-style labeled name
        firstMatch(
            text,
            Regex(
                """(?i)\bPassenger\s*Name\s*[:\-]?\s*([A-Za-z][A-Za-z .'/,-]{2,60}?)(?:\s*\(ADT\)|\s*\(CHD\)|\s*\(INF\)|\s*ADT\b|\R|$)"""
            )
        )?.trim()?.trimEnd(',', '.')?.let { return it }

        // Titled name (MR/MRS/…) — first + optional last only (email local is stripped later)
        firstMatch(
            text,
            Regex(
                """(?i)\b((?:MR|MRS|MS|MISS|DR)\.?\s+[A-Z][A-Za-z'-]+(?:\s+[A-Z][A-Za-z'-]+)?)\b"""
            )
        )?.trim()?.let { return it }

        // "Passenger: Name"
        firstMatch(
            text,
            Regex("""(?i)\bPassenger\s*[:\-]?\s*((?:MR|MRS|MS|MISS|DR)\.?\s+[A-Za-z][A-Za-z .'-]{2,40})""")
        )?.trim()?.let { return it }

        firstMatch(
            text,
            Regex("""(?i)\bPassenger\s*[:\-]?\s*([A-Za-z][A-Za-z .'/,-]{2,48})""")
        )?.trim()?.takeIf {
            !it.equals("Email", true) && !it.equals("Phone", true) &&
                !it.startsWith("Name", true) && !it.equals("Seat", true) &&
                !it.equals("Flight", true)
        }?.let { return it }

        return null
    }

    /** Strip email local-part / embedded email glued onto passenger names (Fly540 tables). */
    private fun cleanPassengerName(raw: String?, email: String?): String? {
        var name = raw?.trim()?.trimEnd(',', '.') ?: return null
        email?.let { em ->
            val at = name.indexOf(em, ignoreCase = true)
            if (at > 0) name = name.substring(0, at).trim()
        }
        email?.substringBefore("@")?.takeIf { it.length >= 4 }?.let { local ->
            val compact = name.uppercase(Locale.US).replace(" ", "")
            val localU = local.uppercase(Locale.US)
            if (compact.endsWith(localU)) {
                var remaining = localU.length
                var cut = name
                while (remaining > 0 && cut.isNotEmpty()) {
                    val c = cut.last()
                    cut = cut.dropLast(1)
                    if (!c.isWhitespace()) remaining--
                }
                name = cut.trim()
            }
        }
        // Drop trailing ALLCAPS token that looks like an email local (no vowels pattern optional)
        val parts = name.split(Regex("""\s+""")).filter { it.isNotBlank() }.toMutableList()
        if (parts.size >= 3) {
            val last = parts.last()
            if (last.length >= 6 && last.all { it.isLetter() } && last == last.uppercase(Locale.US) &&
                !listOf("MR", "MRS", "MS", "MISS", "DR").contains(last)
            ) {
                // Likely glued email local — only strip if an email exists in doc
                if (email != null) parts.removeAt(parts.lastIndex)
            }
        }
        name = parts.joinToString(" ").trim()
        if (name.length < 3) return null
        if (name.matches(Regex("""(?i)^(Passenger|Email|Phone|Seat|Name|Flight).*"""))) return null
        return name.take(48)
    }

    private fun extractEmail(text: String): String? =
        firstMatch(text, Regex("""(?i)\b([A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,})\b"""))

    private fun extractPhone(text: String): String? =
        firstMatch(text, Regex("""(?i)\b(?:Phone|Tel(?:ephone)?|Mobile)\s*[:\-]?\s*(\+?\d{9,15})\b"""))
            ?: firstMatch(text, Regex("""\b(254\d{9})\b"""))

    private fun extractSeat(text: String): String? =
        firstMatch(text, Regex("""(?i)\bSeat\s*(?:No\.?|Number|#)?\s*[:\-]?\s*([0-9]{1,2}[A-F])\b"""))
            ?: firstMatch(text, Regex("""(?i)\bSeat\b[\s\S]{0,120}?\b([1-9]\d?[A-F])\b"""))

    private fun extractBaggage(text: String): String? =
        firstMatch(text, Regex("""(?i)(?:baggage|hold)\s*(?:luggage)?[^\n\r]{0,40}?\b(\d+\s*kg)\b"""))
            ?.replace(" ", "")?.uppercase(Locale.US)
            ?: firstMatch(text, Regex("""(?i)\b(\d+\s*kg)\s*(?:hold|baggage)"""))
                ?.replace(" ", "")?.uppercase(Locale.US)
            ?: firstMatch(text, Regex("""(?i)\b(?:Baggage|Bag)\s*[:\-]?\s*(\d+\s*PC|\d+PC)\b"""))
                ?.replace(" ", "")?.uppercase(Locale.US)

    /** Pull a value after any of the given labels (order-independent layout). */
    private fun labeledValue(
        text: String,
        vararg labels: String,
        pattern: String
    ): String? {
        for (label in labels) {
            val escaped = Regex.escape(label).replace("\\ ", """\s+""")
            firstMatch(
                text,
                Regex("""(?i)\b$escaped\s*[:\-]?\s*$pattern""")
            )?.let { return it }
        }
        return null
    }

    /** Airport codes mentioned anywhere (NBO, MBA, …) — layout-independent. */
    private fun findIataCodes(text: String): List<String> {
        val known = setOf("NBO", "MBA", "KIS", "EDL", "MYD", "ZNZ", "JRO", "DAR", "EBB", "KGL")
        return Regex("""\b([A-Z]{3})\b""").findAll(text.uppercase(Locale.US))
            .map { it.groupValues[1] }
            .filter { it in known }
            .distinct()
            .toList()
    }

    /** Prefer short terminal ids (1D, 1); drop airport codes mistaken as terminals. */
    private fun normalizeTerminal(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val t = raw.trim().uppercase(Locale.US)
        if (t in setOf("JKIA", "HKJK", "WIL", "NBO", "MBA", "KIS", "EDL", "MYD")) return null
        return t.take(6)
    }

    internal fun parseSegments(text: String): List<Segment> {
        val flights = CARRIER.findAll(text)
            .map { it.groupValues[1].replace(" ", "").uppercase(Locale.US) }
            .filter { !it.startsWith("KQA") } // avoid false hits
            .distinct()
            .toList()

        if (flights.isEmpty()) return emptyList()

        val segments = mutableListOf<Segment>()
        for ((index, flight) in flights.withIndex()) {
            val parts = splitFlightNumber(flight) ?: continue
            val (code, number) = parts
            val flightPattern = Regex(
                """(?i)\b${Regex.escape(code)}\s?${Regex.escape(number)}\b"""
            )
            val match = flightPattern.find(text) ?: continue
            val nextStart = flights.getOrNull(index + 1)?.let { next ->
                val np = splitFlightNumber(next) ?: return@let text.length
                Regex("""(?i)\b${Regex.escape(np.first)}\s?${Regex.escape(np.second)}\b""")
                    .find(text, match.range.first + 1)?.range?.first
            } ?: text.length

            val prevEnd = if (index == 0) {
                0
            } else {
                val prev = flights[index - 1]
                val pp = splitFlightNumber(prev)
                if (pp == null) {
                    0
                } else {
                    Regex("""(?i)\b${Regex.escape(pp.first)}\s?${Regex.escape(pp.second)}\b""")
                        .findAll(text)
                        .lastOrNull { it.range.first < match.range.first }
                        ?.range?.last?.plus(1) ?: 0
                }
            }

            val routeChunk = text.substring(
                maxOf(prevEnd, match.range.first - 320),
                match.range.first
            )
            val detailChunk = text.substring(match.range.first, nextStart.coerceAtMost(text.length))
            // Cap detail so Rules / next tables don't bleed (Fly540 rules are long)
            val detailCapped = detailChunk.take(420)
            val chunk = routeChunk + "\n" + detailCapped

            val labeledFrom = airportNear(routeChunk, "From") ?: airportNear(chunk, "From")
            val labeledTo = airportNear(routeChunk, "To") ?: airportNear(chunk, "To")
            // Cities on the flight row (Fly540-style itinerary tables)
            val citiesInWindow = findCityAirports(
                routeChunk.takeLast(120) + "\n" + detailCapped.take(200)
            )
            val from: String?
            val to: String?
            when {
                labeledFrom != null && labeledTo != null -> {
                    from = labeledFrom
                    to = labeledTo
                }
                citiesInWindow.size >= 2 -> {
                    from = citiesInWindow[0]
                    to = citiesInWindow[1]
                }
                else -> {
                    from = labeledFrom ?: citiesInWindow.getOrNull(0)
                    to = labeledTo
                }
            }

            val fromTerminal = firstMatch(
                routeChunk.ifBlank { chunk },
                Regex("""(?i)Terminal\s*[:\-]?\s*([A-Z0-9]{1,3})\b""")
            ) ?: firstMatch(chunk, Regex("""(?i)\[(JKIA|HKJK|WIL)\]"""))

            val terminals = Regex("""(?i)Terminal\s*[:\-]?\s*([A-Z0-9]{1,3})\b""")
                .findAll(routeChunk.ifBlank { chunk }).map { it.groupValues[1] }.toList()
            val toTerminal = terminals.getOrNull(1)

            val times = Regex("""\b([01]?\d|2[0-3])[:.]([0-5]\d)\b""")
                .findAll(detailCapped)
                .mapNotNull {
                    runCatching {
                        LocalTime.of(it.groupValues[1].toInt(), it.groupValues[2].toInt())
                    }.getOrNull()
                }
                .toList()

            // KQ: 20Mar2026 · Fly540: 25 Sep 2020 (often before flight number)
            val dateWindow = routeChunk.takeLast(80) + "\n" + detailCapped.take(120)
            val dates = (
                Regex("""\b(\d{1,2}[A-Za-z]{3}\d{2,4})\b""").findAll(dateWindow)
                    .mapNotNull { parseAirlineDate(it.groupValues[1]) } +
                    Regex("""\b(\d{1,2}\s+[A-Za-z]{3,9}\s+\d{2,4})\b""").findAll(dateWindow)
                        .mapNotNull { parseSpacedDate(it.groupValues[1]) }
                ).toList()

            val departDate = dates.getOrNull(0)
            val arriveDate = dates.getOrNull(1) ?: dates.getOrNull(0)
            val departTime = times.getOrNull(0)
            val arriveTime = times.getOrNull(1)

            val departAt = if (departDate != null && departTime != null) {
                LocalDateTime.of(departDate, departTime)
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            } else null
            val arriveAt = if (arriveDate != null && arriveTime != null) {
                LocalDateTime.of(arriveDate, arriveTime)
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            } else null

            val bookingClass = firstMatch(
                detailCapped,
                Regex("""(?i)\bClass\s*[:\-]?\s*([A-Z])\b""")
            ) ?: firstMatch(
                detailCapped,
                Regex("""(?i)\bCabin\s*[:\-]?\s*(Economy|Business|First|Premium\s*Economy)\b""")
            ) ?: firstMatch(
                detailCapped,
                Regex("""(?i)\b(Economy|Business|First)\b""")
            )

            val status = when {
                Regex("""(?i)\bConfirmed\b""").containsMatchIn(detailCapped) -> "Confirmed"
                Regex("""(?i)\bOK\b""").containsMatchIn(detailCapped) -> "OK"
                else -> null
            }

            val baggage = firstMatch(
                detailCapped,
                Regex("""(?i)\b(?:Baggage|Bag)\s*[:\-]?\s*(\d+\s*PC|\d+\s*KG|[0-9]+PC)\b""")
            )?.replace(" ", "")?.uppercase(Locale.US)
                ?: firstMatch(detailCapped, Regex("""(?i)\b(\d+PC)\b"""))

            val fareBasis = firstMatch(
                detailCapped,
                Regex("""(?i)\bFare\s*Basis\s*[:\-]?\s*([A-Z0-9]{3,10})\b""")
            )

            val duration = firstMatch(
                detailCapped,
                Regex("""(?i)\b(?:Duration|Flight\s*Time)\s*[:\-]?\s*(\d{1,2}:\d{2})\b""")
            )

            val operatedBy = detectAirline(detailCapped) ?: detectAirline(text)

            segments += Segment(
                flightNumber = flight,
                from = from,
                to = to,
                fromTerminal = fromTerminal,
                toTerminal = toTerminal,
                departAtMillis = departAt,
                arriveAtMillis = arriveAt,
                bookingClass = bookingClass,
                status = status,
                baggage = baggage,
                fareBasis = fareBasis,
                duration = duration,
                operatedBy = operatedBy
            )
        }

        if (segments.isNotEmpty() && segments.any { it.from == null || it.to == null }) {
            val cityAirports = findCityAirports(text)
            if (cityAirports.size >= 2 && segments.size == 1) {
                val s = segments[0]
                segments[0] = s.copy(
                    from = s.from ?: cityAirports[0],
                    to = s.to ?: cityAirports[1]
                )
            } else if (cityAirports.size >= 2 && segments.size >= 2) {
                segments[0] = segments[0].copy(
                    from = segments[0].from ?: cityAirports[0],
                    to = segments[0].to ?: cityAirports.getOrNull(1)
                )
                segments[1] = segments[1].copy(
                    from = segments[1].from ?: cityAirports.getOrNull(1) ?: cityAirports[0],
                    to = segments[1].to ?: cityAirports[0]
                )
            }
        }

        return segments
    }

    private fun splitFlightNumber(flight: String): Pair<String, String>? {
        val m = Regex(
            """^([A-Z]{2}|[A-Z]\d|\d[A-Z])(\d{2,4})$""",
            RegexOption.IGNORE_CASE
        ).matchEntire(flight.trim()) ?: return null
        return m.groupValues[1].uppercase(Locale.US) to m.groupValues[2]
    }

    private fun detectAirline(text: String): String? = when {
        text.contains("Kenya Airways", ignoreCase = true) -> "Kenya Airways"
        text.contains("Pride of Africa", ignoreCase = true) -> "Kenya Airways"
        text.contains("Fly540", ignoreCase = true) ||
            text.contains("Fly 540", ignoreCase = true) ||
            text.contains("Your Local Airline", ignoreCase = true) -> "Fly540"
        Regex("""\b5H\d{2,4}\b""", RegexOption.IGNORE_CASE).containsMatchIn(text) -> "Fly540"
        else -> null
    }

    private fun findCityAirports(text: String): List<String> =
        Regex(
            """(?i)(Nairobi(?:\s+Int(?:l)?)?(?:\s*\[JKIA\])?|Mombasa(?:\s+Moi)?(?:\s+Int(?:l)?)?|Kisumu|Eldoret|Malindi)"""
        ).findAll(text)
            .map { normalizeAirport(it.groupValues[1]) }
            .distinct()
            .toList()

    private fun airportNear(text: String, label: String): String? {
        val m = Regex(
            """(?i)\b${Regex.escape(label)}\b\s*[:\-]?\s*([A-Za-z][A-Za-z0-9 .'/()\[\]-]{2,55})"""
        ).find(text) ?: return null
        var raw = m.groupValues[1].trim()
            .lineSequence().firstOrNull()?.trim()
            ?: return null
        // Skip table header leftovers
        if (raw.matches(
                Regex(
                    """(?i)^(Cabin|Status|Date|Flight|Arrive|Depart|Departing|Returning|Economy|Business|Confirmed|Rules|Email|Phone|Seat|Total|Fare)\b.*"""
                )
            )
        ) {
            return null
        }
        raw = raw.substringBefore("Operated").substringBefore("Marketed")
            .substringBefore("Flight").trim()
            .trimEnd(',', '.', ';', ':')
        raw = raw.replace(Regex("""\s+\d{1,2}[:.]\d{2}\b.*"""), "").trim()
        raw = raw.replace(Regex("""(?i)\s*Terminal\s*:?\s*[A-Z0-9]+.*"""), "").trim()
        val normalized = normalizeAirport(raw.take(55))
        // Drop unrecognized OCR junk (normalizeAirport echoes unknown strings)
        if (shortenAirport(normalized) == null) return null
        return normalized.takeIf { it.length >= 3 }
    }

    private fun normalizeAirport(name: String): String {
        val n = name.trim().replace(Regex("""\s+"""), " ")
        return when {
            n.contains("JKIA", true) || n.contains("JOMO", true) ||
                n.contains("KENYATTA", true) || n.equals("NBO", true) ->
                "Nairobi JKIA (NBO)"
            n.contains("NAIROBI", true) -> "Nairobi JKIA (NBO)"
            n.contains("MOI", true) || n.equals("MBA", true) -> "Mombasa MOI (MBA)"
            n.contains("MOMBASA", true) -> "Mombasa (MBA)"
            n.contains("KISUMU", true) || n.equals("KIS", true) -> "Kisumu (KIS)"
            n.contains("ELDORET", true) || n.equals("EDL", true) -> "Eldoret (EDL)"
            n.contains("MALINDI", true) || n.equals("MYD", true) -> "Malindi (MYD)"
            else -> n
        }
    }

    private fun shortenAirport(name: String?): String? {
        if (name.isNullOrBlank()) return null
        return when {
            name.contains("NBO") || name.contains("Nairobi", true) ||
                name.contains("JKIA", true) || name.contains("Jomo", true) -> "NBO"
            name.contains("MBA") || name.contains("Mombasa", true) ||
                name.contains("Moi", true) -> "MBA"
            name.contains("KIS") || name.contains("Kisumu", true) -> "KIS"
            name.contains("EDL") || name.contains("Eldoret", true) -> "EDL"
            name.contains("MYD") || name.contains("Malindi", true) -> "MYD"
            name.contains("ZNZ") || name.contains("Zanzibar", true) -> "ZNZ"
            name.contains("JRO") || name.contains("Kilimanjaro", true) -> "JRO"
            name.contains("DAR") || name.contains("Dar es", true) -> "DAR"
            name.contains("EBB") || name.contains("Entebbe", true) -> "EBB"
            name.contains("KGL") || name.contains("Kigali", true) -> "KGL"
            Regex("""\(([A-Z]{3})\)""").find(name) != null -> {
                val code = Regex("""\(([A-Z]{3})\)""").find(name)!!.groupValues[1]
                code.takeIf {
                    it in setOf("NBO", "MBA", "KIS", "EDL", "MYD", "ZNZ", "JRO", "DAR", "EBB", "KGL", "WIL")
                }
            }
            Regex("""^[A-Z]{3}$""").matches(name.trim().uppercase(Locale.US)) -> {
                val code = name.trim().uppercase(Locale.US)
                code.takeIf {
                    it in setOf("NBO", "MBA", "KIS", "EDL", "MYD", "ZNZ", "JRO", "DAR", "EBB", "KGL", "WIL")
                }
            }
            else -> null
        }
    }

    private fun formatWhen(millis: Long): String {
        val dt = java.time.Instant.ofEpochMilli(millis)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()
        return dt.format(DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm", Locale.ENGLISH))
    }

    /** Compact datetime without ` · ` so notes split stays intact. */
    private fun formatWhenCompact(millis: Long): String {
        val dt = java.time.Instant.ofEpochMilli(millis)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()
        return dt.format(DateTimeFormatter.ofPattern("d MMM yyyy HH:mm", Locale.ENGLISH))
    }

    private fun parseAirlineDate(raw: String?): LocalDate? {
        if (raw.isNullOrBlank()) return null
        val compact = raw.trim().replace(" ", "")
        val m = Regex("""(?i)^(\d{1,2})([A-Za-z]{3})(\d{2,4})$""").matchEntire(compact) ?: return null
        val day = m.groupValues[1].toIntOrNull() ?: return null
        val month = parseMonth(m.groupValues[2]) ?: return null
        var year = m.groupValues[3].toIntOrNull() ?: return null
        if (year < 100) year += 2000
        return runCatching { LocalDate.of(year, month, day) }.getOrNull()
    }

    private fun parseSpacedDate(raw: String): LocalDate? {
        val patterns = listOf("d MMM yyyy", "dd MMM yyyy", "d MMMM yyyy", "dd MMMM yyyy")
        for (p in patterns) {
            runCatching {
                return LocalDate.parse(
                    raw.trim(),
                    DateTimeFormatter.ofPattern(p, Locale.ENGLISH)
                )
            }
        }
        return null
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
