package com.lipabill.app.data.tickets

import com.lipabill.app.data.model.TicketBarcodeFormat
import com.lipabill.app.data.model.TicketSource
import com.lipabill.app.data.model.firstNotesValue
import com.lipabill.app.data.model.notesValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupTicketsTest {

    private fun qr(value: String) = ETicketTextParser.FoundBarcode(value, TicketBarcodeFormat.QR_CODE)

    private val main = TicketImport(
        title = "Sample Fest",
        venue = "Sample Grounds",
        startsAtMillis = 1_800_000_000_000L,
        seatOrTier = "Row B · Seat 12",
        barcodeValue = "GATE-AAA111",
        barcodeFormat = TicketBarcodeFormat.QR_CODE,
        orderId = "ORD-555",
        notes = "Tier: VIP · Guest: Sample Person · Seat: 12",
        source = TicketSource.E_TICKET
    )

    @Test
    fun extra_codes_keep_same_kind_and_drop_app_links() {
        val read = listOf(
            qr("GATE-AAA111"),
            qr("GATE-BBB222"),
            qr("GATE-BBB222"),
            qr("https://example.com/get-the-app"),
            ETicketTextParser.FoundBarcode("123456789012", TicketBarcodeFormat.CODE_128),
            qr("GATE-CCC333")
        )
        val extras = ETicketTextParser.extraTicketCodes(read, chosen = "GATE-AAA111")
        assertEquals(listOf("GATE-BBB222", "GATE-CCC333"), extras.map { it.value })
    }

    @Test
    fun printed_reference_has_no_extra_tickets() {
        val read = listOf(ETicketTextParser.FoundBarcode("REF12345", TicketBarcodeFormat.TEXT), qr("GATE-BBB222"))
        assertTrue(ETicketTextParser.extraTicketCodes(read, chosen = "REF12345").isEmpty())
    }

    @Test
    fun group_drafts_share_the_event_and_number_each_ticket() {
        val drafts = GroupTickets.drafts(main, listOf(qr("GATE-BBB222"), qr("GATE-CCC333")))
        assertEquals(3, drafts.size)
        assertEquals(listOf("GATE-AAA111", "GATE-BBB222", "GATE-CCC333"), drafts.map { it.barcodeValue })
        assertEquals(listOf("1 of 3", "2 of 3", "3 of 3"), drafts.map { it.notes.notesValue("Ticket") })
        drafts.forEach {
            assertEquals("Sample Fest", it.title)
            assertEquals(main.startsAtMillis, it.startsAtMillis)
            assertEquals("ORD-555", it.orderId)
            assertEquals("VIP", it.notes.notesValue("Tier"))
        }
    }

    @Test
    fun seat_and_guest_stay_on_the_first_ticket_only() {
        val (first, second) = GroupTickets.drafts(main, listOf(qr("GATE-BBB222")))
        assertEquals("Row B · Seat 12", first.seatOrTier)
        assertEquals("Sample Person", first.notes.notesValue("Guest"))
        assertNull(second.seatOrTier)
        assertNull(second.notes.firstNotesValue("Guest", "Seat"))
    }

    @Test
    fun no_extras_returns_the_draft_unchanged() {
        assertEquals(listOf(main), GroupTickets.drafts(main, emptyList()))
    }
}
