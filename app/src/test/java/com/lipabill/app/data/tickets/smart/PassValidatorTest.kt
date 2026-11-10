package com.lipabill.app.data.tickets.smart

import com.lipabill.app.data.model.TicketSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PassValidatorTest {

    private val today = LocalDate.of(2026, 11, 10)

    @Test
    fun airport_codes_must_be_three_letters_on_airline_passes() {
        assertNull(PassValidator.problemWith(PassField.ORIGIN, "NBO", PassKind.AIRLINE, today))
        assertNotNull(PassValidator.problemWith(PassField.ORIGIN, "Nairobi", PassKind.AIRLINE, today))
        // Train stations are names, not codes.
        assertNull(PassValidator.problemWith(PassField.ORIGIN, "Nairobi Terminus", PassKind.RAIL, today))
    }

    @Test
    fun flight_numbers_are_checked() {
        assertNull(PassValidator.problemWith(PassField.FLIGHT, "KQ 100", PassKind.AIRLINE, today))
        assertNull(PassValidator.problemWith(PassField.FLIGHT, "ek722", PassKind.AIRLINE, today))
        assertNotNull(PassValidator.problemWith(PassField.FLIGHT, "Kenya Airways", PassKind.AIRLINE, today))
    }

    @Test
    fun dates_in_the_past_or_far_future_are_flagged() {
        assertNull(PassValidator.problemWith(PassField.DATE, "2026-11-20", PassKind.EVENT, today))
        assertNull(PassValidator.problemWith(PassField.DATE, "2026-11-09", PassKind.EVENT, today))
        assertEquals("This date has passed.", PassValidator.problemWith(PassField.DATE, "2026-10-01", PassKind.EVENT, today))
        assertNotNull(PassValidator.problemWith(PassField.DATE, "2028-06-01", PassKind.EVENT, today))
        assertNotNull(PassValidator.problemWith(PassField.DATE, "20/11/2026", PassKind.EVENT, today))
    }

    @Test
    fun times_must_be_24_hour() {
        assertNull(PassValidator.problemWith(PassField.TIME, "08:00", PassKind.RAIL, today))
        assertNotNull(PassValidator.problemWith(PassField.TIME, "8am", PassKind.RAIL, today))
    }

    @Test
    fun ai_values_always_carry_a_check_it_note() {
        val draft = draft(PassField.SEAT to DraftField("12C", FieldSource.AI))
        val checked = PassValidator.validate(draft, today, null)
        assertEquals("Read by on-device AI. Check it.", checked.fields.getValue(PassField.SEAT).warning)
    }

    @Test
    fun a_real_problem_wins_over_the_ai_note() {
        val draft = draft(PassField.DATE to DraftField("2026-01-01", FieldSource.AI))
        val checked = PassValidator.validate(draft, today, null)
        assertEquals("This date has passed.", checked.fields.getValue(PassField.DATE).warning)
    }

    @Test
    fun values_from_the_barcode_have_no_warning() {
        val draft = draft(PassField.ORIGIN to DraftField("NBO", FieldSource.BARCODE))
        assertNull(PassValidator.validate(draft, today, null).fields.getValue(PassField.ORIGIN).warning)
    }

    @Test
    fun no_barcode_blocks_saving() {
        val checked = PassValidator.validate(draft().copy(barcodeValue = null), today, null)
        assertFalse(checked.canSave)
        assertEquals(1, checked.blockers.size)
        assertTrue(PassValidator.validate(draft(), today, null).canSave)
    }

    @Test
    fun names_match_in_any_order_case_or_title() {
        assertEquals(NameCheck.MATCH, PassValidator.nameCheck("John Kamau", "KAMAU/JOHN MR"))
        assertEquals(NameCheck.MATCH, PassValidator.nameCheck("Mr. John Kamau Mwangi", "john kamau"))
        assertEquals(NameCheck.MATCH, PassValidator.nameCheck("Amina", "Amina Otieno"))
    }

    @Test
    fun accents_do_not_break_a_match() {
        assertEquals(NameCheck.MATCH, PassValidator.nameCheck("José Ndegwa", "JOSE NDEGWA"))
    }

    @Test
    fun a_different_person_is_a_mismatch() {
        assertEquals(NameCheck.MISMATCH, PassValidator.nameCheck("John Otieno", "John Kamau"))
        assertEquals(NameCheck.MISMATCH, PassValidator.nameCheck("Grace Wanjiku", "Kevin Otieno"))
    }

    @Test
    fun missing_names_are_unknown() {
        assertEquals(NameCheck.UNKNOWN, PassValidator.nameCheck(null, "John Kamau"))
        assertEquals(NameCheck.UNKNOWN, PassValidator.nameCheck("John Kamau", "  "))
        assertEquals(NameCheck.UNKNOWN, PassValidator.nameCheck("Mr", "Dr"))
    }

    @Test
    fun validate_sets_the_name_check() {
        val draft = draft(PassField.HOLDER to DraftField("John Kamau", FieldSource.BARCODE))
        assertEquals(NameCheck.MATCH, PassValidator.validate(draft, today, "Kamau John").nameCheck)
        assertEquals(NameCheck.UNKNOWN, PassValidator.validate(draft, today, null).nameCheck)
    }

    private fun draft(vararg fields: Pair<PassField, DraftField>) = PassDraft(
        kind = PassKind.AIRLINE,
        fields = fields.toMap(),
        barcodeValue = "CODE",
        source = TicketSource.E_TICKET
    )
}
