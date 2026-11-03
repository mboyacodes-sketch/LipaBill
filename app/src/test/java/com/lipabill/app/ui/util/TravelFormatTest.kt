package com.lipabill.app.ui.util

import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class TravelFormatTest {

    @Test
    fun clock_follows_the_pass_pattern() {
        val zone = ZoneId.systemDefault()
        val zoned = ZonedDateTime.of(2026, 10, 10, 20, 5, 0, 0, zone)
        val clock = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
        assertEquals(zoned.format(clock), formatTravelClock(zoned))
    }
}
