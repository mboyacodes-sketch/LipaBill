package com.lipabill.app.viewmodel

import com.lipabill.app.data.model.MpesaTransaction
import com.lipabill.app.data.model.TransactionType
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

private val periodDayMonth: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")
private val periodDayMonthYear: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

private fun orderedSpanEnds(from: LocalDate, to: LocalDate): Pair<LocalDate, LocalDate> =
    if (from.isAfter(to)) to to from else from to to

data class CounterpartySpend(
    val name: String,
    val count: Int,
    val total: Double,
    val lastMillis: Long = 0L
)

/** Same-length window immediately before [from]–[to], or the prior calendar year. */
fun previousAnalyticsBounds(
    range: AnalyticsRange,
    from: LocalDate,
    to: LocalDate
): Pair<LocalDate, LocalDate>? = when (range) {
    AnalyticsRange.ALL -> null
    AnalyticsRange.THIS_YEAR,
    AnalyticsRange.YEAR -> from.minusYears(1) to to.minusYears(1)
    else -> {
        val span = ChronoUnit.DAYS.between(from, to)
        val prevTo = from.minusDays(1)
        prevTo.minusDays(span) to prevTo
    }
}

fun percentChange(current: Double, previous: Double): Int? {
    if (previous == 0.0) return null
    return ((current - previous) / previous * 100.0).roundToInt()
}

fun vsPriorCopy(current: Double, previous: Double, comparable: Boolean): String? {
    if (!comparable) return null
    if (previous == 0.0 && current == 0.0) return "Same as prior"
    if (previous == 0.0) return "New vs prior"
    val pct = percentChange(current, previous) ?: return null
    return when {
        pct > 0 -> "+$pct% vs prior"
        pct < 0 -> "$pct% vs prior"
        else -> "Same as prior"
    }
}

fun dailyAverage(total: Double, dayCount: Long): Double {
    if (dayCount <= 0L) return 0.0
    return total / dayCount
}

/** Outflow as a share of inflow. Null when there is no income to compare. */
fun spendShareOfIncome(expense: Double, income: Double): Double? {
    if (income <= 0.0) return null
    return expense / income
}

/**
 * Who got the most money out of the wallet. Cash withdrawals and moves into savings are
 * the user's own money and left out, as are rows with no name or number to show.
 */
fun topOutgoingCounterparties(
    txs: Iterable<MpesaTransaction>,
    limit: Int = 5
): List<CounterpartySpend> =
    txs
        .filter { tx ->
            tx.type.isOutgoing() &&
                tx.type != TransactionType.SAVINGS &&
                tx.type != TransactionType.WITHDRAW &&
                (tx.amount ?: 0.0) > 0.0
        }
        .groupBy { payeeKey(it) }
        .filterKeys { it != null }
        .map { (_, list) ->
            val latest = list.maxBy { it.timestampMillis }
            CounterpartySpend(
                name = latest.counterpartyName?.trim()?.takeIf { it.isNotEmpty() }
                    ?: latest.counterpartyPhone?.trim().orEmpty(),
                count = list.size,
                total = list.sumOf { it.amount ?: 0.0 },
                lastMillis = latest.timestampMillis
            )
        }
        .sortedByDescending { it.total }
        .take(limit)

data class MoneyStory(
    val headline: String,
    val detail: String?
)

data class PulseCell(
    val date: LocalDate,
    val inRange: Boolean,
    val isToday: Boolean,
    val inflow: Double,
    val outflow: Double,
    /** Regular payees expected to be paid this day. */
    val due: List<RegularPayee> = emptyList()
) {
    val isDue: Boolean get() = due.isNotEmpty()
    val hasOutflow: Boolean get() = outflow > 0.005
    val hasInflow: Boolean get() = inflow > 0.005
    val net: Double get() = inflow - outflow
    val flow: DayFlow
        get() = when {
            hasInflow && hasOutflow -> if (net >= 0.0) DayFlow.NET_UP else DayFlow.NET_DOWN
            hasInflow -> DayFlow.IN_ONLY
            hasOutflow -> DayFlow.OUT_ONLY
            else -> DayFlow.NONE
        }
}

/** How money moved on one day: one way only, or both ways with the net up or down. */
enum class DayFlow { NONE, IN_ONLY, OUT_ONLY, NET_UP, NET_DOWN }

fun quietDaysInRange(from: LocalDate, to: LocalDate, outflowDates: Set<LocalDate>): Int {
    val days = ChronoUnit.DAYS.between(from, to) + 1
    if (days <= 0L) return 0
    val noisy = outflowDates.count { date -> !date.isBefore(from) && !date.isAfter(to) }
    return (days - noisy).toInt().coerceAtLeast(0)
}

/**
 * Consecutive days with no money out, ending on [end].
 * Looks at the whole ledger so a 7-day view can still show a longer run.
 */
fun quietStreakEnding(
    end: LocalDate,
    outflowDates: Set<LocalDate>,
    ledgerStart: LocalDate?
): Int {
    val lastOut = outflowDates.filter { !it.isAfter(end) }.maxOrNull()
    val from = when {
        lastOut != null -> lastOut.plusDays(1)
        ledgerStart != null -> ledgerStart
        else -> return 0
    }
    if (from.isAfter(end)) return 0
    return (ChronoUnit.DAYS.between(from, end) + 1).toInt()
}

fun busiestOutflow(
    outflowByDate: Map<LocalDate, Double>,
    from: LocalDate,
    to: LocalDate
): Pair<LocalDate, Double>? =
    outflowByDate
        .filter { (date, amount) ->
            amount > 0.005 && !date.isBefore(from) && !date.isAfter(to)
        }
        .maxByOrNull { it.value }
        ?.toPair()

fun busiestDayLabel(date: LocalDate): String = date.format(periodDayMonth)

/**
 * Inclusive calendar span with months named so Insights never relies on “this month”
 * being obvious. Same month: `1–10 Nov 2026`. Across months: `12 Oct – 10 Nov 2026`.
 */
fun formatPeriodSpan(from: LocalDate, to: LocalDate): String {
    val (start, end) = orderedSpanEnds(from, to)
    return when {
        start == end -> start.format(periodDayMonthYear)
        start.year == end.year && start.month == end.month ->
            "${start.dayOfMonth}–${end.format(periodDayMonthYear)}"
        start.year == end.year ->
            "${start.format(periodDayMonth)} – ${end.format(periodDayMonthYear)}"
        else ->
            "${start.format(periodDayMonthYear)} – ${end.format(periodDayMonthYear)}"
    }
}

/** Compact span for the period pill when the full “Last N days · …” label is too long. */
fun shortPeriodSpan(from: LocalDate, to: LocalDate): String {
    val (start, end) = orderedSpanEnds(from, to)
    return when {
        start == end -> start.format(periodDayMonth)
        start.year == end.year && start.month == end.month ->
            "${start.dayOfMonth}–${end.format(periodDayMonth)}"
        start.year == end.year ->
            "${start.format(periodDayMonth)} – ${end.format(periodDayMonth)}"
        else ->
            "${start.format(periodDayMonthYear)} – ${end.format(periodDayMonthYear)}"
    }
}

/** Compact fare-style figure for a calendar cell. Empty when nothing moved. */
fun calendarAmountLabel(amount: Double): String {
    if (amount <= 0.005) return ""
    return when {
        amount >= 1_000_000 -> String.format(Locale.US, "%.1fM", amount / 1_000_000.0)
        amount >= 10_000 -> String.format(Locale.US, "%.0fk", amount / 1_000.0)
        else -> NumberFormat.getIntegerInstance(Locale.US).format(amount.roundToInt().toLong())
    }
}

/**
 * Regular payments expected from today to [days] ahead, plus ones already past their usual
 * date and not seen yet (a regular that stopped altogether drops out of [regulars] on its own).
 * Soonest first.
 */
fun upcomingDue(regulars: List<RegularPayee>, today: LocalDate, days: Long = 14): List<RegularPayee> =
    regulars
        .filter { !it.nextDue.isAfter(today.plusDays(days)) }
        .sortedWith(compareBy<RegularPayee> { it.nextDue }.thenByDescending { it.typicalAmount })

private val dueDayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK)

/** "Due today", "Due tomorrow", "Due Fri 14 Nov", or "Was due 8 Nov, not paid yet". */
fun dueCopy(due: LocalDate, today: LocalDate): String = when {
    due.isBefore(today) -> "Was due ${busiestDayLabel(due)}, not paid yet"
    due == today -> "Due today"
    due == today.plusDays(1) -> "Due tomorrow"
    else -> "Due ${due.format(dueDayFormat)}"
}

fun shouldShowPulse(dayCount: Long): Boolean = dayCount in 1L..31L

fun pulseCalendar(
    from: LocalDate,
    to: LocalDate,
    today: LocalDate,
    inflowByDate: Map<LocalDate, Double>,
    outflowByDate: Map<LocalDate, Double>,
    regulars: List<RegularPayee> = emptyList()
): List<PulseCell> {
    if (!shouldShowPulse(ChronoUnit.DAYS.between(from, to) + 1)) return emptyList()
    val dueByDate = regulars.groupBy { it.nextDue }
    val start = from.with(DayOfWeek.MONDAY)
    val end = to.with(DayOfWeek.SUNDAY)
    return generateSequence(start) { date ->
        val next = date.plusDays(1)
        if (next.isAfter(end)) null else next
    }.map { date ->
        PulseCell(
            date = date,
            inRange = !date.isBefore(from) && !date.isAfter(to),
            isToday = date == today,
            inflow = inflowByDate[date] ?: 0.0,
            outflow = outflowByDate[date] ?: 0.0,
            due = dueByDate[date].orEmpty()
        )
    }.toList()
}

fun moneyStory(
    fuliza: Boolean,
    hasLedger: Boolean,
    quietDays: Int,
    quietStreak: Int,
    priorQuietDays: Int?,
    expense: Double,
    income: Double,
    previousExpense: Double,
    comparable: Boolean,
    topName: String?,
    topShare: Double?,
    busiestLabel: String?,
    endsToday: Boolean
): MoneyStory {
    if (!hasLedger) {
        return MoneyStory(
            headline = "The picture fills from your texts.",
            detail = "Once confirmations land, this stretch gets a shape."
        )
    }
    val through = if (endsToday) "through today" else "through the end of this stretch"
    if (fuliza) {
        return fulizaStory(
            quietStreak = quietStreak,
            through = through,
            expense = expense,
            previousExpense = previousExpense,
            comparable = comparable
        )
    }
    if (quietStreak >= 3) {
        return MoneyStory(
            headline = "$quietStreak quiet days in a row.",
            detail = "No money out $through."
        )
    }
    if (quietStreak == 2) {
        return MoneyStory(
            headline = "Two quiet days in a row.",
            detail = "No money out $through."
        )
    }
    if (comparable && priorQuietDays != null && quietDays > priorQuietDays + 1) {
        return MoneyStory(
            headline = "A quieter stretch than last time.",
            detail = "$quietDays days with no money out."
        )
    }
    val share = spendShareOfIncome(expense, income)
    if (share != null && share > 1.05) {
        return MoneyStory(
            headline = "Outflow ran ahead of what came in.",
            detail = "Spent ${(share * 100).roundToInt()}% of income this stretch."
        )
    }
    if (share != null && share <= 0.55) {
        return MoneyStory(
            headline = "Most of what came in is still here.",
            detail = "Spent ${(share * 100).roundToInt()}% of income this stretch."
        )
    }
    if (topName != null && topShare != null && topShare >= 0.4) {
        return MoneyStory(
            headline = "$topName shaped this stretch.",
            detail = "The largest share of what went out."
        )
    }
    if (comparable && previousExpense > 0.0 && expense < previousExpense * 0.9) {
        return MoneyStory(
            headline = "Spending is lighter than last time.",
            detail = vsPriorCopy(expense, previousExpense, comparable = true)
        )
    }
    if (comparable && previousExpense > 0.0 && expense > previousExpense * 1.1) {
        return MoneyStory(
            headline = "Spending is heavier than last time.",
            detail = vsPriorCopy(expense, previousExpense, comparable = true)
        )
    }
    if (busiestLabel != null) {
        return MoneyStory(
            headline = "The busiest day was $busiestLabel.",
            detail = "That's when the most money went out."
        )
    }
    return MoneyStory(
        headline = "Here's what shaped this stretch.",
        detail = if (quietDays > 0) "$quietDays days with no money out." else null
    )
}

private fun fulizaStory(
    quietStreak: Int,
    through: String,
    expense: Double,
    previousExpense: Double,
    comparable: Boolean
): MoneyStory {
    if (quietStreak >= 3) {
        return MoneyStory(
            headline = "$quietStreak days without a Fuliza draw.",
            detail = "Quiet $through."
        )
    }
    if (quietStreak == 2) {
        return MoneyStory(
            headline = "Two days without a Fuliza draw.",
            detail = "Quiet $through."
        )
    }
    if (comparable && previousExpense > 0.0 && expense < previousExpense * 0.9) {
        return MoneyStory(
            headline = "Access fees are lighter than last time.",
            detail = vsPriorCopy(expense, previousExpense, comparable = true)
        )
    }
    if (comparable && previousExpense > 0.0 && expense > previousExpense * 1.1) {
        return MoneyStory(
            headline = "Access fees are heavier than last time.",
            detail = vsPriorCopy(expense, previousExpense, comparable = true)
        )
    }
    return MoneyStory(
        headline = "Here's the Fuliza stretch.",
        detail = "Draws and access fees, from the same texts."
    )
}

