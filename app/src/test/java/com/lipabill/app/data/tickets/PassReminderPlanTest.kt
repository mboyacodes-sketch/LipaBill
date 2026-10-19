package com.lipabill.app.data.tickets

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class PassReminderPlanTest {

    private val nairobi = ZoneId.of("Africa/Nairobi")

    @Test
    fun evening_event_gets_all_three() {
        val now = at(2026, 10, 10, 12, 0)
        val start = at(2026, 10, 12, 19, 30)
        val planned = planPassReminders(start, now, nairobi)
        assertEquals(
            listOf(
                PassReminderKind.DAY_BEFORE to at(2026, 10, 11, 18, 0),
                PassReminderKind.HOURS_24 to at(2026, 10, 11, 19, 30),
                PassReminderKind.MORNING_OF to at(2026, 10, 12, 8, 0)
            ),
            planned.map { it.kind to it.atMillis }
        )
    }

    @Test
    fun early_event_uses_a_six_am_morning_and_drops_a_past_24_hour_ping() {
        val now = at(2026, 10, 10, 12, 0)
        val start = at(2026, 10, 11, 8, 0)
        val planned = planPassReminders(start, now, nairobi)
        assertEquals(
            listOf(
                PassReminderKind.DAY_BEFORE to at(2026, 10, 10, 18, 0),
                PassReminderKind.MORNING_OF to at(2026, 10, 11, 6, 0)
            ),
            planned.map { it.kind to it.atMillis }
        )
    }

    @Test
    fun close_evening_pings_collapse_to_the_24_hour_one() {
        val now = at(2026, 10, 10, 8, 0)
        val start = at(2026, 10, 12, 18, 20)
        val planned = planPassReminders(start, now, nairobi)
        assertEquals(
            listOf(
                PassReminderKind.HOURS_24 to at(2026, 10, 11, 18, 20),
                PassReminderKind.MORNING_OF to at(2026, 10, 12, 8, 0)
            ),
            planned.map { it.kind to it.atMillis }
        )
    }

    @Test
    fun today_after_morning_has_nothing_left() {
        val now = at(2026, 10, 10, 12, 0)
        val start = at(2026, 10, 10, 20, 0)
        assertEquals(emptyList<PlannedPassReminder>(), planPassReminders(start, now, nairobi))
    }

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(nairobi).toInstant().toEpochMilli()
}
