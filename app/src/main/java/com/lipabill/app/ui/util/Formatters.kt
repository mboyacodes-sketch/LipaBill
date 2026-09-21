package com.lipabill.app.ui.util

import com.lipabill.app.data.model.TransactionType
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val kesFormat: NumberFormat = NumberFormat.getNumberInstance(Locale.US).apply {
    minimumFractionDigits = 2
    maximumFractionDigits = 2
}

private val dateTimeFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm", Locale.UK)

fun formatKes(amount: Double?): String {
    if (amount == null) return "—"
    return kesFormat.format(amount)
}

fun formatTimestamp(millis: Long): String {
    return Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .format(dateTimeFormat)
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
    TransactionType.UNKNOWN -> "Unknown"
}

fun TransactionType.isOutgoing(): Boolean = when (this) {
    TransactionType.SENT,
    TransactionType.PAYBILL,
    TransactionType.BUY_GOODS,
    TransactionType.POCHI,
    TransactionType.WITHDRAW -> true
    TransactionType.RECEIVED,
    TransactionType.DEPOSIT -> false
    TransactionType.UNKNOWN -> true
}
