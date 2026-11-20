package com.lipabill.app.data.tickets.intelligence

import com.lipabill.app.data.model.notesValue
import com.lipabill.app.data.tickets.EventTicketSms
import com.lipabill.app.data.tickets.TicketImport

/** Something review should show. [blocking] issues stop the save; the rest are warnings. */
enum class PassIssue(val message: String, val blocking: Boolean) {
    MISSING_CODE("No entry code was found. Scan or paste it before saving.", true),
    PENDING_CODE("The entry code isn't out yet. You can add it when it arrives.", false),
    MISSING_DATE("Add the date so reminders can find it.", true),
    MISSING_NAME("Add the event name or the venue.", true),
    ENDS_BEFORE_START("The end time is before the start. Check both.", false),
    LONG_PAST("This date has passed. Check the year.", false),
    FAR_FUTURE("This date is more than two years away. Check the year.", false),
    ALREADY_STARTED("This pass’s start time has passed. It will be saved as past due.", false),
    DUE_TODAY("This pass is today.", false),
    CANCELLED("This ticket looks cancelled or void. It will be saved as used.", false),
    UNSUPPORTED_KIND("That looks like a flight. Passes holds event and SGR train tickets for now.", true)
}

/**
 * Deterministic checks before a pass is saved. Optional fields (seat, gate, tier) never block,
 * and an event may have no end time.
 */
object PassValidator {

    private const val DAY = 24L * 60L * 60L * 1000L

    fun validate(
        draft: TicketImport,
        kind: PassKind,
        nowMillis: Long = System.currentTimeMillis()
    ): List<PassIssue> = buildList {
        if (kind == PassKind.OTHER) add(PassIssue.UNSUPPORTED_KIND)
        val code = draft.barcodeValue.trim()
        when {
            code.isEmpty() -> add(PassIssue.MISSING_CODE)
            EventTicketSms.isPending(code) -> add(PassIssue.PENDING_CODE)
        }
        val start = draft.startsAtMillis
        if (start == null) {
            add(PassIssue.MISSING_DATE)
        } else {
            if (start < nowMillis - 30 * DAY) add(PassIssue.LONG_PAST)
            if (start > nowMillis + 730 * DAY) add(PassIssue.FAR_FUTURE)
            val ends = draft.notes.notesValue("Ends")?.toLongOrNull()
            if (ends != null && ends < start) add(PassIssue.ENDS_BEFORE_START)
        }
        if (draft.title.isBlank() && draft.venue.isNullOrBlank()) add(PassIssue.MISSING_NAME)
    }

    fun canSave(issues: List<PassIssue>): Boolean = issues.none { it.blocking }
}
