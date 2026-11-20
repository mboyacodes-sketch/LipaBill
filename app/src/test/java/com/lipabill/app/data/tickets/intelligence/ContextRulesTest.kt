package com.lipabill.app.data.tickets.intelligence

import com.lipabill.app.data.model.Ticket
import com.lipabill.app.data.model.TicketSource
import com.lipabill.app.data.model.TicketStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextRulesTest {

    private val now = 1_700_000_000_000L

    @Test
    fun upcoming_lists_active_future_passes() {
        val tickets = listOf(
            ticket(1, startsAt = now + 3_600_000L),
            ticket(2, startsAt = now - 3_600_000L),
            ticket(3, startsAt = now + 50L * 3_600_000L, status = TicketStatus.USED)
        )
        val findings = UpcomingPassRule.evaluate(tickets, now)
        assertEquals(listOf(1L), findings.flatMap { it.ticketIds })
        assertTrue(findings.single().body.contains("less than a day"))
    }

    @Test
    fun upcoming_ignores_passes_beyond_a_week() {
        val tickets = listOf(ticket(1, startsAt = now + 30L * 24L * 3_600_000L))
        assertTrue(UpcomingPassRule.evaluate(tickets, now).isEmpty())
    }

    @Test
    fun past_due_skips_used() {
        val tickets = listOf(
            ticket(1, startsAt = now - 10_000L),
            ticket(2, startsAt = now - 10_000L, status = TicketStatus.USED)
        )
        val findings = PastDuePassRule.evaluate(tickets, now)
        assertEquals(listOf(1L), findings.flatMap { it.ticketIds })
    }

    @Test
    fun duplicate_booking_ref_groups_matches() {
        val tickets = listOf(
            ticket(1, orderId = "ABC123"),
            ticket(2, orderId = "ABC123"),
            ticket(3, orderId = "OTHER")
        )
        val findings = DuplicateBookingRefRule.evaluate(tickets, now)
        assertEquals(1, findings.size)
        assertEquals(setOf(1L, 2L), findings.single().ticketIds.toSet())
    }

    @Test
    fun overlapping_within_two_hours() {
        val tickets = listOf(
            ticket(1, startsAt = now + 10_000L),
            ticket(2, startsAt = now + 10_000L + 90L * 60L * 1000L),
            ticket(3, startsAt = now + 10_000L + 5L * 60L * 60L * 1000L)
        )
        val findings = OverlappingEventsRule.evaluate(tickets, now)
        assertEquals(1, findings.size)
        assertEquals(setOf(1L, 2L), findings.single().ticketIds.toSet())
    }

    @Test
    fun evaluate_all_default_rules() {
        val tickets = listOf(
            ticket(1, startsAt = now + 3_600_000L, orderId = "DUP"),
            ticket(2, startsAt = now + 3_600_000L + 30L * 60L * 1000L, orderId = "DUP"),
            ticket(3, startsAt = now - 60_000L)
        )
        val findings = evaluateContextRules(tickets, now)
        assertTrue(findings.any { it.ruleId == "upcoming" })
        assertTrue(findings.any { it.ruleId == "past_due" })
        assertTrue(findings.any { it.ruleId == "duplicate_ref" })
        assertTrue(findings.any { it.ruleId == "overlap" })
        // Never leak booking refs into copy
        assertTrue(findings.none { it.body.contains("DUP") })
        assertTrue(findings.none { it.title.contains("DUP") })
    }

    private fun ticket(
        id: Long,
        startsAt: Long? = null,
        status: TicketStatus = TicketStatus.ACTIVE,
        orderId: String? = null
    ) = Ticket(
        id = id,
        title = "Sample",
        barcodeValue = "CODE-$id",
        source = TicketSource.E_TICKET,
        status = status,
        startsAtMillis = startsAt,
        orderId = orderId
    )
}
