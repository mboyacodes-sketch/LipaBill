package com.lipabill.app.tickets

import com.lipabill.app.data.model.Ticket
import com.lipabill.app.data.model.TicketSource
import com.lipabill.app.data.model.TicketStatus
import com.lipabill.app.data.tickets.EventTicketSms
import com.lipabill.app.data.tickets.intelligence.TodayStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/** Sample passes only. Times in Nairobi so stages and midnight are fixed. */
class PassLivePlannerTest {

    private val zone = ZoneId.of("Africa/Nairobi")
    private fun at(h: Int, m: Int = 0, day: Int = 14) =
        LocalDateTime.of(2026, 12, day, h, m).atZone(zone).toInstant().toEpochMilli()

    private fun pass(
        id: Long = 1,
        startsAt: Long? = at(19, 30),
        venue: String? = "Sample Grounds",
        code: String = "SAMPLE-CODE-$id",
        seat: String? = "A12",
        orderId: String? = "SYNTH-0001",
        boarding: Boolean = false,
        status: TicketStatus = TicketStatus.ACTIVE
    ) = Ticket(
        id = id,
        title = "Sample Festival",
        venue = venue,
        startsAtMillis = startsAt,
        seatOrTier = seat,
        orderId = orderId,
        barcodeValue = code,
        source = TicketSource.E_TICKET,
        expectsBoardingPass = boarding,
        hasBoardingPass = !boarding,
        status = status
    )

    private fun plan(now: Long, vararg tickets: Ticket) =
        PassLivePlanner.plan(tickets.toList(), now, zone)

    @Test
    fun nothing_today_means_no_notification() {
        assertNull(plan(at(12)))
        assertNull(plan(at(12), pass(startsAt = at(19, day = 15))))
        assertNull(plan(at(12), pass(status = TicketStatus.USED)))
    }

    @Test
    fun later_today_shows_the_start_in_the_chip_with_show_code_and_directions() {
        val p = plan(at(15), pass())!!
        assertEquals(TodayStage.LATER, p.stage)
        assertEquals("Sample Festival", p.title)
        assertEquals("Tonight at 7:30 PM, Sample Grounds", p.body)
        assertEquals("7:30 PM", p.chip)
        assertNull(p.countdownTo)
        assertEquals(listOf(LiveAction.SHOW_CODE, LiveAction.DIRECTIONS), p.actions)
        // Before the 3 h lead-in the marker sits at the start of the bar.
        assertEquals(0, p.progress.value)
    }

    @Test
    fun within_the_hour_the_chip_counts_down_and_the_marker_nears_the_start() {
        val p = plan(at(19, 0), pass())!!
        assertEquals(TodayStage.SOON, p.stage)
        assertEquals("Sample Festival starts soon", p.title)
        assertNull(p.chip)
        assertEquals(at(19, 30), p.countdownTo)
        assertEquals(150, p.progress.value) // 2.5 h into the 3 h lead-in
        assertEquals(p.progress.leadMinutes + 240, p.progress.max)
    }

    @Test
    fun during_the_event_it_says_now_and_offers_mark_used() {
        val p = plan(at(20, 30), pass())!!
        assertEquals(TodayStage.LIVE, p.stage)
        assertEquals("Sample Festival is on", p.title)
        assertEquals("Until 11:30 PM, Sample Grounds", p.body)
        assertEquals("Now", p.chip)
        assertEquals(listOf(LiveAction.SHOW_CODE, LiveAction.MARK_USED), p.actions)
    }

    @Test
    fun finished_asks_to_mark_used_with_a_full_bar() {
        val p = plan(at(23, 45), pass())!!
        assertEquals(TodayStage.FINISHED, p.stage)
        assertEquals("Sample Festival has ended", p.title)
        assertEquals(listOf(LiveAction.MARK_USED), p.actions)
        assertEquals(p.progress.max, p.progress.value)
    }

    @Test
    fun blocked_passes_point_at_the_fix() {
        val booking = plan(at(6), pass(startsAt = at(8), boarding = true))!!
        assertEquals("Add your boarding pass", booking.title)
        assertEquals("Sample Festival at 8:00 AM, Sample Grounds", booking.body)
        assertEquals(LiveAction.ADD_BOARDING_PASS, booking.actions.first())
        val pending = plan(at(15), pass(code = EventTicketSms.PENDING_PREFIX + "REF1"))!!
        assertEquals(LiveAction.ADD_CODE, pending.actions.first())
    }

    @Test
    fun no_venue_means_no_directions() {
        assertFalse(LiveAction.DIRECTIONS in plan(at(15), pass(venue = null))!!.actions)
    }

    @Test
    fun the_card_headline_reads_like_a_pickup_countdown() {
        val later = plan(at(16, 30), pass())!!
        assertEquals("3 hours", later.lead)
        assertEquals(" until it starts", later.leadRest)
        assertEquals("7:30 PM", later.startText)
        val soon = plan(at(19, 5), pass())!!
        assertEquals("25 min", soon.lead)
        val live = plan(at(20, 30), pass())!!
        assertEquals("On now", live.lead)
        assertEquals(", until 11:30 PM", live.leadRest)
    }

    @Test
    fun copy_has_no_dot_chains_or_dashes() {
        listOf(at(15), at(19, 0), at(20, 30), at(23, 45)).forEach { now ->
            val p = plan(now, pass())!!
            val text = p.title + " " + p.body + " " + p.lead + p.leadRest
            assertFalse(text, text.contains("·"))
            assertFalse(text, text.contains("—"))
        }
    }

    @Test
    fun copy_never_carries_seat_code_or_reference() {
        val p = plan(at(19, 0), pass())!!
        val text = listOfNotNull(p.title, p.body, p.chip).joinToString(" ")
        assertFalse(text.contains("A12"))
        assertFalse(text.contains("SAMPLE-CODE"))
        assertFalse(text.contains("SYNTH-0001"))
    }

    @Test
    fun leads_with_the_next_pass_and_counts_the_rest() {
        val early = pass(id = 1, startsAt = at(18))
        val late = pass(id = 2, startsAt = at(22))
        val p = plan(at(15), late, early)!!
        assertEquals(1L, p.ticketId)
        assertTrue(p.body.endsWith(". 1 more pass today"))
    }

    @Test
    fun refreshes_at_the_next_change_and_clears_at_midnight() {
        val p = plan(at(15), pass())!!
        // Moving bar: within 15 min, before the 6:30 PM "soon" boundary.
        assertEquals(at(15, 15), p.refreshAt)
        assertEquals(at(0, day = 15), p.clearAt)
        val soon = plan(at(19, 25), pass())!!
        assertEquals(at(19, 30), soon.refreshAt) // the start comes before the next 15 min
        val done = plan(at(23, 45), pass())!!
        assertEquals(at(0, day = 15), done.refreshAt)
    }
}
