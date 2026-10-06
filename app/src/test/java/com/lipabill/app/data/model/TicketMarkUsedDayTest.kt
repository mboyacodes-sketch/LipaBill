package com.lipabill.app.data.model

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TicketMarkUsedDayTest {

    private val nairobi = ZoneId.of("Africa/Nairobi")

    private fun at(year: Int, month: Int, day: Int, hour: Int): Long =
        ZonedDateTime.of(year, month, day, hour, 0, 0, 0, nairobi)
            .toInstant()
            .toEpochMilli()

    private fun ticket(startsAtMillis: Long?) = Ticket(
        title = "Blankets & Wine",
        barcodeValue = "QR",
        startsAtMillis = startsAtMillis
    )

    @Test
    fun hidden_before_event_day() {
        val event = at(2026, 10, 10, 20)
        assertFalse(ticket(event).canMarkUsedFromEventDay(at(2026, 10, 9, 23), nairobi))
    }

    @Test
    fun shown_on_event_day_before_start_time() {
        val event = at(2026, 10, 10, 20)
        assertTrue(ticket(event).canMarkUsedFromEventDay(at(2026, 10, 10, 8), nairobi))
    }

    @Test
    fun shown_after_event_day() {
        val event = at(2026, 10, 10, 20)
        assertTrue(ticket(event).canMarkUsedFromEventDay(at(2026, 10, 11, 9), nairobi))
    }

    @Test
    fun shown_when_undated() {
        assertTrue(ticket(null).canMarkUsedFromEventDay(at(2026, 10, 10, 8), nairobi))
    }
}
