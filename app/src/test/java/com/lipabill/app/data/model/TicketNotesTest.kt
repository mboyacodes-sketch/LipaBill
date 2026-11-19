package com.lipabill.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TicketNotesTest {
    @Test
    fun setsReplacesAndRemovesSegments() {
        val notes = "Guest: Jane Doe · Tier: VIP · Ends: 123"
        assertEquals("Guest: Jane Doe · Ends: 123 · Tier: Early Bird", notes.withNotesValue("Tier", "Early Bird"))
        assertEquals("Tier: VIP · Ends: 123", notes.withNotesValue("Guest", " "))
        assertEquals("Guest: A - B", null.withNotesValue("Guest", "A · B"))
        assertNull("Tier: VIP".withNotesValue("Tier", null))
        assertEquals("VIP", notes.withNotesValue("Guest", "John").notesValue("Tier"))
    }
}
