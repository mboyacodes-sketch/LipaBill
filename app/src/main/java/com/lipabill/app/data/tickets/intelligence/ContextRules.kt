package com.lipabill.app.data.tickets.intelligence

import com.lipabill.app.data.model.Ticket
import com.lipabill.app.data.model.TicketStatus

/** One finding about the user's passes. Never includes codes or booking refs. */
data class ContextFinding(
    val ruleId: String,
    val title: String,
    val body: String,
    /** Ticket ids involved (for navigation), never shown in the copy. */
    val ticketIds: List<Long> = emptyList()
)

/** Pure check over the in-memory pass list. */
fun interface ContextRule {
    fun evaluate(tickets: List<Ticket>, nowMillis: Long): List<ContextFinding>
}

object UpcomingPassRule : ContextRule {
    private const val HORIZON_MS = 7L * 24L * 60L * 60L * 1000L

    override fun evaluate(tickets: List<Ticket>, nowMillis: Long): List<ContextFinding> =
        tickets.mapNotNull { ticket ->
            val start = ticket.occasionMillis ?: return@mapNotNull null
            if (ticket.effectiveStatus(nowMillis) != TicketStatus.ACTIVE) return@mapNotNull null
            if (start <= nowMillis) return@mapNotNull null
            // "In the next few days" must be true: further out isn't news yet.
            if (start - nowMillis > HORIZON_MS) return@mapNotNull null
            val hours = ((start - nowMillis) / 3_600_000L).coerceAtLeast(0)
            ContextFinding(
                ruleId = "upcoming",
                title = "Coming up",
                body = when {
                    hours < 24 -> "A pass is less than a day away."
                    hours < 48 -> "A pass is tomorrow."
                    else -> "A pass is in the next few days."
                },
                ticketIds = listOf(ticket.id)
            )
        }
}

object PastDuePassRule : ContextRule {
    override fun evaluate(tickets: List<Ticket>, nowMillis: Long): List<ContextFinding> =
        tickets.filter { it.effectiveStatus(nowMillis) == TicketStatus.PAST_DUE }
            .map {
                ContextFinding(
                    ruleId = "past_due",
                    title = "Past due",
                    body = "A pass’s day has passed and it wasn’t marked used.",
                    ticketIds = listOf(it.id)
                )
            }
}

object DuplicateBookingRefRule : ContextRule {
    override fun evaluate(tickets: List<Ticket>, nowMillis: Long): List<ContextFinding> {
        val byRef = tickets
            .mapNotNull { t -> t.orderId?.trim()?.takeIf { it.isNotEmpty() }?.let { it to t } }
            .groupBy({ it.first }, { it.second })
        return byRef.values.filter { it.size > 1 }.map { group ->
            ContextFinding(
                ruleId = "duplicate_ref",
                title = "Same booking twice",
                body = "Two passes share a booking reference. Keep one if they’re the same trip.",
                ticketIds = group.map { it.id }
            )
        }
    }
}

object OverlappingEventsRule : ContextRule {
    private const val WINDOW_MS = 2L * 60L * 60L * 1000L

    override fun evaluate(tickets: List<Ticket>, nowMillis: Long): List<ContextFinding> {
        val dated = tickets.mapNotNull { t ->
            val start = t.occasionMillis ?: return@mapNotNull null
            if (t.effectiveStatus(nowMillis) != TicketStatus.ACTIVE) return@mapNotNull null
            t to start
        }.sortedBy { it.second }
        val findings = mutableListOf<ContextFinding>()
        for (i in dated.indices) {
            for (j in i + 1 until dated.size) {
                val a = dated[i]
                val b = dated[j]
                if (b.second - a.second > WINDOW_MS) break
                findings += ContextFinding(
                    ruleId = "overlap",
                    title = "Overlapping passes",
                    body = "Two active passes are within two hours of each other.",
                    ticketIds = listOf(a.first.id, b.first.id)
                )
            }
        }
        return findings
    }
}

/** Every rule. Passes shows due-soon, past-due, overlap and duplicate notices. */
val DEFAULT_CONTEXT_RULES: List<ContextRule> = listOf(
    UpcomingPassRule,
    PastDuePassRule,
    DuplicateBookingRefRule,
    OverlappingEventsRule
)

fun evaluateContextRules(
    tickets: List<Ticket>,
    nowMillis: Long,
    rules: List<ContextRule> = DEFAULT_CONTEXT_RULES
): List<ContextFinding> = rules.flatMap { it.evaluate(tickets, nowMillis) }
