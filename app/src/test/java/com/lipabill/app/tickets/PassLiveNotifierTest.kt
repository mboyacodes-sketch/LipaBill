package com.lipabill.app.tickets

import com.lipabill.app.data.model.Ticket
import com.lipabill.app.data.model.TicketSource
import com.lipabill.app.data.model.TicketStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class PassLiveNotifierTest {

    private val zone = ZoneOffset.UTC
    // 2026-11-20 12:00 UTC
    private val now = 1_795_161_600_000L

    @Test
    fun copy_before_start_says_pass_today() {
        val ticket = ticket(startsAt = now + 3_600_000L, title = "Tonight Check", venue = "Today Hall")
        val (title, body) = PassLiveNotifier.copy(ticket, count = 1, time = "11:30 PM", nowMillis = now)
        assertEquals("Pass today", title)
        assertTrue(body.contains("Tonight Check"))
        assertTrue(body.contains("Today Hall"))
    }

    @Test
    fun copy_after_start_says_show_your_pass_and_counts_extras() {
        val ticket = ticket(startsAt = now - 60_000L, title = "Tonight Check")
        val (title, body) = PassLiveNotifier.copy(ticket, count = 3, time = "11:30 PM", nowMillis = now)
        assertEquals("Show your pass", title)
        assertTrue(body.contains("+2 more"))
    }

    @Test
    fun isOnDay_filters_to_calendar_day() {
        val todayStart = now + 2L * 3_600_000L
        val tomorrow = now + 30L * 3_600_000L
        val on = ticket(startsAt = todayStart)
        val off = ticket(startsAt = tomorrow, id = 2)
        val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        assertTrue(on.isOnDay(zone, today))
        assertTrue(!off.isOnDay(zone, today))
        assertTrue(on.effectiveStatus(now) == TicketStatus.ACTIVE)
    }

    private fun ticket(
        id: Long = 1,
        startsAt: Long,
        title: String = "Sample",
        venue: String? = null,
        status: TicketStatus = TicketStatus.ACTIVE
    ) = Ticket(
        id = id,
        title = title,
        venue = venue,
        startsAtMillis = startsAt,
        barcodeValue = "CODE-$id",
        source = TicketSource.E_TICKET,
        status = status
    )
}
