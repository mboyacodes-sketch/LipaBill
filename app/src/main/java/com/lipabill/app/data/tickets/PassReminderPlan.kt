package com.lipabill.app.data.tickets

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

enum class PassReminderKind {
    /** 6:00 PM on the calendar day before the event. */
    DAY_BEFORE,

    /** Exactly one day before the start time. */
    HOURS_24,

    /** Morning of the event, early enough to still be useful. */
    MORNING_OF
}

data class PlannedPassReminder(
    val kind: PassReminderKind,
    val atMillis: Long
)

private const val DEDUPE_WINDOW_MS = 45L * 60L * 1000L

/**
 * Three reminders for a pass that still has a future start:
 * the evening before, 24 hours before, and the morning of the event.
 * Times already passed are left out. Two that land within 45 minutes
 * collapse to the more specific one.
 */
fun planPassReminders(
    startMillis: Long,
    nowMillis: Long,
    zone: ZoneId
): List<PlannedPassReminder> {
    val start = Instant.ofEpochMilli(startMillis).atZone(zone)
    val slots = buildList {
        morningOf(start)?.let { add(PassReminderKind.MORNING_OF to it) }
        add(PassReminderKind.HOURS_24 to start.minusHours(24))
        val eveningBefore = start.toLocalDate().minusDays(1)
            .atTime(LocalTime.of(18, 0))
            .atZone(zone)
        if (eveningBefore.isBefore(start)) {
            add(PassReminderKind.DAY_BEFORE to eveningBefore)
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
 * 8:00 AM on the event day. Events that start at 9:00 or earlier use 6:00 AM,
 * and only when that is still 45 minutes before the start.
 */
private fun morningOf(start: ZonedDateTime): ZonedDateTime? {
    val hour = if (start.toLocalTime() <= LocalTime.of(9, 0)) 6 else 8
    val slot = start.toLocalDate().atTime(hour, 0).atZone(start.zone)
    return slot.takeIf { slot.isBefore(start.minusMinutes(45)) }
}

private val PassReminderKind.priority: Int
    get() = when (this) {
        PassReminderKind.HOURS_24 -> 3
        PassReminderKind.MORNING_OF -> 2
        PassReminderKind.DAY_BEFORE -> 1
    }
