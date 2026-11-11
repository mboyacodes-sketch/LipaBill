package com.lipabill.app.engage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ChallengeClockTest {

    private val nairobi = ZoneId.of("Africa/Nairobi")

    @Test
    fun zone_of_falls_back_for_blank_or_invalid() {
        assertEquals(ChallengeClock.fallback, ChallengeClock.zoneOf(null))
        assertEquals(ChallengeClock.fallback, ChallengeClock.zoneOf("   "))
        assertEquals(ChallengeClock.fallback, ChallengeClock.zoneOf("Not/AZone"))
        assertEquals(nairobi, ChallengeClock.zoneOf("Africa/Nairobi"))
    }

    @Test
    fun window_from_is_inclusive_start_exclusive_end() {
        val noon = LocalDate.of(2026, 11, 11).atTime(12, 0).atZone(nairobi).toInstant().toEpochMilli()
        val (start, end) = ChallengeClock.windowFrom(noon, 7, nairobi)
        assertEquals(LocalDate.of(2026, 11, 11), ChallengeClock.localDate(start, nairobi))
        assertEquals(LocalDate.of(2026, 11, 18), ChallengeClock.localDate(end, nairobi))
        assertEquals(1, ChallengeClock.dayNumber(start, end, noon, nairobi))
        val lastDay = LocalDate.of(2026, 11, 17).atTime(12, 0).atZone(nairobi).toInstant().toEpochMilli()
        assertEquals(7, ChallengeClock.dayNumber(start, end, lastDay, nairobi))
    }

    @Test
    fun day_number_stays_on_last_day_after_window() {
        val start = LocalDate.of(2026, 11, 1).atStartOfDay(nairobi).toInstant().toEpochMilli()
        val end = LocalDate.of(2026, 11, 4).atStartOfDay(nairobi).toInstant().toEpochMilli()
        val after = LocalDate.of(2026, 11, 10).atStartOfDay(nairobi).toInstant().toEpochMilli()
        assertEquals(3, ChallengeClock.dayNumber(start, end, after, nairobi))
    }

    @Test
    fun iso_week_helpers() {
        val monday = LocalDate.of(2026, 11, 9).atStartOfDay(nairobi).toInstant().toEpochMilli()
        assertEquals("2026-W46", ChallengeClock.isoWeekId(monday, nairobi))
        val (weekStart, weekEnd) = ChallengeClock.isoWeekRange(monday, nairobi)
        assertEquals(LocalDate.of(2026, 11, 9), ChallengeClock.localDate(weekStart, nairobi))
        assertEquals(LocalDate.of(2026, 11, 16), ChallengeClock.localDate(weekEnd, nairobi))
        assertEquals("2026-W45", ChallengeClock.previousWeekId("2026-W46"))
        assertNull(ChallengeClock.previousWeekId("bad"))
    }

    @Test
    fun next_sunday_evening_is_strictly_after_now() {
        val sundayEvening = LocalDate.of(2026, 11, 8).atTime(18, 0).atZone(nairobi).toInstant().toEpochMilli()
        val next = ChallengeClock.nextSundayEvening(sundayEvening, nairobi, hour = 18)
        assertTrue(next > sundayEvening)
        assertEquals(LocalDate.of(2026, 11, 15), ChallengeClock.localDate(next, nairobi))
    }
}
