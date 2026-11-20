package com.lipabill.app.tickets

import com.lipabill.app.data.model.Ticket
import com.lipabill.app.data.tickets.intelligence.TodayStage
import com.lipabill.app.data.tickets.intelligence.TodayStages
import java.time.Instant
import java.time.ZoneId

/** Buttons the live notification can carry, in the order shown. */
enum class LiveAction { SHOW_CODE, MARK_USED, ADD_BOARDING_PASS, ADD_CODE, DIRECTIONS }

/**
 * The bar under the live notification, in minutes: a lead-in up to the start (the ticket marker
 * travels along it, like a car to the pickup), then the event itself.
 */
data class LiveProgress(val leadMinutes: Int, val eventMinutes: Int, val value: Int) {
    val max: Int get() = leadMinutes + eventMinutes
}

/** Everything the live notification shows for the pass that leads today. */
data class PassLiveContent(
    val ticketId: Long,
    val stage: TodayStage,
    val title: String,
    val body: String,
    /** Status-bar chip text ("7:30 PM", "Now"). Null lets the chip show the countdown instead. */
    val chip: String?,
    /** Start time for a live countdown (within the hour). */
    val countdownTo: Long?,
    val progress: LiveProgress,
    val actions: List<LiveAction>,
    /** Where Directions points: the venue as printed, never a code or reference. */
    val venue: String?,
    /** When the text or bar next needs redrawing. */
    val refreshAt: Long?,
    /** Local midnight: the notification removes itself then. */
    val clearAt: Long
)

/**
 * Pure plan for the Uber-style live notification. Uses the same stages as the Today card, so
 * the notification and Passes never disagree. Copy never carries a code, seat or reference.
 */
object PassLivePlanner {

    /** Lead-in shown on the bar before the start. */
    const val LEAD_MS = 3L * 60L * 60L * 1000L

    /** While a pass is ahead or on, redraw the bar at least this often. */
    const val REFRESH_MS = 15L * 60L * 1000L

    fun plan(
        tickets: List<Ticket>,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): PassLiveContent? {
        val today = TodayStages.onToday(tickets, nowMillis, zone)
        val (ticket, status) = today.firstOrNull() ?: return null
        val start = status.startMillis
        val end = status.endMillis
        val stage = status.stage
        val at = TodayStages.clock(start, zone)
        val title = when (stage) {
            TodayStage.LATER -> "Pass today · $at"
            TodayStage.SOON -> "Starts at $at"
            TodayStage.LIVE -> "Happening now · until ${TodayStages.clock(end, zone)}"
            TodayStage.FINISHED -> "Finished — mark it used?"
            TodayStage.NEEDS_BOARDING_PASS -> "Boarding pass needed · $at"
            TodayStage.CODE_PENDING -> "Code not out yet · $at"
        }
        val venue = ticket.venue?.trim()?.takeIf { it.isNotEmpty() }
        val more = today.size - 1
        val body = listOfNotNull(
            ticket.title.takeIf { it.isNotBlank() },
            venue,
            if (more > 0) "+$more more today" else null
        ).joinToString(" · ")
        val chip = when (stage) {
            TodayStage.SOON, TodayStage.FINISHED -> null
            TodayStage.LIVE -> "Now"
            else -> at
        }
        val leadMinutes = (LEAD_MS / 60_000L).toInt()
        val eventMinutes = ((end - start) / 60_000L).toInt().coerceAtLeast(1)
        val elapsed = ((nowMillis - (start - LEAD_MS)) / 60_000L).toInt()
        val progress = LiveProgress(
            leadMinutes = leadMinutes,
            eventMinutes = eventMinutes,
            value = if (stage == TodayStage.FINISHED) leadMinutes + eventMinutes
            else elapsed.coerceIn(0, leadMinutes + eventMinutes)
        )
        val actions = buildList {
            when (stage) {
                TodayStage.LATER, TodayStage.SOON -> add(LiveAction.SHOW_CODE)
                TodayStage.LIVE -> {
                    add(LiveAction.SHOW_CODE)
                    add(LiveAction.MARK_USED)
                }
                TodayStage.FINISHED -> add(LiveAction.MARK_USED)
                TodayStage.NEEDS_BOARDING_PASS -> add(LiveAction.ADD_BOARDING_PASS)
                TodayStage.CODE_PENDING -> add(LiveAction.ADD_CODE)
            }
            val headingThere = stage == TodayStage.LATER || stage == TodayStage.SOON || stage.isBlocked
            if (venue != null && headingThere) add(LiveAction.DIRECTIONS)
        }
        val midnight = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
            .plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val moving = stage != TodayStage.FINISHED
        val refreshAt = listOfNotNull(
            start - TodayStages.SOON_WINDOW_MS,
            start,
            end,
            midnight,
            if (moving) nowMillis + REFRESH_MS else null
        ).filter { it > nowMillis }.minOrNull()
        return PassLiveContent(
            ticketId = ticket.id,
            stage = stage,
            title = title,
            body = body,
            chip = chip,
            countdownTo = start.takeIf { stage == TodayStage.SOON },
            progress = progress,
            actions = actions,
            venue = venue,
            refreshAt = refreshAt,
            clearAt = midnight
        )
    }
}
