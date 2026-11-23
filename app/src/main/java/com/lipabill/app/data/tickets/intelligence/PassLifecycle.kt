package com.lipabill.app.data.tickets.intelligence

import com.lipabill.app.data.model.TicketStatus
import com.lipabill.app.data.tickets.TicketImport
import java.time.Instant
import java.time.ZoneId

/**
 * How a freshly read pass should land in Passes: active, past due, or already used
 * (cancelled / void on the document). Dates drive past-due; cancelled keywords win.
 */
data class PassLifecycle(
    val status: TicketStatus,
    /** Non-blocking review note, or null when the pass is a normal upcoming one. */
    val issue: PassIssue? = null,
    /** True when the event falls on the device's local calendar day. */
    val isToday: Boolean = false
)

object PassLifecycleResolver {

    /** A line that states the ticket's own status. Bare "invalid" never counts. */
    private val CANCELLED = Regex("""(?i)\b(?:cancelled|canceled|voided|void|refunded)\b""")

    /**
     * Fine print every ticket carries: "Void if altered", "If the event is cancelled…",
     * "Cancellation policy". A cancel word on such a line is a condition, not a status.
     */
    private val CONDITIONAL = Regex(
        """(?i)\b(?:if|unless|policy|in case|may|will be|should|terms|altered|without|prohibited|transferable|resale|non-refundable)\b"""
    )

    internal fun saysCancelled(text: String): Boolean =
        text.lineSequence().any { line -> CANCELLED.containsMatchIn(line) && !CONDITIONAL.containsMatchIn(line) }

    fun resolve(
        text: String,
        startsAtMillis: Long?,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): PassLifecycle {
        if (saysCancelled(text)) {
            return PassLifecycle(
                status = TicketStatus.USED,
                issue = PassIssue.CANCELLED
            )
        }
        val start = startsAtMillis ?: return PassLifecycle(TicketStatus.ACTIVE)
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val eventDay = Instant.ofEpochMilli(start).atZone(zone).toLocalDate()
        val isToday = eventDay == today
        return when {
            // A pass that started earlier today is still the one at the gate: keep it active.
            eventDay.isBefore(today) -> PassLifecycle(
                status = TicketStatus.PAST_DUE,
                issue = PassIssue.DAY_PASSED,
                isToday = false
            )
            start < nowMillis -> PassLifecycle(
                status = TicketStatus.ACTIVE,
                issue = PassIssue.ALREADY_STARTED,
                isToday = isToday
            )
            isToday -> PassLifecycle(
                status = TicketStatus.ACTIVE,
                issue = PassIssue.DUE_TODAY,
                isToday = true
            )
            else -> PassLifecycle(TicketStatus.ACTIVE, isToday = false)
        }
    }

    /** Re-check after the user edits the date in review (no original document text). */
    fun resolveDraft(
        draft: TicketImport,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): PassLifecycle {
        val blob = listOfNotNull(draft.title, draft.venue, draft.notes, draft.orderId)
            .joinToString("\n")
        return resolve(blob, draft.startsAtMillis, nowMillis, zone)
    }
}
