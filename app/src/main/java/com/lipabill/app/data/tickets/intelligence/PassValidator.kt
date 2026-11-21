package com.lipabill.app.data.tickets.intelligence

import com.lipabill.app.data.model.notesValue
import com.lipabill.app.data.tickets.ETicketTextParser
import com.lipabill.app.data.tickets.EventTicketExtractor
import com.lipabill.app.data.tickets.EventTicketSms
import com.lipabill.app.data.tickets.TicketImport
import java.time.Instant
import java.time.ZoneId

/** Something review should show. [blocking] issues stop the save; the rest are warnings. */
enum class PassIssue(val message: String, val blocking: Boolean) {
    MISSING_CODE("No entry code was found. Scan or paste it before saving.", true),
    PENDING_CODE("The entry code isn't out yet. You can add it when it arrives.", false),
    MISSING_DATE("Add the date so reminders can find it.", true),
    MISSING_NAME("Add the event name or the venue.", true),
    ENDS_BEFORE_START("The end time is before the start. Check both.", false),
    LONG_PAST("This date has passed. Check the year.", false),
    FAR_FUTURE("This date is more than two years away. Check the year.", false),
    ALREADY_STARTED("This pass has already started today.", false),
    DAY_PASSED("This pass’s day has passed. It will be saved as past due.", false),
    DUE_TODAY("This pass is today.", false),
    CANCELLED("This ticket looks cancelled or void. It will be saved as used.", false),
    UNSUPPORTED_KIND("That looks like a flight. Passes holds event and SGR train tickets for now.", true),
    WEEKDAY_MISMATCH("The weekday on the ticket doesn't match this date. Check the year.", false),
    IMPLAUSIBLE_CODE("That entry code looks like a phone, link or amount. Check it.", false),
    MULTI_DAY_TOO_LONG("This event spans more than a week. Check the end date.", false),
    MULTI_TICKET_FILE("This file looks like it has several tickets. Save one at a time for now.", false)
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
        nowMillis: Long = System.currentTimeMillis(),
        /** OCR / PDF text used to catch printed weekday mismatches. */
        sourceText: String? = null,
        zone: ZoneId = ZoneId.systemDefault(),
        distinctTicketCodes: Int = 1
    ): List<PassIssue> = buildList {
        if (kind == PassKind.OTHER) add(PassIssue.UNSUPPORTED_KIND)
        if (distinctTicketCodes > 1) add(PassIssue.MULTI_TICKET_FILE)
        val code = draft.barcodeValue.trim()
        when {
            code.isEmpty() -> add(PassIssue.MISSING_CODE)
            EventTicketSms.isPending(code) -> add(PassIssue.PENDING_CODE)
            ETicketTextParser.looksLikeImplausibleCode(code) -> add(PassIssue.IMPLAUSIBLE_CODE)
        }
        val start = draft.startsAtMillis
        if (start == null) {
            add(PassIssue.MISSING_DATE)
        } else {
            if (start < nowMillis - 30 * DAY) add(PassIssue.LONG_PAST)
            if (start > nowMillis + 730 * DAY) add(PassIssue.FAR_FUTURE)
            val ends = draft.notes.notesValue("Ends")?.toLongOrNull()
            if (ends != null) {
                if (ends < start) add(PassIssue.ENDS_BEFORE_START)
                if (ends - start > 7 * DAY) add(PassIssue.MULTI_DAY_TOO_LONG)
            }
            if (!sourceText.isNullOrBlank()) {
                val local = Instant.ofEpochMilli(start).atZone(zone).toLocalDate()
                val hit = EventTicketExtractor.findAllDates(sourceText, local)
                    .firstOrNull { it.start == local }
                if (hit != null && EventTicketExtractor.weekdayMismatch(hit)) {
                    add(PassIssue.WEEKDAY_MISMATCH)
                }
            }
        }
        if (draft.title.isBlank() && draft.venue.isNullOrBlank()) add(PassIssue.MISSING_NAME)
    }

    fun canSave(issues: List<PassIssue>): Boolean = issues.none { it.blocking }
}
