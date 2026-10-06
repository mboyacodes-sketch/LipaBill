package com.lipabill.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TicketNotesTest {

    @Test
    fun first_label_wins() {
        val notes = "Dep time: 09:00 · Departure time: 10:15 · Seat: 12A"
        assertEquals("09:00", notes.firstNotesValue("Dep time", "Departure time"))
        assertEquals("10:15", notes.firstNotesValue("Departure time", "Dep time"))
        assertNull(notes.firstNotesValue("Gate", "Cabin"))
    }

    @Test
    fun labeled_piece_strips_the_prefix() {
        assertEquals("12A", "Gate B · Seat 12A · Economy".labeledPiece("Seat"))
        assertEquals("B", "Gate B · Seat 12A".labeledPiece("Gate"))
        assertNull("Economy".labeledPiece("Seat"))
    }
}
