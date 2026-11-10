package com.lipabill.app.data.tickets.smart

import java.util.Locale

/**
 * Prompt and answer handling for the on-device model. The model only fills gaps the
 * rules left, answers in plain `KEY: value` lines, and any value that is not in the
 * ticket text (dates and times aside, which it may reformat) is thrown away.
 */
object PassAiPrompt {

    /** Small models lose the thread on long inputs; tickets fit well inside this. */
    internal const val MAX_TEXT = 2_500
    private const val MAX_VALUE = 60

    /** Values that must appear in the ticket text word for word (spaces ignored). */
    private val VERBATIM = setOf(
        PassField.BOOKING_REF,
        PassField.SEAT,
        PassField.FLIGHT,
        PassField.ORIGIN,
        PassField.DESTINATION,
        PassField.HOLDER,
        PassField.VENUE,
        PassField.TITLE
    )

    private val NO_ANSWER = setOf("?", "unknown", "none", "n/a", "na", "null", "-", "not found")

    private val HINTS = mapOf(
        PassField.TITLE to "event or trip name",
        PassField.HOLDER to "passenger or ticket holder name",
        PassField.ORIGIN to "departure airport code or station",
        PassField.DESTINATION to "arrival airport code or station",
        PassField.FLIGHT to "airline code and flight number, like KQ 100",
        PassField.VENUE to "venue name",
        PassField.DATE to "date as YYYY-MM-DD",
        PassField.TIME to "departure or start time as HH:MM, 24-hour",
        PassField.SEAT to "seat, like 12C or Coach 5 Seat 42",
        PassField.CABIN to "class, like Economy",
        PassField.BOOKING_REF to "booking reference or PNR"
    )

    fun build(ticketText: String, kind: PassKind, missing: Collection<PassField>): String {
        val text = ticketText.replace(Regex("\\s+"), " ").trim().take(MAX_TEXT)
        val keys = missing.joinToString("\n") { "${it.name}: ${HINTS[it]}" }
        return """
            You read ${kind.label.lowercase(Locale.US)}s. From the ticket text below, answer only these keys.
            $keys

            Rules:
            - One line per key, written as KEY: value.
            - Copy values exactly as they appear in the text.
            - If a value is not in the text, write KEY: ?
            - Do not add any other words.

            Ticket text:
            $text
        """.trimIndent()
    }

    /**
     * Turns the model's answer into fields, keeping only [wanted] keys whose values
     * can be trusted: present in [ticketText], or a valid date / time.
     */
    fun parse(answer: String?, ticketText: String, wanted: Collection<PassField>): Map<PassField, String> {
        if (answer.isNullOrBlank()) return emptyMap()
        val haystack = compact(ticketText)
        val result = linkedMapOf<PassField, String>()
        for (line in answer.lines()) {
            val split = line.indexOfFirst { it == ':' || it == '=' }
            if (split <= 0) continue
            val key = line.substring(0, split).trim().trimStart('-', '*', ' ').uppercase(Locale.US).replace(' ', '_')
            val field = PassField.entries.firstOrNull { it.name == key } ?: continue
            if (field !in wanted || field in result) continue
            val value = line.substring(split + 1).trim().trim('"', '\'', '`', '.', ',').trim()
            if (value.isEmpty() || value.lowercase(Locale.US) in NO_ANSWER || value.length > MAX_VALUE) continue
            val trusted = when (field) {
                PassField.DATE -> parseDraftDate(value) != null
                PassField.TIME -> parseDraftTime(value) != null
                PassField.CABIN -> true
                in VERBATIM -> haystack.contains(compact(value))
                else -> false
            }
            if (trusted) result[field] = value
        }
        return result
    }

    private fun compact(text: String): String = text.lowercase(Locale.US).replace(Regex("[\\s/·.,-]+"), "")
}
