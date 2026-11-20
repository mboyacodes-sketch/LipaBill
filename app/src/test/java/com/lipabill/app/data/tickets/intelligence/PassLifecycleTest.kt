package com.lipabill.app.data.tickets.intelligence

import com.lipabill.app.data.model.TicketStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ticket fine print must not mark a valid pass as used. Sample text only. */
class PassLifecycleTest {

    private val now = 1_800_000_000_000L
    private val inAWeek = now + 7L * 24L * 60L * 60L * 1000L

    @Test
    fun a_stated_cancellation_counts() {
        assertTrue(PassLifecycleResolver.saysCancelled("Status: CANCELLED"))
        assertTrue(PassLifecycleResolver.saysCancelled("This ticket has been cancelled."))
        assertTrue(PassLifecycleResolver.saysCancelled("SAMPLE PASS\nVOID"))
        assertTrue(PassLifecycleResolver.saysCancelled("Order refunded"))
    }

    @Test
    fun fine_print_does_not_count() {
        listOf(
            "Void if altered or duplicated.",
            "Invalid without valid ID.",
            "If the event is cancelled, refunds will be issued.",
            "Cancellation policy: no refunds.",
            "Tickets are non-refundable and void where prohibited.",
            "Not transferable. Resale voids this ticket."
        ).forEach { line ->
            assertFalse(line, PassLifecycleResolver.saysCancelled(line))
        }
    }

    @Test
    fun a_valid_ticket_with_fine_print_stays_active() {
        val text = "SAMPLE EVENT PASS\nVenue: Test Hall\nVoid if altered. Invalid without ID."
        val life = PassLifecycleResolver.resolve(text, inAWeek, now)
        assertEquals(TicketStatus.ACTIVE, life.status)
    }
}
