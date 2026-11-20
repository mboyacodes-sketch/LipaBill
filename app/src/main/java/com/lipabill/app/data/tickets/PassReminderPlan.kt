package com.lipabill.app.data.tickets

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

enum class PassReminderKind {
    /** Calendar evening before the event (hour from [PassReminderLeadTimes.dayBeforeHour]). */
    DAY_BEFORE,

    /** [PassReminderLeadTimes.hoursBefore] hours before the start time. */
    HOURS_24,

    /** Morning of the event (hour from [PassReminderLeadTimes.morningHour]). */
    MORNING_OF
}

data class PlannedPassReminder(
    val kind: PassReminderKind,
    val atMillis: Long
)

/**
 * User-configurable lead times. A value of `-1` turns that reminder off.
 * Defaults match the original fixed plan (6 PM evening before, 24h ahead, 8 AM morning).
 */
data class PassReminderLeadTimes(
    val dayBeforeHour: Int = 18,
    val hoursBefore: Int = 24,
    val morningHour: Int = 8
) {
    init {
        require(dayBeforeHour == -1 || dayBeforeHour in 0..23)
        require(hoursBefore == -1 || hoursBefore in setOf(2, 12, 24, 48))
        require(morningHour == -1 || morningHour in 5..11)
    }

    companion object {
        val DEFAULT = PassReminderLeadTimes()
    }
}

private const val DEDUPE_WINDOW_MS = 45L * 60L * 1000L

/**
 * Reminders for a pass that still has a future start.
 * Times already passed are left out. Two that land within 45 minutes
 * collapse to the more specific one.
 */
fun planPassReminders(
    startMillis: Long,
    nowMillis: Long,
    zone: ZoneId,
    leadTimes: PassReminderLeadTimes = PassReminderLeadTimes.DEFAULT
): List<PlannedPassReminder> {
    val start = Instant.ofEpochMilli(startMillis).atZone(zone)
    val slots = buildList {
        if (leadTimes.morningHour >= 0) {
            morningOf(start, leadTimes.morningHour)?.let { add(PassReminderKind.MORNING_OF to it) }
        }
        if (leadTimes.hoursBefore >= 0) {
            add(PassReminderKind.HOURS_24 to start.minusHours(leadTimes.hoursBefore.toLong()))
        }
        if (leadTimes.dayBeforeHour >= 0) {
            val eveningBefore = start.toLocalDate().minusDays(1)
                .atTime(LocalTime.of(leadTimes.dayBeforeHour, 0))
                .atZone(zone)
            if (eveningBefore.isBefore(start)) {
                add(PassReminderKind.DAY_BEFORE to eveningBefore)
            }
        }
    }
    return slots
        .map { (kind, whenAt) -> PlannedPassReminder(kind, whenAt.toInstant().toEpochMilli()) }
        .filter { it.atMillis > nowMillis }
        .sortedBy { it.atMillis }
        .fold(mutableListOf()) { kept, next ->
            val previous = kept.lastOrNull()
            if (previous != null && next.atMillis - previous.atMillis < DEDUPE_WINDOW_MS) {
                if (next.kind.priority > previous.kind.priority) {
                    kept[kept.lastIndex] = next
                }
            } else {
                kept += next
            }
            kept
        }
}

/**
 * Morning slot on the event day. Events that start at 9:00 or earlier use two hours
 * earlier than [morningHour] (floored at 5), and only when that is still 45 minutes
 * before the start.
 */
private fun morningOf(start: ZonedDateTime, morningHour: Int): ZonedDateTime? {
    val hour = if (start.toLocalTime() <= LocalTime.of(9, 0)) {
        (morningHour - 2).coerceAtLeast(5)
    } else {
        morningHour
    }
    val slot = start.toLocalDate().atTime(hour, 0).atZone(start.zone)
    return slot.takeIf { slot.isBefore(start.minusMinutes(45)) }
}

private val PassReminderKind.priority: Int
    get() = when (this) {
        PassReminderKind.HOURS_24 -> 3
        PassReminderKind.MORNING_OF -> 2
        PassReminderKind.DAY_BEFORE -> 1
    }
