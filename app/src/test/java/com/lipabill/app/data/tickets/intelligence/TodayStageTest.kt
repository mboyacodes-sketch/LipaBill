package com.lipabill.app.data.tickets.intelligence

import com.lipabill.app.data.model.Ticket
import com.lipabill.app.data.model.TicketSource
import com.lipabill.app.data.model.TicketStatus
import com.lipabill.app.data.tickets.EventTicketSms
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/** Sample passes only. All times in Nairobi so "tonight" and day ends are fixed. */
class TodayStageTest {

    private val zone = ZoneId.of("Africa/Nairobi")
    private fun at(h: Int, m: Int = 0, day: Int = 14) =
        LocalDateTime.of(2026, 12, day, h, m).atZone(zone).toInstant().toEpochMilli()

    private val start = at(19, 30)

    private fun pass(
        startsAt: Long? = start,
        code: String = "SAMPLE-CODE",
        notes: String? = null,
        boarding: Boolean = false,
        status: TicketStatus = TicketStatus.ACTIVE
    ) = Ticket(
        id = 1,
        title = "Sample Festival",
        barcodeValue = code,
        source = TicketSource.E_TICKET,
        startsAtMillis = startsAt,
        notes = notes,
        expectsBoardingPass = boarding,
        hasBoardingPass = !boarding,
        status = status
    )

    private fun stage(t: Ticket, now: Long) = TodayStages.stageOf(t, now, zone)

    @Test
    fun walks_through_the_day() {
        assertEquals(TodayStage.LATER, stage(pass(), at(16, 30))?.stage)
        assertEquals("Tonight · in 3 h", stage(pass(), at(16, 30))?.pill)
        assertEquals(TodayStage.SOON, stage(pass(), at(19, 5))?.stage)
        assertEquals("Starts in 25 min", stage(pass(), at(19, 5))?.pill)
        assertEquals(TodayStage.LIVE, stage(pass(), at(20, 0))?.stage)
        assertEquals("Happening now · until 11:30 PM", stage(pass(), at(20, 0))?.pill)
        assertEquals(TodayStage.FINISHED, stage(pass(), at(23, 45))?.stage)
    }

    @Test
    fun daytime_passes_say_today_not_tonight() {
        val noon = pass(startsAt = at(13))
        assertEquals("Today · in 4 h", stage(noon, at(9))?.pill)
    }

    @Test
    fun a_printed_end_beats_the_default_length() {
        val ends = pass(notes = "Ends: ${at(21)}")
        assertEquals(TodayStage.FINISHED, stage(ends, at(21, 15))?.stage)
        // An end before the start is ignored.
        val bad = pass(notes = "Ends: ${at(18)}")
        assertEquals(TodayStage.LIVE, stage(bad, at(21, 15))?.stage)
    }

    @Test
    fun blocked_passes_say_why_until_they_finish() {
        val booking = pass(boarding = true)
        assertEquals(TodayStage.NEEDS_BOARDING_PASS, stage(booking, at(18))?.stage)
        val pending = pass(code = EventTicketSms.PENDING_PREFIX + "REF1")
        assertEquals(TodayStage.CODE_PENDING, stage(pending, at(20))?.stage)
        assertEquals(TodayStage.FINISHED, stage(pending, at(23, 59))?.stage)
    }

    @Test
    fun undated_passes_have_no_stage() {
        assertNull(stage(pass(startsAt = null), at(12)))
    }

    @Test
    fun a_started_pass_stays_active_until_its_day_is_over() {
        assertEquals(TicketStatus.ACTIVE, pass().effectiveStatus(at(21), zone))
        assertEquals(TicketStatus.ACTIVE, pass().effectiveStatus(at(23, 59), zone))
        assertEquals(TicketStatus.PAST_DUE, pass().effectiveStatus(at(0, 1, day = 15), zone))
        // A night that runs past midnight stays active until its printed end.
        val late = pass(notes = "Ends: ${at(2, day = 15)}")
        assertEquals(TicketStatus.ACTIVE, late.effectiveStatus(at(1, day = 15), zone))
        assertEquals(TicketStatus.PAST_DUE, late.effectiveStatus(at(3, day = 15), zone))
        // Saved statuses still win.
        assertEquals(TicketStatus.USED, pass(status = TicketStatus.USED).effectiveStatus(at(21), zone))
    }
}
