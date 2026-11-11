package com.lipabill.app.viewmodel

import com.lipabill.app.data.model.MpesaTransaction
import com.lipabill.app.data.model.TransactionType
import java.time.LocalDate
import java.util.Locale

/** What is owed on Fuliza, as of the latest draw message. Daily charges after it are not included. */
data class FulizaStatus(
    val outstanding: Double,
    val dueDate: LocalDate?
)

/** "Total Fuliza M-PESA outstanding amount is Ksh192.97 due on 02/10/26" — day/month/year. */
private val DUE_ON = Regex("due\\s+on\\s+(\\d{1,2})/(\\d{1,2})/(\\d{2,4})", RegexOption.IGNORE_CASE)

private const val BURST_MILLIS = 10L * 60L * 1000L

fun fulizaDueDate(rawBody: String): LocalDate? {
    val match = DUE_ON.find(rawBody) ?: return null
    val (day, month, year) = match.destructured
    val fullYear = year.toInt().let { if (it < 100) 2000 + it else it }
    return runCatching { LocalDate.of(fullYear, month.toInt(), day.toInt()) }.getOrNull()
}

/**
 * Newest outstanding figure. Texts from one burst of draws can arrive out of order,
 * so within a few minutes of the newest text the higher receipt code wins: M-PESA
 * codes count up, and the later draw carries the larger running total.
 */
fun latestFulizaStatus(rows: List<MpesaTransaction>): FulizaStatus? {
    val withOutstanding = rows.filter { it.type == TransactionType.FULIZA && it.balance != null }
    if (withOutstanding.isEmpty()) return null
    val newest = withOutstanding.maxOf { it.timestampMillis }
    val latest = withOutstanding
        .filter { it.timestampMillis >= newest - BURST_MILLIS }
        .maxBy { it.code.uppercase(Locale.US) }
    return FulizaStatus(
        outstanding = latest.balance ?: 0.0,
        dueDate = fulizaDueDate(latest.rawBody)
    )
}

fun fulizaDueCopy(due: LocalDate, today: LocalDate): String = when {
    due.isBefore(today) -> "was due ${busiestDayLabel(due)}"
    due == today -> "due today"
    else -> "due ${busiestDayLabel(due)}"
}
