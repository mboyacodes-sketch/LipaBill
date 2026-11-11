package com.lipabill.app.data.tickets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalTime
import java.time.Month

class TicketTextHelpersTest {

    @Test
    fun sgr_station_normalizes_city_names() {
        assertEquals("Nairobi Terminus", sgrStationName("Nairobi"))
        assertEquals("Mombasa Terminus", sgrStationName("mombasa"))
        assertEquals("Voi", sgrStationName("Voi"))
    }

    @Test
    fun clock_from_parts_handles_ampm_and_invalid() {
        assertEquals(LocalTime.of(14, 30), clockFromParts(2, 30, "pm"))
        assertEquals(LocalTime.of(0, 15), clockFromParts(12, 15, "am"))
        assertEquals(LocalTime.of(12, 0), clockFromParts(12, 0, "pm"))
        assertNull(clockFromParts(25, 0))
    }

    @Test
    fun parse_month_accepts_abbreviations() {
        assertEquals(Month.NOVEMBER, parseMonth("Nov"))
        assertEquals(Month.SEPTEMBER, parseMonth("september"))
        assertNull(parseMonth("xyz"))
    }

    @Test
    fun first_match_returns_trimmed_group() {
        assertEquals("ABC123", firstMatch("PNR: ABC123 ", Regex("""PNR:\s*(\S+)""")))
        assertNull(firstMatch("none", Regex("""PNR:\s*(\S+)""")))
    }
}
