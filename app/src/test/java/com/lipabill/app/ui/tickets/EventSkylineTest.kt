package com.lipabill.app.ui.tickets

import com.lipabill.app.data.model.Ticket
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EventSkylineTest {

    @Test
    fun detectsCitiesFromVenueText() {
        assertEquals(EventSkyline.NAIROBI, EventSkyline.detect("KICC Grounds"))
        assertEquals(EventSkyline.NAIROBI, EventSkyline.detect("Moi International Sports Centre, Kasarani"))
        assertEquals(EventSkyline.MOMBASA, EventSkyline.detect("Mama Ngina Waterfront"))
        assertEquals(EventSkyline.COAST, EventSkyline.detect("Diani Beach Hotel"))
        assertEquals(EventSkyline.KAMPALA, EventSkyline.detect(null, "Lugogo Cricket Oval"))
        assertEquals(EventSkyline.LONDON, EventSkyline.detect("The O2 Arena, London"))
    }

    @Test
    fun specificCityBeatsStadiumAndUnknownFallsBack() {
        assertEquals(EventSkyline.NAIROBI, EventSkyline.detect("Nyayo National Stadium"))
        assertEquals(EventSkyline.STADIUM, EventSkyline.detect("Riverside Stadium"))
        assertEquals(EventSkyline.CITY, EventSkyline.detect("The Venue", null, ""))
        assertEquals(EventSkyline.CITY, EventSkyline.detect())
    }

    @Test
    fun ticketPicksSkylineFromVenueAndCityNotes() {
        val t = Ticket(title = "Blankets & Wine", venue = "Laureate Gardens", notes = "City: Nairobi", barcodeValue = "X1")
        assertEquals(EventSkyline.NAIROBI, t.toEventPassModel().skyline)
    }

    @Test
    fun lightingFollowsTheClock() {
        assertEquals("night", skyAt(23f).label)
        assertEquals("dawn", skyAt(6.2f).label)
        assertEquals("daytime", skyAt(12f).label)
        assertEquals("golden hour", skyAt(17.8f).label)
        assertEquals("dusk", skyAt(19.3f).label)
        assertTrue(skyAt(22f).windows > 0.9f)
        assertTrue(skyAt(12f).windows < 0.01f)
        assertNotNull(skyAt(12f).sunProgress)
        assertNull(skyAt(22f).sunProgress)
        // Wraps around midnight.
        assertEquals(skyAt(1f), skyAt(25f))
    }

    @Test
    fun eventHourUsesLocalStartAndTreatsDateOnlyAsShowNight() {
        val zone = ZoneId.of("Africa/Nairobi")
        fun at(h: Int, m: Int) = LocalDateTime.of(2026, 12, 5, h, m).atZone(zone).toInstant().toEpochMilli()
        assertEquals(14.5f, eventHour(at(14, 30), zone), 0.001f)
        assertEquals(DefaultEventHour, eventHour(at(0, 0), zone), 0.001f)
        assertEquals(DefaultEventHour, eventHour(null, zone), 0.001f)
    }

    @Test
    fun venueOnlyTicketsUseTheHomeRegionCity() {
        assertEquals(EventSkyline.NAIROBI, EventSkyline.forTicket(com.lipabill.app.data.model.AppRegion.KENYA, "Jenga Jungle Restaurant"))
        assertEquals(EventSkyline.NAIROBI, EventSkyline.forTicket(com.lipabill.app.data.model.AppRegion.KENYA, "iHUB"))
        assertEquals(EventSkyline.KAMPALA, EventSkyline.forTicket(com.lipabill.app.data.model.AppRegion.UGANDA, "Riverside Stadium"))
        assertEquals(EventSkyline.DAR, EventSkyline.forTicket(com.lipabill.app.data.model.AppRegion.TANZANIA, null))
        assertEquals(EventSkyline.KIGALI, EventSkyline.forTicket(com.lipabill.app.data.model.AppRegion.RWANDA, "2"))
        // A named city still wins over the home region.
        assertEquals(EventSkyline.MOMBASA, EventSkyline.forTicket(com.lipabill.app.data.model.AppRegion.UGANDA, "Nyali Golf Club"))
        val t = Ticket(title = "Cloud for CTOs : Part 2", venue = "iHUB", barcodeValue = "X2")
        assertEquals(EventSkyline.NAIROBI, t.toEventPassModel(com.lipabill.app.data.model.AppRegion.KENYA).skyline)
    }
}
