package com.lipabill.app.data.tickets

import com.lipabill.app.data.model.TicketSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class BookingConfirmationParserTest {

    private val sample = """
        Booking Confirmed. Ref No: KRCX1Y2Z3. Train: Nairobi to Mombasa E2.
        Date: 20/09/2026. Time: 08:00 AM. Seat: Coach 5, Seat 42. Amount Paid: KES 1,500.
        Use Ref No and your phone number to print your ticket at the station kiosk.
    """.trimIndent()

    @Test
    fun looksLikeConfirmation() {
        assertTrue(BookingConfirmationParser.looksLikeConfirmation(sample))
        assertFalse(BookingConfirmationParser.looksLikeConfirmation("Madaraka Express boarding pass QR only"))
    }

    @Test
    fun parsesSgrBookingSms() {
        val draft = BookingConfirmationParser.parse(sample)
        assertEquals("SGR E2", draft.title)
        assertEquals("Nairobi Terminus → Mombasa Terminus", draft.venue)
        assertEquals("KRCX1Y2Z3", draft.orderId)
        assertEquals("REF:KRCX1Y2Z3", draft.barcodeValue)
        assertEquals("Coach 5 · Seat 42", draft.seatOrTier)
        assertEquals(TicketSource.BOOKING_CONFIRMATION, draft.source)
        assertFalse(draft.hasBoardingPass)
        assertTrue(draft.notes!!.contains("Fare: KES 1,500"))

        val cal = Calendar.getInstance().apply { timeInMillis = draft.startsAtMillis!! }
        assertEquals(2026, cal.get(Calendar.YEAR))
        assertEquals(Calendar.SEPTEMBER, cal.get(Calendar.MONTH))
        assertEquals(20, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(8, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMessageWithoutRef() {
        BookingConfirmationParser.parse("Booking Confirmed. Train: Nairobi to Mombasa.")
    }
}
