package com.lipabill.app.data.tickets

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs

/**
 * One line of ticket text with its position, top-down. PDF lines carry their font size;
 * OCR lines use the box height. Text-only sources use the line index as `top` and 1 as size.
 */
data class TextLine(
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val size: Float
) {
    val height: Float get() = (bottom - top).coerceAtLeast(0.0001f)

    companion object {
        /** Plain text without geometry: reading order only. */
        fun fromPlainText(text: String): List<TextLine> = text.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapIndexed { i, t -> TextLine(t, 0f, i.toFloat(), 1f, i + 0.9f, 1f) }
    }
}

/** What an event ticket says, whatever its layout. Null when the ticket doesn't say it. */
data class EventTicketFields(
    val title: String? = null,
    val venue: String? = null,
    val city: String? = null,
    val startsAtMillis: Long? = null,
    val endsAtMillis: Long? = null,
    val doors: String? = null,
    val attendee: String? = null,
    val tier: String? = null,
    val price: String? = null,
    val admits: Int? = null,
    val orderRef: String? = null,
    val ticketNumber: String? = null,
    val gate: String? = null,
    val section: String? = null,
    val row: String? = null,
    val seat: String? = null,
    val organizer: String? = null
) {
    /** `Label: value` notes the pass screen reads (see EventPassModel). Phone / email never stored. */
    fun toNotes(): String? = listOfNotNull(
        attendee?.let { "Guest: ${clean(it)}" },
        tier?.let { "Tier: ${clean(it)}" },
        doors?.let { "Doors: ${clean(it)}" },
        price?.let { "Price: ${clean(it)}" },
        admits?.takeIf { it > 1 }?.let { "Admits: $it" },
        ticketNumber?.takeIf { it != orderRef }?.let { "Ticket no: ${clean(it)}" },
        organizer?.let { "Organizer: ${clean(it)}" },
        city?.let { "City: ${clean(it)}" },
        endsAtMillis?.let { "Ends: $it" }
    ).joinToString(" · ").ifBlank { null }

    /** Gate / section / row / seat in the `Prefix value · …` form the pass splits on. */
    fun seatLine(): String? = listOfNotNull(
        gate?.let { "Gate ${clean(it)}" },
        section?.let { "Section ${clean(it)}" },
        row?.let { "Row ${clean(it)}" },
        seat?.let { "Seat ${clean(it)}" }
    ).joinToString(" · ").ifBlank { null }

    private fun clean(v: String) = v.replace("·", "-").replace(Regex("\\s+"), " ").trim()
}

/**
 * Layout-agnostic event ticket reader. Works on positioned lines so it copes with
 * `LABEL value`, `Label: value`, labels with the value underneath (column layouts), header
 * lines like `Sat 14 Nov 2026 · 02:00 PM · Venue`, and values that wrap onto a second line.
 */
object EventTicketExtractor {

    internal enum class Field {
        VENUE, CITY, DATE, START_DATE, END_DATE, TIME, START_TIME, END_TIME, DOORS,
        ATTENDEE, TIER, TICKET, PRICE, QUANTITY, ORDER, TICKET_NO, GATE, SECTION, ROW, SEAT,
        ORGANIZER, IGNORE
    }

    /** Label synonyms, matched longest first. */
    private val LABELS: List<Pair<String, Field>> = listOf(
        Field.VENUE to listOf("venue", "location", "event location", "event venue", "where", "address", "place", "venue address"),
        Field.CITY to listOf("city", "town"),
        Field.DATE to listOf("date", "event date", "when", "date & time", "date and time", "day"),
        Field.START_DATE to listOf("start date", "starts", "start", "from", "start date & time"),
        Field.END_DATE to listOf("end date", "ends", "end", "until", "to", "end date & time"),
        Field.TIME to listOf("time", "event time", "show time", "showtime", "kick-off", "kick off", "kickoff"),
        Field.START_TIME to listOf("start time"),
        Field.END_TIME to listOf("end time", "finish"),
        Field.DOORS to listOf("doors", "doors open", "gates open", "gate opens", "entry from", "doors at"),
        Field.ATTENDEE to listOf(
            "attendee", "attendee name", "name", "full name", "guest", "guest name", "ticket holder", "holder",
            "holder name", "issued to", "participant", "customer", "customer name", "buyer", "purchaser",
            "booked by", "ticket owner", "visitor"
        ),
        Field.TIER to listOf(
            "ticket type", "ticket category", "ticket class", "category", "tier", "type", "admission",
            "admission type", "pass type", "package", "class", "zone", "ticket tier", "ticket name"
        ),
        Field.TICKET to listOf("ticket"),
        Field.PRICE to listOf(
            "price", "amount", "amount paid", "total", "total paid", "paid", "cost", "ticket price", "fee",
            "total amount", "order total", "subtotal"
        ),
        Field.QUANTITY to listOf("quantity", "qty", "admits", "admit", "no. of tickets", "number of tickets", "tickets"),
        Field.ORDER to listOf(
            "order", "order no", "order number", "order id", "order #", "booking", "booking ref", "booking reference",
            "booking id", "booking code", "reference", "ref", "ref no", "confirmation", "confirmation code",
            "confirmation number", "invoice", "receipt", "transaction id"
        ),
        Field.TICKET_NO to listOf(
            "ticket no", "ticket number", "ticket #", "ticket id", "ticket code", "serial", "serial no", "barcode", "code"
        ),
        Field.GATE to listOf("gate", "entrance", "entry", "door"),
        Field.SECTION to listOf("section", "block", "stand", "area", "level", "floor"),
        Field.ROW to listOf("row"),
        Field.SEAT to listOf("seat", "seat no", "seat number", "seats"),
        Field.ORGANIZER to listOf(
            "organizer", "organiser", "organized by", "organised by", "presented by", "hosted by", "host",
            "promoter", "storefront", "sold by", "brought to you by"
        ),
        Field.IGNORE to listOf("phone", "phone number", "mobile", "email", "e-mail", "id no", "id number", "passport")
    ).flatMap { (field, words) -> words.map { it to field } }
        .sortedByDescending { it.first.length }

    /** Labels strong enough to take an inline value without a colon (`ATTENDEE Jane Doe`). */
    private val STRONG = setOf(
        Field.ATTENDEE, Field.ORGANIZER, Field.VENUE, Field.START_DATE, Field.END_DATE, Field.TICKET,
        Field.TICKET_NO, Field.ORDER, Field.PRICE, Field.TIER, Field.DOORS, Field.SEAT, Field.ROW,
        Field.GATE, Field.SECTION, Field.IGNORE
    )

    private val BOILERPLATE = Regex(
        """(?i)^(e[- ]?tickets?|tickets?|your tickets?|admit one|admission ticket|order confirmation|booking confirmation|""" +
            """confirmation|thank you.*|powered by.*|terms.*|page \d+.*|scan .*|present .*|this is your ticket.*|event ticket|""" +
            """general admission|official ticket|receipt|invoice|tax invoice|qr code|valid for.*|entry: .*)$"""
    )

    private val MONTHS = mapOf(
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
        "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12
    )
    private const val MONTH = "(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|june?|july?|aug(?:ust)?|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)"
    private const val ORD = "(?:st|nd|rd|th)?"
    private val DAY_FIRST = Regex("""(?i)\b(\d{1,2})$ORD(?:\s*[-–/&]\s*(\d{1,2})$ORD)?\s+$MONTH\.?,?(?:\s+(\d{4}))?\b""")
    private val MONTH_FIRST = Regex("""(?i)\b$MONTH\.?\s+(\d{1,2})$ORD(?:\s*[-–]\s*(\d{1,2})$ORD)?,?(?:\s+(\d{4}))?\b""")
    private val ISO = Regex("""\b(\d{4})-(\d{2})-(\d{2})""")
    private val NUMERIC = Regex("""\b(\d{1,2})[/.](\d{1,2})[/.](\d{2,4})\b""")
    private val TIME = Regex("""(?i)\b(\d{1,2})(?:[:.](\d{2}))?\s*([ap])\.?\s*m\b\.?|\b([01]?\d|2[0-3]):([0-5]\d)\b(?!\s*[ap]\.?m)""")
    private val MONEY = Regex(
        """(?i)(KES|KSHS?|K\.\s?SHS?\.?|USD|UGX|TZS|RWF|NGN|ZAR|EUR|GBP|US\$|\$|€|£)\s?([0-9][0-9,]*(?:\.[0-9]{1,2})?)|""" +
            """([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s?(KES|KSHS?|USD|UGX|TZS|RWF|NGN|ZAR|EUR|GBP)\b"""
    )
    private val QTY = Regex("""(?i)\b(\d{1,2})\s*(?:x\s*)?tick""")
    private val OF = Regex("""(?i)\b(\d{1,2})\s*(?:of|/)\s*(\d{1,2})\b""")
    private val CODE = Regex("""^[A-Z0-9][A-Z0-9\-_/#]{3,}$""", RegexOption.IGNORE_CASE)

    private val FLIGHT_WORDS = listOf(
        "flight", "itinerary", "departure", "departing", "arrive", "arrival", "cabin", "boarding pass",
        "airline", "aviation", "baggage", "check-in", "pnr", "airport"
    )

    /** True for airline itineraries / flight e-tickets, which aren't event passes. */
    fun looksLikeFlight(text: String): Boolean {
        val lower = text.lowercase(Locale.US)
        // Needs an aviation-only word, so SGR tickets ("departure", "arrival") never trip it.
        val aviation = listOf("flight", "airline", "aviation", "airport", "pnr").any { lower.contains(it) }
        return aviation && FLIGHT_WORDS.count { lower.contains(it) } >= 3
    }

    fun extract(
        lines: List<TextLine>,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): EventTicketFields {
        val ordered = lines
            .map { it.copy(text = it.text.replace(' ', ' ').replace(Regex("\\s+"), " ").trim()) }
            .filter { it.text.isNotEmpty() }
            .sortedWith(compareBy({ it.top }, { it.left }))
        if (ordered.isEmpty()) return EventTicketFields()

        val used = HashSet<Int>()
        val values = LinkedHashMap<Field, String>()
        var sawPriceLabel = false
        fun put(field: Field, value: String?) {
            val v = value?.trim()?.trim(':', '-', '|', '·', '•', ',')?.trim()
            if (v.isNullOrEmpty() || field in values) return
            // Seating values are short codes; a sentence ("QR scan at the gate") is not a gate.
            if (field in SEATING && (v.length > 16 || v.split(" ").size > 3)) return
            // References always carry a digit ("ORDER CONFIRMATION" is a heading, not a ref).
            if ((field == Field.ORDER || field == Field.TICKET_NO) && v.none(Char::isDigit)) return
            // "Ticket" means a number when it's a code, the tier when it's words.
            if (field == Field.TICKET) {
                val code = v.replace(" ", "")
                if (CODE.matches(code) && code.any(Char::isDigit)) {
                    if (Field.TICKET_NO !in values) values[Field.TICKET_NO] = code
                } else if (Field.TIER !in values) {
                    values[Field.TIER] = v
                }
                return
            }
            values[field] = v
        }

        // 1) Label → value, same line or the line under it in the same column.
        ordered.forEachIndexed { i, line ->
            val hit = matchLabel(line.text) ?: return@forEachIndexed
            used += i
            val (field, inline) = hit
            if (field == Field.PRICE) sawPriceLabel = true
            if (field == Field.IGNORE) {
                // Swallow the value too so a phone / email never becomes a name or a title.
                if (inline.isNullOrBlank()) findValueLine(ordered, i)?.let { used += it }
                return@forEachIndexed
            }
            if (!inline.isNullOrBlank()) {
                put(field, inline)
            } else {
                val j = findValueLine(ordered, i) ?: return@forEachIndexed
                used += j
                val text = if (field in MULTILINE) withContinuation(ordered, j, used) else ordered[j].text
                put(field, text)
            }
        }

        // 2) Header line: "Sat 14 Nov 2026 · 02:00 PM · Venue" (venue may wrap to the next line).
        var headerVenue: String? = null
        var headerDate: DateHit? = null
        var headerTimes: List<LocalTime> = emptyList()
        for ((i, line) in ordered.withIndex()) {
            if (i in used) continue
            val parts = line.text.split(Regex("""\s*[·•|]\s*|\s+@\s+|\s+-\s+(?=[A-Z])""")).filter { it.isNotBlank() }
            if (parts.size < 2) continue
            val date = findDate(line.text, today)
            val times = findTimes(line.text)
            if (date == null && times.isEmpty()) continue
            val rest = parts.filter { findDate(it, today) == null && findTimes(it).isEmpty() && MONEY.find(it) == null }
            if (headerDate == null) headerDate = date
            if (headerTimes.isEmpty()) headerTimes = times
            if (rest.isNotEmpty() && headerVenue == null) {
                var venue = rest.last()
                val next = ordered.getOrNull(i + 1)
                if (next != null && (i + 1) !in used && next.text.split(" ").size <= 3 &&
                    next.text.first().isUpperCase() && matchLabel(next.text) == null &&
                    findDate(next.text, today) == null && findTimes(next.text).isEmpty() &&
                    abs(next.size - line.size) <= line.size * 0.25f
                ) {
                    venue = "$venue ${next.text}"
                    used += i + 1
                }
                headerVenue = venue
            }
            used += i
            if (headerDate != null && headerVenue != null) break
        }

        // 3) Dates and times.
        val dateText = values[Field.START_DATE] ?: values[Field.DATE]
        val startDate = dateText?.let { findDate(it, today) } ?: headerDate
            ?: ordered.firstNotNullOfOrNull { findDate(it.text, today) }
        val startTimes = (values[Field.START_TIME] ?: values[Field.TIME] ?: values[Field.START_DATE] ?: values[Field.DATE])
            ?.let { findTimes(it) }?.takeIf { it.isNotEmpty() }
            ?: headerTimes.takeIf { it.isNotEmpty() }
            ?: emptyList()
        val endDateHit = values[Field.END_DATE]?.let { findDate(it, today) }
        val endTime = (values[Field.END_TIME] ?: values[Field.END_DATE])?.let { findTimes(it).firstOrNull() }
            ?: startTimes.getOrNull(1)
        val startAt = startDate?.let { d -> LocalDateTime.of(d.start, startTimes.firstOrNull() ?: LocalTime.MIDNIGHT) }
        val endAt = if (startAt != null && endTime != null) {
            val day = endDateHit?.start ?: startDate.end ?: startDate.start
            var end = LocalDateTime.of(day, endTime)
            if (!end.isAfter(startAt)) end = end.plusDays(1)
            end
        } else {
            null
        }

        // 4) Ticket label is overloaded: a code means ticket number, words mean the tier.
        val ticketValue = values[Field.TICKET]
        val ticketIsCode = ticketValue != null && CODE.matches(ticketValue.replace(" ", "")) && ticketValue.any { it.isDigit() }
        val ticketNumber = (values[Field.TICKET_NO]?.replace(" ", "")?.takeIf { CODE.matches(it) && it.any(Char::isDigit) })
            ?: ticketValue?.takeIf { ticketIsCode }?.replace(" ", "")
        val tier = (values[Field.TIER] ?: ticketValue?.takeIf { !ticketIsCode })?.let(::shortTier)

        // 5) Money and quantity, labelled first, then anywhere on the ticket.
        val priceSource = values[Field.PRICE]
        val price = priceSource?.let(::formatMoney) ?: ordered.firstNotNullOfOrNull { line ->
            if (Regex("(?i)total|paid|amount|price").containsMatchIn(line.text)) formatMoney(line.text) else null
        } ?: (if (sawPriceLabel) ordered.firstNotNullOfOrNull { formatMoney(it.text) } else null)
            ?: if (priceSource?.contains("free", true) == true) "Free" else null
        val admits = (values[Field.QUANTITY]?.let { Regex("\\d+").find(it)?.value?.toIntOrNull() })
            ?: (priceSource?.let { QTY.find(it)?.groupValues?.get(1)?.toIntOrNull() })
            ?: ordered.firstNotNullOfOrNull { QTY.find(it.text)?.groupValues?.get(1)?.toIntOrNull() }
            ?: values[Field.TICKET]?.let { OF.find(it)?.groupValues?.get(2)?.toIntOrNull() }

        // 6) Title: the most prominent meaningful line near the top.
        val organizer = values[Field.ORGANIZER]
        val title = pickTitle(ordered, used, organizer, today)

        // 7) Attendee: labelled, else a bare personal-name line beside the ticket code.
        val attendee = values[Field.ATTENDEE]?.takeIf(::looksLikeName)
            ?: nameNearCode(ordered, used, title)

        val venue = (values[Field.VENUE] ?: headerVenue)
            ?.takeIf { findDate(it, today) == null && MONEY.find(it) == null }

        return EventTicketFields(
            title = title,
            venue = venue,
            city = values[Field.CITY],
            startsAtMillis = startAt?.atZone(zone)?.toInstant()?.toEpochMilli(),
            endsAtMillis = endAt?.atZone(zone)?.toInstant()?.toEpochMilli(),
            doors = values[Field.DOORS]?.let { findTimes(it).firstOrNull()?.let(::formatTime) ?: it },
            attendee = attendee?.let(::tidyName),
            tier = tier,
            price = price,
            admits = admits,
            orderRef = values[Field.ORDER]?.replace(" ", "")?.takeIf { it.length >= 4 && it.any(Char::isDigit) },
            ticketNumber = ticketNumber,
            gate = values[Field.GATE]?.take(16),
            section = values[Field.SECTION]?.take(16),
            row = values[Field.ROW]?.take(8),
            seat = values[Field.SEAT]?.take(12),
            organizer = organizer?.take(40)
        )
    }

    private val MULTILINE = setOf(Field.VENUE, Field.TIER, Field.ORGANIZER)
    private val SEATING = setOf(Field.GATE, Field.SECTION, Field.ROW, Field.SEAT)

    /** Matches a label at the start of [text]; returns the field and any inline value. */
    internal fun matchLabel(text: String): Pair<Field, String?>? {
        val lower = text.lowercase(Locale.US)
        for ((word, field) in LABELS) {
            if (!lower.startsWith(word)) continue
            val rest = text.substring(word.length)
            if (rest.isEmpty()) return field to null
            val next = rest.first()
            if (next.isLetterOrDigit()) continue // "Timeless" is not "time"
            val trimmed = rest.trimStart(' ', ':', '#', '.', '-', '–', '\t')
            val hasColon = rest.trimStart().startsWith(":") || rest.trimStart().startsWith("#")
            if (trimmed.isEmpty()) return if (hasColon || text.trim().length <= 24) field to null else null
            val labelIsCaps = text.substring(0, word.length).let { it == it.uppercase(Locale.US) }
            return when {
                hasColon -> field to trimmed
                labelIsCaps && field in STRONG -> field to trimmed
                else -> null
            }
        }
        return null
    }

    /** Value line for the label at [i]: to its right on the same row, else below in its column. */
    private fun findValueLine(lines: List<TextLine>, i: Int): Int? {
        val label = lines[i]
        var best: Int? = null
        var bestScore = Float.MAX_VALUE
        for (j in lines.indices) {
            if (j == i) continue
            val c = lines[j]
            if (matchLabel(c.text) != null) continue
            val sameRow = abs(c.top - label.top) < label.height * 0.6f && c.left >= label.right - 1f
            val dy = c.top - label.top
            // Same column, or indented into a value column that starts past the label.
            val aligned = abs(c.left - label.left) <= maxOf((label.right - label.left) * 0.8f, label.height * 1.5f) ||
                (c.left > label.left && c.left <= label.right + label.height * 4f)
            val score = when {
                sameRow -> c.left - label.right
                dy > 0 && dy <= label.height * 4.5f && aligned -> 10_000f + dy
                else -> continue
            }
            if (score < bestScore) {
                bestScore = score
                best = j
            }
        }
        return best
    }

    /** Joins up to two wrapped lines under [j] that sit in the same column at the same size. */
    private fun withContinuation(lines: List<TextLine>, j: Int, used: MutableSet<Int>): String {
        val first = lines[j]
        val parts = mutableListOf(first.text)
        var prev = first
        for (k in (j + 1) until minOf(lines.size, j + 6)) {
            if (parts.size >= 3) break
            val c = lines[k]
            if (k in used || matchLabel(c.text) != null) continue
            val gap = c.top - prev.bottom
            if (gap < -prev.height * 0.3f) continue // same row, different column
            if (gap > prev.height * 0.9f) break
            if (abs(c.left - first.left) > first.height * 1.2f) continue
            if (abs(c.size - first.size) > first.size * 0.25f) break
            if (findTimes(c.text).isNotEmpty() || MONEY.find(c.text) != null) break
            parts += c.text
            used += k
            prev = c
        }
        return parts.joinToString(" ")
    }

    private fun pickTitle(lines: List<TextLine>, used: Set<Int>, organizer: String?, today: LocalDate): String? {
        val maxTop = lines.maxOf { it.bottom }
        val minTop = lines.minOf { it.top }
        val span = (maxTop - minTop).coerceAtLeast(1f)
        val candidates = lines.withIndex().filter { (i, l) ->
            val t = l.text
            i !in used &&
                (l.top - minTop) / span <= 0.5f &&
                t.count(Char::isLetter) >= 3 &&
                !t.startsWith("(") && !t.endsWith(":") &&
                !BOILERPLATE.matches(t) &&
                matchLabel(t) == null &&
                findDate(t, today) == null && findTimes(t).isEmpty() && MONEY.find(t) == null &&
                !t.contains("@") && !Regex("""(?i)https?://|www\.""").containsMatchIn(t) &&
                t.split(" ").size <= 12 &&
                (organizer == null || !t.equals(organizer, ignoreCase = true))
        }
        if (candidates.isEmpty()) return null
        val maxSize = candidates.maxOf { it.value.size }
        // Biggest type wins; among near-equal sizes, the higher line.
        val (idx, line) = candidates.filter { it.value.size >= maxSize * 0.92f }.minBy { it.value.top }
        var title = line.text
        // A title set in big type can wrap onto a second line.
        lines.getOrNull(idx + 1)?.let { n ->
            if ((idx + 1) !in used && abs(n.size - line.size) <= line.size * 0.08f && n.size > 1f &&
                n.top - line.bottom < line.height * 0.8f && matchLabel(n.text) == null &&
                findDate(n.text, today) == null && n.text.count(Char::isLetter) >= 3
            ) {
                title = "$title ${n.text}"
            }
        }
        return prettyCase(title).take(80)
    }

    /** Personal-name line (2–4 words, letters only) within a couple of lines of a ticket code. */
    private fun nameNearCode(lines: List<TextLine>, used: Set<Int>, title: String?): String? {
        val codeRows = lines.withIndex().filter { (_, l) ->
            Regex("""(?i)\b(ticket|order|ref)\b""").containsMatchIn(l.text) && l.text.any(Char::isDigit)
        }
        for ((ci, code) in codeRows) {
            val near = lines.withIndex().filter { (i, l) ->
                i != ci && i !in used && abs(l.top - code.top) <= code.height * 3f
            }.sortedBy { abs(it.value.top - code.top) }
            near.firstOrNull { (_, l) ->
                looksLikeName(l.text) && (title == null || !l.text.equals(title, ignoreCase = true))
            }?.let { return it.value.text }
        }
        return null
    }

    private fun looksLikeName(v: String): Boolean {
        val words = v.trim().split(Regex("\\s+"))
        return words.size in 1..5 && words.all { w -> w.all { it.isLetter() || it == '.' || it == '\'' || it == '-' } } &&
            v.count(Char::isLetter) >= 3 && !BOILERPLATE.matches(v) && matchLabel(v) == null
    }

    private fun tidyName(v: String): String = prettyCase(v.trim())

    /** "VVIP Offer" stays; "Long Weekend Pass - Access to …" becomes "Long Weekend Pass". */
    private fun shortTier(v: String): String {
        val cut = v.split(Regex("""\s+[-–—]\s+|\s*\(""")).first().trim()
        return (if (cut.length >= 2) cut else v).take(32)
    }

    /** Sports club prefixes kept in capitals (not short words like "as", "us", "if"). */
    private val CLUB_ABBREVIATIONS = setOf(
        "fc", "afc", "sc", "ac", "cf", "fk", "sk", "bk", "sv", "vfb", "vfl", "psv", "rfc", "hc", "sfc", "kcb"
    )

    private fun prettyCase(v: String): String {
        val letters = v.filter(Char::isLetter)
        if (letters.length < 4 || letters != letters.uppercase(Locale.US)) return v
        return v.lowercase(Locale.US).split(" ").joinToString(" ") { w ->
            when {
                w.length <= 3 && w.any { !it.isLetter() } || w in setOf("vip", "vvip", "ga", "dj", "mc") ->
                    w.uppercase(Locale.US)
                // Club prefixes stay capitals: "FC Bayern Munich", "AFC Ajax".
                w in CLUB_ABBREVIATIONS -> w.uppercase(Locale.US)
                // "BAYERN VS AJAX" reads "Bayern vs Ajax".
                w == "vs" || w == "vs." || w == "v" -> "vs"
                else -> w.replaceFirstChar { it.titlecase(Locale.US) }
            }
        }
    }

    private fun formatMoney(text: String): String? {
        val m = MONEY.find(text) ?: return null
        val (cur, amount) = if (m.groupValues[2].isNotEmpty()) {
            m.groupValues[1] to m.groupValues[2]
        } else {
            m.groupValues[4] to m.groupValues[3]
        }
        val code = when (cur.uppercase(Locale.US).replace(" ", "").replace(".", "")) {
            "KES", "KSH", "KSHS", "KSHS." -> "KES"
            "US$", "$", "USD" -> "USD"
            "€", "EUR" -> "EUR"
            "£", "GBP" -> "GBP"
            else -> cur.uppercase(Locale.US).replace(".", "")
        }
        return "$code $amount"
    }

    private fun formatTime(t: LocalTime): String =
        String.format(Locale.US, "%d:%02d %s", if (t.hour % 12 == 0) 12 else t.hour % 12, t.minute, if (t.hour < 12) "AM" else "PM")

    internal data class DateHit(val start: LocalDate, val end: LocalDate?)

    internal fun findDate(text: String, today: LocalDate): DateHit? {
        ISO.find(text)?.let { m ->
            return runCatching {
                DateHit(LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()), null)
            }.getOrNull()
        }
        DAY_FIRST.find(text)?.let { m ->
            val month = monthOf(m.groupValues[3]) ?: return@let
            val year = m.groupValues[4].toIntOrNull()
            val d1 = m.groupValues[1].toInt()
            val d2 = m.groupValues[2].toIntOrNull()
            return build(year, month, d1, d2, today)
        }
        MONTH_FIRST.find(text)?.let { m ->
            val month = monthOf(m.groupValues[1]) ?: return@let
            val year = m.groupValues[4].toIntOrNull()
            return build(year, month, m.groupValues[2].toInt(), m.groupValues[3].toIntOrNull(), today)
        }
        NUMERIC.find(text)?.let { m ->
            var a = m.groupValues[1].toInt()
            var b = m.groupValues[2].toInt()
            var y = m.groupValues[3].toInt()
            if (y < 100) y += 2000
            // East African tickets are day-first; flip only when it can't be.
            if (b > 12 && a <= 12) { val t = a; a = b; b = t }
            return runCatching { DateHit(LocalDate.of(y, b, a), null) }.getOrNull()
        }
        return null
    }

    private fun monthOf(raw: String): Int? = MONTHS[raw.lowercase(Locale.US).take(3)]

    private fun build(year: Int?, month: Int, d1: Int, d2: Int?, today: LocalDate): DateHit? = runCatching {
        var y = year ?: today.year
        var start = LocalDate.of(y, month, d1)
        // No year printed: an event far in the past most likely means next year.
        if (year == null && start.isBefore(today.minusDays(60))) {
            y += 1
            start = LocalDate.of(y, month, d1)
        }
        val end = d2?.takeIf { it > d1 }?.let { LocalDate.of(y, month, it) }
        DateHit(start, end)
    }.getOrNull()

    internal fun findTimes(text: String): List<LocalTime> = TIME.findAll(text).mapNotNull { m ->
        runCatching {
            if (m.groupValues[3].isNotEmpty()) {
                var h = m.groupValues[1].toInt()
                val min = m.groupValues[2].toIntOrNull() ?: 0
                val pm = m.groupValues[3].equals("p", ignoreCase = true)
                if (h == 12) h = 0
                if (pm) h += 12
                LocalTime.of(h, min)
            } else {
                LocalTime.of(m.groupValues[4].toInt(), m.groupValues[5].toInt())
            }
        }.getOrNull()
    }.toList()
}
