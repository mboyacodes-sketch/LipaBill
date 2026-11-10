package com.lipabill.app.data.tickets.smart

import com.lipabill.app.data.model.TicketBarcodeFormat
import com.lipabill.app.data.model.TicketSource
import com.lipabill.app.data.tickets.TicketImport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class PassDraftTest {

    private val zone = ZoneOffset.UTC

    private fun airline() = PassDraft(
        kind = PassKind.AIRLINE,
        fields = mapOf(
            PassField.HOLDER to DraftField("Amina Otieno", FieldSource.BARCODE),
            PassField.ORIGIN to DraftField("NBO", FieldSource.BARCODE),
            PassField.DESTINATION to DraftField("MBA", FieldSource.BARCODE),
            PassField.FLIGHT to DraftField("KQ 612", FieldSource.BARCODE),
            PassField.DATE to DraftField("2026-11-16", FieldSource.BARCODE),
            PassField.TIME to DraftField("07:40", FieldSource.AI),
            PassField.SEAT to DraftField("14A", FieldSource.BARCODE),
            PassField.CABIN to DraftField("Economy", FieldSource.BARCODE),
            PassField.BOOKING_REF to DraftField("PNR123", FieldSource.BARCODE)
        ),
        barcodeValue = "M1OTIENO/AMINA",
        barcodeFormat = TicketBarcodeFormat.PDF_417,
        source = TicketSource.MANUAL_SCAN
    )

    @Test
    fun airline_draft_becomes_a_one_shot_pass() {
        val import = airline().toTicketImport(zone)!!
        assertEquals("NBO → MBA · KQ 612", import.title)
        assertEquals("NBO → MBA", import.venue)
        assertEquals("Seat 14A · Economy", import.seatOrTier)
        assertEquals("PNR123", import.orderId)
        assertEquals(TicketBarcodeFormat.PDF_417, import.barcodeFormat)
        assertEquals(TicketSource.MANUAL_SCAN, import.source)
        assertFalse(import.expectsBoardingPass)
        assertTrue(import.notes!!.contains("Passenger: Amina Otieno"))
        assertTrue(import.notes!!.contains("Flight: KQ 612"))
        assertEquals(
            LocalDate.of(2026, 11, 16).atTime(7, 40).toInstant(zone).toEpochMilli(),
            import.startsAtMillis
        )
    }

    @Test
    fun a_date_without_a_time_starts_at_midnight() {
        val import = airline().withUserValue(PassField.TIME, "").toTicketImport(zone)!!
        assertEquals(LocalDate.of(2026, 11, 16).atStartOfDay().toInstant(zone).toEpochMilli(), import.startsAtMillis)
    }

    @Test
    fun parser_imports_are_kept_and_only_edits_override() {
        val base = TicketImport(
            title = "SGR E2",
            venue = "Nairobi → Mombasa",
            barcodeValue = "REF:KRCX1Y2Z3",
            orderId = "KRCX1Y2Z3",
            notes = "Booking confirmation — add boarding pass when you have it",
            source = TicketSource.BOOKING_CONFIRMATION,
            expectsBoardingPass = true,
            hasBoardingPass = false
        )
        val draft = PassDraft(
            kind = PassKind.RAIL,
            fields = mapOf(PassField.SEAT to DraftField("Coach 5 · Seat 42", FieldSource.TEXT)),
            barcodeValue = base.barcodeValue,
            source = base.source,
            base = base
        )
        val import = draft.toTicketImport(zone)!!
        assertEquals("SGR E2", import.title)
        assertTrue(import.expectsBoardingPass)
        assertFalse(import.hasBoardingPass)
        assertEquals("Coach 5 · Seat 42", import.seatOrTier)
        assertEquals(base.notes, import.notes)

        val renamed = draft.withUserValue(PassField.TITLE, "Mombasa trip").toTicketImport(zone)!!
        assertEquals("Mombasa trip", renamed.title)
    }

    @Test
    fun edits_are_marked_as_yours_and_blank_removes_a_field() {
        val edited = airline().withUserValue(PassField.SEAT, " 15B ")
        assertEquals(DraftField("15B", FieldSource.USER), edited.fields[PassField.SEAT])
        assertNull(edited.withUserValue(PassField.SEAT, "  ")[PassField.SEAT])
    }

    @Test
    fun no_code_means_no_import() {
        assertNull(airline().copy(barcodeValue = null).toTicketImport(zone))
        assertNull(airline().copy(barcodeValue = " ").toTicketImport(zone))
    }

    @Test
    fun titles_come_from_what_is_known() {
        assertEquals("Event ticket", PassDraft(PassKind.EVENT, emptyMap(), "X", source = TicketSource.E_TICKET).displayTitle())
        val rail = PassDraft(
            PassKind.RAIL,
            mapOf(
                PassField.ORIGIN to DraftField("Nairobi", FieldSource.TEXT),
                PassField.DESTINATION to DraftField("Mombasa", FieldSource.TEXT)
            ),
            "X",
            source = TicketSource.E_TICKET
        )
        assertEquals("SGR Nairobi → Mombasa", rail.displayTitle())
    }

    @Test
    fun counts_fields_by_where_they_came_from() {
        val counts = airline().countBySource()
        assertEquals(8, counts[FieldSource.BARCODE])
        assertEquals(1, counts[FieldSource.AI])
    }

    @Test
    fun draft_dates_and_times_round_trip() {
        assertEquals(LocalDate.of(2026, 1, 2), parseDraftDate(formatDraftDate(LocalDate.of(2026, 1, 2))))
        assertNull(parseDraftDate("2026-13-01"))
        assertNull(parseDraftTime("7:4"))
        assertEquals("07:40", parseDraftTime("07:40")?.let(::formatDraftTime))
    }
}
