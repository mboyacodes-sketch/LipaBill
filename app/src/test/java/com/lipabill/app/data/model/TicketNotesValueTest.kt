package com.lipabill.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TicketNotesValueTest {

    @Test
    fun notes_value_reads_labeled_segments() {
        val notes = "Ticketmaster · Ends: 1700000000000 · Time: 6:00 pm – 9:00 pm"
        assertEquals("1700000000000", notes.notesValue("Ends"))
        assertEquals("6:00 pm – 9:00 pm", notes.notesValue("Time"))
        assertNull(notes.notesValue("Seat"))
        assertNull(null.notesValue("Ends"))
    }
}
