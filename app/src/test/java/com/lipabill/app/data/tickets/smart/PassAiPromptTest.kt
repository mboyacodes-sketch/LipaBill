package com.lipabill.app.data.tickets.smart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PassAiPromptTest {

    private val ticket = """
        KENYA AIRWAYS  BOARDING PASS
        Passenger: OTIENO/AMINA MS
        Flight KQ 612   From NBO   To MBA
        Booking ref PNR123   Seat 14A   Gate 7
        Boarding 07:40
    """.trimIndent()

    private val airlineWanted = PassField.wantedFor(PassKind.AIRLINE)

    @Test
    fun prompt_asks_only_for_missing_keys() {
        val prompt = PassAiPrompt.build(ticket, PassKind.AIRLINE, listOf(PassField.TIME, PassField.SEAT))
        assertTrue(prompt.contains("TIME:"))
        assertTrue(prompt.contains("SEAT:"))
        assertFalse(prompt.contains("BOOKING_REF:"))
        assertTrue(prompt.contains("KQ 612"))
    }

    @Test
    fun prompt_trims_very_long_text() {
        val prompt = PassAiPrompt.build("x".repeat(10_000), PassKind.EVENT, listOf(PassField.TITLE))
        assertTrue(prompt.length < PassAiPrompt.MAX_TEXT + 1_000)
    }

    @Test
    fun parses_colon_and_equals_lines() {
        val found = PassAiPrompt.parse("SEAT: 14A\nTIME=07:40", ticket, airlineWanted)
        assertEquals(mapOf(PassField.SEAT to "14A", PassField.TIME to "07:40"), found)
    }

    @Test
    fun tolerates_bullets_quotes_and_case() {
        val found = PassAiPrompt.parse("- seat: \"14A\"\n* Booking_Ref: pnr123.", ticket, airlineWanted)
        assertEquals("14A", found[PassField.SEAT])
        assertEquals("pnr123", found[PassField.BOOKING_REF])
    }

    @Test
    fun question_marks_and_unknowns_are_skipped() {
        val found = PassAiPrompt.parse("SEAT: ?\nTIME: unknown\nCABIN: N/A", ticket, airlineWanted)
        assertTrue(found.isEmpty())
    }

    @Test
    fun values_not_in_the_ticket_are_thrown_away() {
        // The model invents a booking ref and a seat that are not printed anywhere.
        val found = PassAiPrompt.parse("BOOKING_REF: ZZZ999\nSEAT: 31F\nFLIGHT: KQ 612", ticket, airlineWanted)
        assertEquals(mapOf(PassField.FLIGHT to "KQ 612"), found)
    }

    @Test
    fun spacing_and_punctuation_do_not_hide_a_real_value() {
        val text = "Flight KQ 612 · Passenger OTIENO/AMINA"
        val found = PassAiPrompt.parse("FLIGHT: KQ612\nHOLDER: Otieno Amina", text, airlineWanted)
        assertEquals("KQ612", found[PassField.FLIGHT])
        assertEquals("Otieno Amina", found[PassField.HOLDER])
        // Reordered, the name is no longer copied from the text, so it is dropped.
        assertFalse(PassAiPrompt.parse("HOLDER: Amina Otieno", text, airlineWanted).containsKey(PassField.HOLDER))
    }

    @Test
    fun dates_and_times_must_be_valid_but_may_be_reformatted() {
        val found = PassAiPrompt.parse("DATE: 2026-11-16\nTIME: 25:99", ticket, airlineWanted)
        assertEquals(mapOf(PassField.DATE to "2026-11-16"), found)
        assertTrue(PassAiPrompt.parse("DATE: 16th Nov", ticket, airlineWanted).isEmpty())
    }

    @Test
    fun keys_that_were_not_asked_for_are_ignored() {
        val found = PassAiPrompt.parse("SEAT: 14A\nVENUE: Gate 7", ticket, listOf(PassField.SEAT))
        assertEquals(setOf(PassField.SEAT), found.keys)
    }

    @Test
    fun first_answer_for_a_key_wins() {
        val found = PassAiPrompt.parse("SEAT: 14A\nSEAT: 7", ticket, airlineWanted)
        assertEquals("14A", found[PassField.SEAT])
    }

    @Test
    fun rambling_or_empty_answers_give_nothing() {
        assertTrue(PassAiPrompt.parse(null, ticket, airlineWanted).isEmpty())
        assertTrue(PassAiPrompt.parse("", ticket, airlineWanted).isEmpty())
        assertTrue(PassAiPrompt.parse("I think this is a boarding pass for a flight.", ticket, airlineWanted).isEmpty())
        val longValue = "SEAT: " + "A".repeat(80)
        assertTrue(PassAiPrompt.parse(longValue, ticket + "A".repeat(80), airlineWanted).isEmpty())
    }
}
