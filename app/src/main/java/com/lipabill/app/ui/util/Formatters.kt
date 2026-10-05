package com.lipabill.app.ui.util

import com.lipabill.app.data.model.TransactionType
import com.lipabill.app.data.model.notesValue
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateTimeFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm", Locale.UK)

fun formatKes(amount: Double?): String {
    if (amount == null) return "—"
    // NumberFormat is not thread-safe — allocate per call (amounts are infrequent UI work).
    val format = NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }
    return format.format(amount)
}

fun formatTimestamp(millis: Long): String {
    return Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .format(dateTimeFormat)
}

private val eventDateFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMMM, yyyy", Locale.ENGLISH)
private val eventTimeFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

data class EventSchedule(val date: String?, val time: String?)

/** Date on one line, clock time on the other. An end instant extends whichever line it changes. */
fun formatEventSchedule(startMillis: Long?, notes: String?): EventSchedule {
    val end = notes.notesValue("Ends")?.toLongOrNull()
    return EventSchedule(
        date = startMillis?.let { formatEventDateLabel(it, end) },
        time = notes.notesValue("Time") ?: startMillis?.let { formatEventTimeLabel(it, end) }
    )
}

/** Calendar date for a pass. A second day is included when the event crosses midnight. */
private fun formatEventDateLabel(startMillis: Long, endMillis: Long? = null): String {
    val zone = ZoneId.systemDefault()
    val start = Instant.ofEpochMilli(startMillis).atZone(zone)
    val startDate = start.format(eventDateFormat)
    val end = endMillis?.let { Instant.ofEpochMilli(it).atZone(zone) } ?: return startDate
    if (end.toLocalDate() == start.toLocalDate()) return startDate
    return "$startDate – ${end.format(eventDateFormat)}"
}

/**
 * Clock time for a pass. Midnight-only values are dates, so they stay off this line.
 * A later end time is shown on the same line.
 */
private fun formatEventTimeLabel(startMillis: Long, endMillis: Long? = null): String? {
    val zone = ZoneId.systemDefault()
    val start = Instant.ofEpochMilli(startMillis).atZone(zone)
    val end = endMillis?.let { Instant.ofEpochMilli(it).atZone(zone) }
    val startText = start.format(eventTimeFormat)
    val endText = end?.format(eventTimeFormat)
    val startHasTime = start.toLocalTime() != LocalTime.MIDNIGHT
    val endHasTime = end != null && end.toLocalTime() != LocalTime.MIDNIGHT
    if (!startHasTime && !endHasTime) return null
    if (endText == null || endText == startText) return startText
    return "$startText – $endText"
}

/**
 * Event start for tickets: "Tomorrow · 7:00 pm", "Sat, 1 Oct · 7:00 pm", or date-only if midnight.
 */
fun formatEventWhen(millis: Long): String {
    val zone = ZoneId.systemDefault()
    val zoned = Instant.ofEpochMilli(millis).atZone(zone)
    val date = zoned.toLocalDate()
    val today = LocalDate.now(zone)
    val timePart = zoned.toLocalTime()
    val hasTime = timePart.hour != 0 || timePart.minute != 0 || timePart.second != 0
    val time = if (hasTime) {
        " · " + zoned.format(DateTimeFormatter.ofPattern("h:mm a", Locale.UK))
    } else {
        ""
    }
    val day = when (date) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        today.minusDays(1) -> "Yesterday"
        else -> {
            val pattern = if (date.year == today.year) "EEE, d MMM" else "EEE, d MMM yyyy"
            date.format(DateTimeFormatter.ofPattern(pattern, Locale.UK))
        }
    }
    return day + time
}

/** Home activity row style: "Today, 9:30 am" */
fun formatActivityTime(millis: Long): String {
    val zone = ZoneId.systemDefault()
    val zoned = Instant.ofEpochMilli(millis).atZone(zone)
    val date = zoned.toLocalDate()
    val today = LocalDate.now(zone)
    val time = zoned.format(DateTimeFormatter.ofPattern("h:mm a", Locale.UK))
    val day = when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(DateTimeFormatter.ofPattern("d MMM", Locale.UK))
    }
    return "$day, $time"
}

fun TransactionType.displayLabel(): String = when (this) {
    TransactionType.SENT -> "Sent"
    TransactionType.RECEIVED -> "Received"
    TransactionType.PAYBILL -> "Paybill"
    TransactionType.BUY_GOODS -> "Buy Goods"
    TransactionType.POCHI -> "Pochi La Biashara"
    TransactionType.WITHDRAW -> "Withdraw"
    TransactionType.DEPOSIT -> "Deposit"
    TransactionType.REVERSED -> "Reversed"
    TransactionType.FULIZA -> "Fuliza"
    TransactionType.UNKNOWN -> "Unknown"
}
