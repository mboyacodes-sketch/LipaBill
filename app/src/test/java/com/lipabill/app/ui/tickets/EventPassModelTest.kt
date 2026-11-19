package com.lipabill.app.ui.tickets

import com.lipabill.app.data.model.Ticket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EventPassModelTest {

    private fun ticket(
        title: String = "Neon Noir Rooftop",
        venue: String? = "Skyline Terrace",
        seatOrTier: String? = null,
        notes: String? = null,
        orderId: String? = "AD-2147"
    ) = Ticket(
        title = title,
        venue = venue,
        seatOrTier = seatOrTier,
        notes = notes,
        orderId = orderId,
        barcodeValue = "QR-PAYLOAD-123"
    )

    @Test
    fun mapsTemplateFieldsWhenPresent() {
        val m = ticket(
            seatOrTier = "Early Bird · Gate B East",
            notes = "Guest: MARA KHAN · Doors: 18:30 · Price: KSH 3,400 · City: Westlands · Ticket: 1 of 2"
        ).toEventPassModel()
        assertEquals("Mara", m.firstName)
        assertEquals("AD-2147", m.orderRef)
        assertEquals("Early Bird", m.chip)
        assertEquals("B East", m.gate)
        assertEquals("01 / 02", m.counter)
        assertEquals("Skyline Terrace · Westlands", m.venue)
        val byLabel = m.fields.associate { it.label to it.value }
        assertEquals("18:30", byLabel["Doors"])
        assertEquals("Early Bird", byLabel["Zone"])
        assertEquals("Mara K.", byLabel["Guest"])
        assertEquals("B East", byLabel["Gate"])
        assertEquals("KSH 3,400", byLabel["Paid"])
    }

    @Test
    fun dropsMissingFieldsInsteadOfShowingPlaceholders() {
        val m = ticket(venue = null, notes = null, orderId = null, seatOrTier = null).toEventPassModel()
        assertNull(m.firstName)
        assertNull(m.chip)
        assertNull(m.counter)
        assertNull(m.venue)
        assertTrue(m.fields.none { it.label == "Paid" || it.label == "Guest" })
        assertTrue(m.fields.none { it.value == "—" })
    }

    @Test
    fun routeVenuesAreNotEventVenues() {
        assertNull(ticket(venue = "Nairobi → Mombasa").toEventPassModel().venue)
    }

    @Test
    fun nameHelpers() {
        assertEquals("Mara K.", shortName("mara  khan"))
        assertEquals("Mara", shortName("MARA"))
        assertNull(firstName("1"))
    }
}
