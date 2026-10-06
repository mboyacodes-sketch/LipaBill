package com.lipabill.app.ui.util

import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class TravelFormatTest {

    @Test
    fun clock_and_date_follow_the_pass_patterns() {
        val zone = ZoneId.systemDefault()
        val zoned = ZonedDateTime.of(2026, 10, 10, 20, 5, 0, 0, zone)
        val millis = zoned.toInstant().toEpochMilli()
        val clock = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
        val date = DateTimeFormatter.ofPattern("d MMM, yyyy", Locale.ENGLISH)
        assertEquals(zoned.format(clock), formatTravelClock(millis))
        assertEquals(zoned.format(clock), formatTravelClock(zoned))
        assertEquals(zoned.format(date), formatTravelDate(millis))
    }
}
