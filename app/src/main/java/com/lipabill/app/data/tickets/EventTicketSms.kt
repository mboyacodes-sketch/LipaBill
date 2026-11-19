package com.lipabill.app.data.tickets

import com.lipabill.app.data.model.TicketSource
import com.lipabill.app.data.model.withNotesValue
import java.time.LocalDate
import java.time.ZoneId

/**
 * A pasted event-ticket SMS ("Your ticket for … Gate 4, Seat 18. Ticket No … View: https://…").
 * The SMS carries details and a link, not the QR, so the pass is saved with a pending entry
 * code; importing the ticket screenshot or PDF later fills the real code into the same pass.
 */
object EventTicketSms {

    /** Placeholder entry code: never drawn as a QR (see TicketDetailScreen). */
    const val PENDING_PREFIX = "PENDING:"

    /** Notes key for the issuer's "view your ticket" link. */
    const val LINK_KEY = "Link"

    private val URL = Regex("""https?://[^\s,]+""", RegexOption.IGNORE_CASE)
    private val RAIL = Regex("""(?i)\b(madaraka|sgr|train|coach)\b""")
    /** "Talanta Sports City: Your ticket…" → drop the sender label in front. */
    private val SENDER_PREFIX = Regex("""^[A-Za-z][A-Za-z &.'-]{2,40}:\s+(?=[A-Z])""")
    private val BOILERPLATE = Regex(
        """(?i)^(your (ticket|booking|order)|thank you|thanks|dear |hi |hello |congratulations|one scan|ticket void|do not share)|\bis confirmed\b"""
    )
    private val VERSUS = Regex("""(?i)\s(vs\.?|v)\s""")
    /** "Gate 4" / "Seat 18": seat parts written without a colon. */
    private val SEAT_PART = Regex("""(?i)^(gate|entrance|door|block|section|stand|row|seat)\s+([A-Za-z0-9-]{1,8})$""")
    /** "Tue 05 Jan 2027 20:15": a date and a time on one unlabelled line. */
    private val DATE_THEN_TIME = Regex("""^(.*\b\d{4})\s+(\d{1,2}[:.]\d{2}(?:\s?[AaPp]\.?[Mm]\.?)?)$""")

    /** Rewrites SMS shorthand into the `Label: value` lines the extractor reads. */
    private fun labelled(line: String): List<String> {
        SEAT_PART.matchEntire(line)?.let { m -> return listOf("${m.groupValues[1]}: ${m.groupValues[2]}") }
        if (!line.contains(':') || DATE_THEN_TIME.matches(line)) {
            DATE_THEN_TIME.matchEntire(line)?.let { m ->
                val date = m.groupValues[1].substringAfter(':').trim()
                return listOf("Date: $date", "Time: ${m.groupValues[2]}")
            }
        }
        return listOf(line)
    }

    fun isPending(code: String?): Boolean = code?.startsWith(PENDING_PREFIX, ignoreCase = true) == true

    /** True when the message is an SGR booking (handled by [BookingConfirmationParser]). */
    fun looksLikeRailBooking(text: String): Boolean = RAIL.containsMatchIn(text)

    fun parse(
        raw: String,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.of("Africa/Nairobi")
    ): TicketImport {
        val text = raw.replace(' ', ' ').trim()
        require(text.isNotBlank()) { "Paste the ticket SMS." }
        val link = URL.find(text)?.value?.trimEnd('.', ')')
        // SMS run label: value pairs together; split them into lines the extractor can read.
        // Only the message's first line can carry a sender label ("Talanta Sports City: …");
        // later "Category: Regular" / "Ticket No: …" are real labels.
        val body = text.replace(URL, " ").trim().replaceFirst(SENDER_PREFIX, "")
        val lines = body
            .split(Regex("""\n|(?<=[.!])\s+|,\s+|;\s+"""))
            .map { it.trim().trimEnd('.') }
            .flatMap(::labelled)
            .filter { it.isNotBlank() && !BOILERPLATE.containsMatchIn(it) }
        val extracted = EventTicketExtractor.extract(TextLine.fromPlainText(lines.joinToString("\n")), today, zone)
        // A match line ("Bayern vs Ajax, Tue 05 Jan …") is the title when there is one.
        val matchLine = lines.firstOrNull { VERSUS.containsMatchIn(it) }
            ?.let { EventTicketExtractor.extract(TextLine.fromPlainText(it), today, zone).title ?: it }
        val fields = if (matchLine != null) extracted.copy(title = matchLine) else extracted
        val reference = fields.orderRef ?: fields.ticketNumber
        val preset = EventPresets.match(text)
        require(reference != null || link != null) {
            "Couldn't find a ticket number or ticket link in that message."
        }
        require(fields.title != null || fields.venue != null || preset != null) {
            "That doesn't look like an event ticket SMS."
        }
        val draft = TicketImport(
            title = fields.title ?: "Event",
            venue = fields.venue,
            startsAtMillis = fields.startsAtMillis,
            seatOrTier = fields.seatLine(),
            barcodeValue = PENDING_PREFIX + (reference ?: link!!),
            orderId = reference,
            notes = (fields.toNotes() ?: "From ticket SMS").withNotesValue(LINK_KEY, link),
            source = TicketSource.MANUAL_PASTE,
            expectsBoardingPass = false,
            hasBoardingPass = true
        )
        return preset?.let { EventPresets.applyTo(draft, it) } ?: draft
    }
}
