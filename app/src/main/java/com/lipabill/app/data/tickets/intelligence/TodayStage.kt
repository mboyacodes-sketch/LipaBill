package com.lipabill.app.data.tickets.intelligence

import com.lipabill.app.data.model.Ticket
import com.lipabill.app.data.model.notesValue
import com.lipabill.app.data.tickets.EventTicketSms
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Where a pass on today's calendar is in its day. Drives the Today card on Passes. */
enum class TodayStage {
    /** More than an hour to go. */
    LATER,

    /** Within the hour: the card asks for attention. */
    SOON,

    /** Between the start and the end (or a default length when no end is printed). */
    LIVE,

    /** The end has passed, same day: offer Mark used. */
    FINISHED,

    /** SGR booking saved, but no boarding pass to show at the gate. */
    NEEDS_BOARDING_PASS,

    /** Event saved from SMS; the entry code hasn't been issued yet. */
    CODE_PENDING;

    val isBlocked: Boolean get() = this == NEEDS_BOARDING_PASS || this == CODE_PENDING
}

data class TodayStatus(
    val stage: TodayStage,
    /** Short live label for the card's pill ("Starts in 25 min"). Never a code or reference. */
    val pill: String,
    val startMillis: Long,
    val endMillis: Long
)

object TodayStages {

    const val SOON_WINDOW_MS = 60L * 60L * 1000L

    /** Passes rarely print an end time; assume an evening's length. */
    const val DEFAULT_LENGTH_MS = 4L * 60L * 60L * 1000L

    /** Printed end when it is after the start, else start + [DEFAULT_LENGTH_MS]. */
    fun endOf(ticket: Ticket): Long? {
        val start = ticket.occasionMillis ?: return null
        val printed = ticket.notes.notesValue("Ends")?.toLongOrNull()
        return printed?.takeIf { it > start } ?: (start + DEFAULT_LENGTH_MS)
    }

    /**
     * Stage for a dated pass. A finished pass is FINISHED even if it was blocked: there is
     * nothing left to fix. Before that, a missing boarding pass or entry code wins, because
     * it is what stops the holder at the gate.
     */
    fun stageOf(
        ticket: Ticket,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): TodayStatus? {
        val start = ticket.occasionMillis ?: return null
        val end = endOf(ticket) ?: return null
        val stage = when {
            nowMillis >= end -> TodayStage.FINISHED
            ticket.isConfirmationOnly -> TodayStage.NEEDS_BOARDING_PASS
            EventTicketSms.isPending(ticket.barcodeValue) -> TodayStage.CODE_PENDING
            nowMillis >= start -> TodayStage.LIVE
            start - nowMillis <= SOON_WINDOW_MS -> TodayStage.SOON
            else -> TodayStage.LATER
        }
        return TodayStatus(stage, pill(stage, start, end, nowMillis, zone), start, end)
    }

    internal fun pill(stage: TodayStage, start: Long, end: Long, nowMillis: Long, zone: ZoneId): String =
        when (stage) {
            TodayStage.LATER -> {
                val hours = ((start - nowMillis) / 3_600_000L).coerceAtLeast(1)
                val evening = Instant.ofEpochMilli(start).atZone(zone).hour >= 17
                "${if (evening) "Tonight" else "Today"} · in $hours h"
            }
            TodayStage.SOON -> {
                val minutes = ((start - nowMillis + 59_999L) / 60_000L).coerceAtLeast(1)
                "Starts in $minutes min"
            }
            TodayStage.LIVE -> "Happening now · until ${clock(end, zone)}"
            TodayStage.FINISHED -> "Finished · ${clock(end, zone)}"
            TodayStage.NEEDS_BOARDING_PASS -> "Boarding pass needed"
            TodayStage.CODE_PENDING -> "Code not out yet"
        }

    private val CLOCK = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

    private fun clock(millis: Long, zone: ZoneId): String =
        Instant.ofEpochMilli(millis).atZone(zone).format(CLOCK)
}
