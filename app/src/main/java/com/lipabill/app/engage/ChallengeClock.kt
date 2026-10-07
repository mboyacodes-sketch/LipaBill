package com.lipabill.app.engage

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.WeekFields

/**
 * A challenge day is a calendar date in the zone saved on the challenge.
 *
 * The window is `[startOfDay, startOfDay + N days)`. The start instant is included
 * and the end instant is excluded. The zone is stored when the challenge starts,
 * so a later change of the device timezone does not move the window or recount days.
 *
 * When the device zone cannot be read, the fallback is Africa/Nairobi.
 */
object ChallengeClock {

    const val NAIROBI = "Africa/Nairobi"
    val fallback: ZoneId = ZoneId.of(NAIROBI)

    fun deviceZone(): ZoneId = runCatching { ZoneId.systemDefault() }.getOrDefault(fallback)

    fun zoneOf(id: String?): ZoneId =
        if (id.isNullOrBlank()) fallback else runCatching { ZoneId.of(id) }.getOrDefault(fallback)

    /** Inclusive start and exclusive end, beginning at the local start of the day that contains [now]. */
    fun windowFrom(now: Long, days: Int, zone: ZoneId): Pair<Long, Long> {
        val startDate = localDate(now, zone)
        val start = startDate.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = startDate.plusDays(days.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
        return start to end
    }

    fun localDate(millis: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    /** 1-based day inside the window. Stays on the last day once [now] has passed [endExclusive]. */
    fun dayNumber(startAt: Long, endExclusive: Long, now: Long, zone: ZoneId): Int {
        val startDate = localDate(startAt, zone)
        val cursor = if (now >= endExclusive) endExclusive - 1 else now
        val date = localDate(cursor.coerceAtLeast(startAt), zone)
        val between = java.time.temporal.ChronoUnit.DAYS.between(startDate, date).toInt()
        return (between + 1).coerceAtLeast(1)
    }

    /** ISO week id, for example `2026-W40`, in [zone]. */
    fun isoWeekId(millis: Long, zone: ZoneId): String {
        val date = localDate(millis, zone)
        val fields = WeekFields.ISO
        val year = date.get(fields.weekBasedYear())
        val week = date.get(fields.weekOfWeekBasedYear())
        return "%d-W%02d".format(year, week)
    }

    /** Monday 00:00 inclusive through the next Monday 00:00 exclusive, in [zone]. */
    fun isoWeekRange(millis: Long, zone: ZoneId): Pair<Long, Long> {
        val monday = localDate(millis, zone).with(DayOfWeek.MONDAY)
        val start = monday.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = monday.plusDays(7).atStartOfDay(zone).toInstant().toEpochMilli()
        return start to end
    }

    fun previousWeekId(weekId: String): String? {
        val monday = mondayOf(weekId) ?: return null
        return isoWeekId(monday.minusWeeks(1).atStartOfDay(fallback).toInstant().toEpochMilli(), fallback)
    }

    private fun mondayOf(weekId: String): LocalDate? {
        val parts = weekId.split("-W")
        if (parts.size != 2) return null
        val year = parts[0].toIntOrNull() ?: return null
        val week = parts[1].toIntOrNull() ?: return null
        return LocalDate.of(year, 6, 1)
            .with(WeekFields.ISO.weekBasedYear(), year.toLong())
            .with(WeekFields.ISO.weekOfWeekBasedYear(), week.toLong())
            .with(DayOfWeek.MONDAY)
    }

    /** Next Sunday at [hour]:00 in [zone], strictly after [now]. */
    fun nextSundayEvening(now: Long, zone: ZoneId, hour: Int = 18): Long {
        val zoned = Instant.ofEpochMilli(now).atZone(zone)
        var sunday = zoned.toLocalDate().with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        var candidate = sunday.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
        if (candidate <= now) {
            sunday = sunday.plusWeeks(1)
            candidate = sunday.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
        }
        return candidate
    }
}
