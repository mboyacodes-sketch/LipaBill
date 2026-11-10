package com.lipabill.app.data.tickets.smart

import org.junit.Assert.assertEquals
import org.junit.Test

class PasteRouteTest {

    @Test
    fun sgr_booking_sms_skips_the_review_screen() {
        val sms = "Booking Confirmed. Ref No: KRCX1Y2Z3. Train: Nairobi to Mombasa E2. " +
            "Date: 20/11/2026. Time: 08:00 AM. Seat: Coach 5, Seat 42. Amount Paid: KES 1,500."
        assertEquals(PasteRoute.SGR_CONFIRMATION, pasteRoute(sms))
    }

    @Test
    fun madaraka_wording_also_counts() {
        assertEquals(
            PasteRoute.SGR_CONFIRMATION,
            pasteRoute("Madaraka Express booking confirmation. Ref No: AB12CD34. Coach 3 Seat 18.")
        )
    }

    @Test
    fun airline_and_event_messages_go_to_review() {
        assertEquals(PasteRoute.REVIEW, pasteRoute("Your Kenya Airways booking KQ 612 NBO-MBA. PNR ABC123. Seat 14A."))
        assertEquals(PasteRoute.REVIEW, pasteRoute("Sauti Sol Live, KICC, Sat 21 Nov. Order ORD9."))
        assertEquals(PasteRoute.REVIEW, pasteRoute(""))
    }
}
