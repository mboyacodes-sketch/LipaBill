package com.lipabill.app.data.tickets

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Layout fixtures modelled on real tickets, with made-up names and codes. */
class EventTicketExtractorTest {

    private val zone = ZoneId.of("Africa/Nairobi")
    private val today = LocalDate.of(2026, 3, 1)
    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int) =
        LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant().toEpochMilli()

    private fun l(text: String, left: Float, top: Float, size: Float, width: Float = text.length * size * 0.5f) =
        TextLine(text, left, top, left + width, top + size, size)

    /** PDF storefront ticket: big title, header line wrapping the venue, `LABEL value` rows. */
    private val storefrontPdf = listOf(
        l("Lakeside Culture Fest", 40f, 40f, 24f),
        l("Sat 14 Nov 2026 · 02:00 PM · Arboretum", 40f, 80f, 12f),
        l("Gardens", 40f, 94f, 12f),
        l("ATTENDEE Jane Doe", 40f, 140f, 10f),
        l("PHONE NUMBER +254700000000", 40f, 156f, 10f),
        l("START DATE Sat 14 Nov 2026", 40f, 172f, 10f),
        l("02:00 PM", 120f, 186f, 10f),
        l("END DATE", 40f, 204f, 10f, 40f),
        l("11:00 PM", 120f, 204f, 10f),
        l("STOREFRONT Tikiti Demo", 40f, 220f, 10f),
        l("TICKET VVIP Offer", 40f, 236f, 10f),
        l("Ticket: 4821", 40f, 280f, 10f),
        l("S90021", 40f, 296f, 10f),
        l("By attending this Event, you acknowledge that photography, filming and", 40f, 330f, 8f),
        l("Entry: QR scan at the gate. Tikiti Demo", 40f, 360f, 8f)
    )

    /** Photo ticket (OCR boxes): labels in columns with values underneath. */
    private val photoTicket = listOf(
        l("SUNSET SESSIONS", 100f, 94f, 118f, 600f),
        l("• 04-05 Apr 2026", 92f, 326f, 101f, 250f),
        l("Time:", 375f, 347f, 41f, 70f),
        l("Amount Paid:", 575f, 347f, 41f, 160f),
        l("2:00 PM - 2:00 AM KES 4,500|2 tick", 375f, 406f, 76f, 560f),
        l("Ticket Type:", 100f, 565f, 53f, 160f),
        l("Location:", 375f, 570f, 40f, 120f),
        l("Long Weekend Pass -", 100f, 641f, 41f, 250f),
        l("Lakeview Grounds", 375f, 641f, 47f, 360f),
        l("Access to Sunset", 100f, 712f, 41f, 250f),
        l("Paid. Lipa", 800f, 763f, 75f, 150f),
        l("Hangout on Saturday and", 100f, 782f, 35f, 250f),
        l("TICKET: TKT00DEMO7QX", 372f, 846f, 65f, 380f),
        l("ZAWADI ATIENO", 125f, 853f, 41f, 200f),
        l("(Sunday)", 95f, 923f, 54f, 120f)
    )

    @Test
    fun storefrontPdf() {
        val f = EventTicketExtractor.extract(storefrontPdf, today, zone)
        assertEquals("Lakeside Culture Fest", f.title)
        assertEquals("Arboretum Gardens", f.venue)
        assertEquals(at(2026, 11, 14, 14, 0), f.startsAtMillis)
        assertEquals(at(2026, 11, 14, 23, 0), f.endsAtMillis)
        assertEquals("Jane Doe", f.attendee)
        assertEquals("VVIP Offer", f.tier)
        assertEquals("4821", f.ticketNumber)
        assertEquals("Tikiti Demo", f.organizer)
        assertNull("a sentence is not a gate", f.gate)
        assertFalse("phone numbers are never kept", f.toNotes().orEmpty().contains("254700000000"))
    }

    /** Exact geometry PDFBox reports on the phone for a storefront ticket (names replaced). */
    private val storefrontPdfIndented = listOf(
        l("Lakeside Culture Fest", 29.4f, 8.9f, 25f, 120f),
        l("Sat 14 Nov 2026 · 02:00 PM · Arboretum", 29.4f, 32.9f, 13f, 230f),
        l("Gardens", 29.4f, 43.1f, 13f, 50f),
        l("ATTENDEE Jane Doe", 29.4f, 97.1f, 13f, 120f),
        l("PHONE NUMBER +254700000000", 29.4f, 116.3f, 13f, 190f),
        l("START DATE Sat 14 Nov 2026", 29.4f, 135.5f, 13f, 180f),
        l("02:00 PM", 84.6f, 146.9f, 13f, 55f),
        TextLine("END DATE", 29.4f, 166.4f, 72f, 173.4f, 10f),
        l("11:00 PM", 84.6f, 177.5f, 13f, 55f),
        l("STOREFRONT Tikiti Demo", 29.4f, 196.7f, 13f, 160f),
        l("TICKET VVIP Offer", 29.4f, 215.9f, 13f, 120f),
        l("Ticket: 4821", 86.4f, 376.2f, 24f, 140f),
        l("S90021", 114.6f, 404.3f, 13f, 50f),
        l("Entry: QR scan at the gate. Tikiti Demo", 85.8f, 480.6f, 12f, 240f)
    )

    @Test
    fun storefrontPdfIndentedValueColumn() {
        val f = EventTicketExtractor.extract(storefrontPdfIndented, today, zone)
        assertEquals("Lakeside Culture Fest", f.title)
        assertEquals("Arboretum Gardens", f.venue)
        assertEquals(at(2026, 11, 14, 14, 0), f.startsAtMillis)
        assertEquals("value indented under END DATE", at(2026, 11, 14, 23, 0), f.endsAtMillis)
        assertEquals("VVIP Offer", f.tier)
        assertEquals("4821", f.ticketNumber)
        assertEquals("Tikiti Demo", f.organizer)
        assertEquals("Jane Doe", f.attendee)
    }

    @Test
    fun photoTicketWithColumns() {
        val f = EventTicketExtractor.extract(photoTicket, today, zone)
        assertEquals("Sunset Sessions", f.title)
        assertEquals("Lakeview Grounds", f.venue)
        assertEquals(at(2026, 4, 4, 14, 0), f.startsAtMillis)
        assertEquals("ends after midnight on the second day", at(2026, 4, 5, 2, 0), f.endsAtMillis)
        assertEquals("Long Weekend Pass", f.tier)
        assertEquals("KES 4,500", f.price)
        assertEquals(2, f.admits)
        assertEquals("TKT00DEMO7QX", f.ticketNumber)
        assertEquals("Zawadi Atieno", f.attendee)
    }

    @Test
    fun plainTextStillWorks() {
        val text = """
            ORDER CONFIRMATION
            Blankets & Wine
            Date: 7 Dec 2026
            Time: 1pm
            Venue: Laureate Gardens, Nairobi
            Ticket Type: Early Bird
            Name: John Otieno
            Order #: BW-99812
            Total: Ksh 3,500
            Doors: 12:00 PM
            Gate: B
            Row: 4  
            Seat: 12
        """.trimIndent()
        val f = EventTicketExtractor.extract(TextLine.fromPlainText(text), today, zone)
        assertEquals("Blankets & Wine", f.title)
        assertEquals("Laureate Gardens, Nairobi", f.venue)
        assertEquals(at(2026, 12, 7, 13, 0), f.startsAtMillis)
        assertEquals("Early Bird", f.tier)
        assertEquals("John Otieno", f.attendee)
        assertEquals("BW-99812", f.orderRef)
        assertEquals("KES 3,500", f.price)
        assertEquals("12:00 PM", f.doors)
        assertEquals("Gate B · Row 4 · Seat 12", f.seatLine())
    }

    @Test
    fun dateFormats() {
        fun d(s: String) = EventTicketExtractor.findDate(s, today)?.start
        assertEquals(LocalDate.of(2026, 11, 21), d("Sat, Nov 21, 2026"))
        assertEquals(LocalDate.of(2026, 11, 21), d("21st November 2026"))
        assertEquals(LocalDate.of(2026, 11, 21), d("2026-11-21T18:30"))
        assertEquals(LocalDate.of(2026, 11, 21), d("21/11/2026"))
        assertEquals("no year: upcoming date stays this year", LocalDate.of(2026, 5, 2), d("Sat 2 May"))
        assertEquals("no year, recent: this year", LocalDate.of(2026, 1, 10), d("10 Jan"))
        assertEquals(
            "no year, long past: next year",
            LocalDate.of(2027, 1, 10),
            EventTicketExtractor.findDate("10 Jan", LocalDate.of(2026, 6, 1))?.start
        )
        assertEquals(listOf(19 * 60 + 30), EventTicketExtractor.findTimes("7.30pm").map { it.hour * 60 + it.minute })
    }

    @Test
    fun labelsNeedAColonOrCaps() {
        assertNull("a title is not a time label", EventTicketExtractor.matchLabel("Timeless Jazz Night"))
        assertNull(EventTicketExtractor.matchLabel("Time Out Festival"))
        assertEquals(EventTicketExtractor.Field.ATTENDEE, EventTicketExtractor.matchLabel("ATTENDEE Jane")?.first)
        assertEquals(EventTicketExtractor.Field.TIER, EventTicketExtractor.matchLabel("Ticket Type: VIP")?.first)
    }

    @Test
    fun flightItinerariesAreRefused() {
        val flight = """
            Itinerary
            Booking Reference: ABC123
            Passenger Contact Information Flight Ticket
            DEPARTING:
            25 Sep 2026 5H0417 Nairobi Int [JKIA] 06:30 Mombasa 07:40 Economy Confirmed
            Baggage allowance per person is 20kg. Check-in closes 30 mins before departure.
            Airport check-in information
        """.trimIndent()
        assertTrue(EventTicketExtractor.looksLikeFlight(flight))
        try {
            ETicketTextParser.parse(flight, emptyList(), kind = TicketDocumentKind.EVENT)
            fail("flight should be refused")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("flight", ignoreCase = true))
        }
        assertFalse(
            "SGR wording alone is not a flight",
            EventTicketExtractor.looksLikeFlight("Madaraka Express Departure 08:00 Arrival 13:30 Boarding pass")
        )
    }

    @Test
    fun notesFeedThePass() {
        val f = EventTicketExtractor.extract(photoTicket, today, zone)
        val notes = f.toNotes()!!
        assertTrue(notes.contains("Guest: Zawadi Atieno"))
        assertTrue(notes.contains("Tier: Long Weekend Pass"))
        assertTrue(notes.contains("Price: KES 4,500"))
        assertTrue(notes.contains("Admits: 2"))
        assertTrue(notes.contains("Ends: "))
    }
}
