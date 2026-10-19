package com.lipabill.app.data.tickets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KnownAirportsTest {

    @Test
    fun extract_iata_keeps_any_three_letter_code() {
        assertEquals("LHR", extractIata("London (LHR)"))
        assertEquals("NBO", extractIata("nbo"))
        assertNull(extractIata("Nairobi"))
    }

    @Test
    fun known_iata_maps_cities_and_drops_unknown_codes() {
        assertEquals("NBO", extractKnownIata("Jomo Kenyatta, Nairobi"))
        assertEquals("MBA", extractKnownIata("Mombasa"))
        assertNull(extractKnownIata("London (LHR)"))
        assertNull(extractKnownIata("Nairob"))
    }

    @Test
    fun route_requires_two_known_codes() {
        assertEquals("NBO" to "MBA", parseIataRoute("NBO → MBA"))
        assertNull(parseIataRoute("NBO → LHR"))
    }
}
