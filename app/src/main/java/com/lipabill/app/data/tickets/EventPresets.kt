package com.lipabill.app.data.tickets

import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Known upcoming events a QR-only or half-read ticket can borrow details from, so the pass is
 * still useful before a real ticket has taught the extractor its layout. Presets only fill
 * blanks; anything read from the ticket wins.
 *
 * Adapter kit: when the first real ticket for a preset arrives, follow
 * docs/tickets/first-ticket-playbook.md (fixture, expected fields, extractor synonyms).
 */
data class EventPreset(
    val id: String,
    /** Short chip label in the review step. */
    val label: String,
    val title: String,
    val venue: String,
    val startsAt: LocalDateTime,
    val zone: ZoneId,
    /** Words on a ticket (title, venue, any text) that mean it is this event. */
    val hints: List<String>,
    /** Hints that only count together (both teams on one ticket). */
    val pairedHints: List<Pair<String, String>> = emptyList()
) {
    val startsAtMillis: Long get() = startsAt.atZone(zone).toInstant().toEpochMilli()
}

object EventPresets {

    private val nairobi = ZoneId.of("Africa/Nairobi")

    /**
     * Talanta Stadium inauguration. Kick-off 20:15 EAT per the official FAQ (some listings say
     * 19:00): a printed time on a real ticket always replaces this.
     */
    val TALANTA_BAYERN_AJAX = EventPreset(
        id = "talanta-bayern-ajax-2027",
        label = "Bayern vs Ajax · Talanta",
        title = "FC Bayern Munich vs AFC Ajax",
        venue = "Raila Odinga International Stadium, Talanta Sports City, Nairobi",
        startsAt = LocalDateTime.of(2027, 1, 5, 20, 15),
        zone = nairobi,
        hints = listOf("talanta", "raila odinga", "jamhuri grounds"),
        pairedHints = listOf("bayern" to "ajax")
    )

    val all: List<EventPreset> = listOf(TALANTA_BAYERN_AJAX)

    /** Presets whose event has not started yet. */
    fun upcoming(nowMillis: Long = System.currentTimeMillis()): List<EventPreset> =
        all.filter { it.startsAtMillis > nowMillis }

    /** The upcoming preset this ticket's text points to, if any. */
    fun match(vararg text: String?, nowMillis: Long = System.currentTimeMillis()): EventPreset? {
        val hay = text.filterNotNull().joinToString(" ").lowercase()
        if (hay.isBlank()) return null
        return upcoming(nowMillis).firstOrNull { preset ->
            preset.hints.any { hay.contains(it) } ||
                preset.pairedHints.any { (a, b) -> hay.contains(a) && hay.contains(b) }
        }
    }

    /** Fill only what the ticket did not give: a generic title, the venue, the start time. */
    fun applyTo(draft: TicketImport, preset: EventPreset): TicketImport = draft.copy(
        title = if (isGenericTitle(draft.title)) preset.title else draft.title,
        venue = draft.venue?.takeIf { it.isNotBlank() } ?: preset.venue,
        startsAtMillis = draft.startsAtMillis ?: preset.startsAtMillis
    )

    /** Placeholder titles the importers use when nothing better was read. */
    fun isGenericTitle(title: String?): Boolean =
        title.isNullOrBlank() || title == "E-ticket" || title == "Event" || title == "Ticket"
}
