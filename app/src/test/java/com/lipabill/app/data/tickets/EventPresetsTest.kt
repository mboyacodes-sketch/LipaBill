package com.lipabill.app.data.tickets

import com.lipabill.app.data.model.AppRegion
import com.lipabill.app.data.model.TicketSource
import com.lipabill.app.ui.tickets.EventSkyline
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EventPresetsTest {

    private val nairobi = ZoneId.of("Africa/Nairobi")
    private val before = LocalDateTime.of(2026, 11, 18, 12, 0).atZone(nairobi).toInstant().toEpochMilli()
    private val after = LocalDateTime.of(2027, 1, 6, 0, 0).atZone(nairobi).toInstant().toEpochMilli()
    private val talanta = EventPresets.TALANTA_BAYERN_AJAX

    @Test
    fun recognisesTheMatchFromVenueOrBothTeams() {
        assertEquals(talanta, EventPresets.match("E-ticket", "Talanta Sports City", null, nowMillis = before))
        assertEquals(talanta, EventPresets.match("Bayern Munchen v Ajax", null, nowMillis = before))
        assertEquals(talanta, EventPresets.match(null, "RAILA ODINGA INTERNATIONAL STADIUM", nowMillis = before))
        // One team alone is not this match.
        assertNull(EventPresets.match("Ajax supporters night", null, nowMillis = before))
        // After kick-off the preset is gone.
        assertNull(EventPresets.match("Talanta", null, nowMillis = after))
    }

    @Test
    fun kickOffIsTwentyFifteenNairobiOnFifthJanuary2027() {
        assertEquals(
            LocalDateTime.of(2027, 1, 5, 20, 15).atZone(nairobi).toInstant().toEpochMilli(),
            talanta.startsAtMillis
        )
    }

    @Test
    fun fillsOnlyBlanksAndKeepsThePrintedTime() {
        val printed = LocalDateTime.of(2027, 1, 5, 19, 0).atZone(nairobi).toInstant().toEpochMilli()
        val draft = TicketImport(title = "E-ticket", venue = null, startsAtMillis = printed, barcodeValue = "TLT-123", source = TicketSource.E_TICKET)
        val filled = EventPresets.applyTo(draft, talanta)
        assertEquals("FC Bayern Munich vs AFC Ajax", filled.title)
        assertEquals(talanta.venue, filled.venue)
        assertEquals(printed, filled.startsAtMillis)
        val named = draft.copy(title = "Inauguration Match", venue = "Gate C, Talanta")
        assertEquals("Inauguration Match", EventPresets.applyTo(named, talanta).title)
        assertEquals("Gate C, Talanta", EventPresets.applyTo(named, talanta).venue)
    }

    @Test
    fun talantaVenueGetsNairobiSkylineFromAnyHomeCountry() {
        assertEquals(EventSkyline.NAIROBI, EventSkyline.forTicket(AppRegion.UGANDA, talanta.venue))
        assertEquals(EventSkyline.NAIROBI, EventSkyline.forTicket(AppRegion.UGANDA, "Jamhuri Grounds"))
        assertTrue(EventPresets.upcoming(before).contains(talanta))
    }
}
