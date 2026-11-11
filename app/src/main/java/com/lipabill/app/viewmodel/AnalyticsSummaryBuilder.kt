package com.lipabill.app.viewmodel

import com.lipabill.app.data.model.MpesaTransaction
import com.lipabill.app.data.model.TransactionType
import com.lipabill.app.ui.util.displayLabel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class AnalyticsRangeState(
    val range: AnalyticsRange,
    val year: Int?,
    val customStart: LocalDate?,
    val customEnd: LocalDate?
)

internal fun buildAnalyticsSummary(
    ledger: List<MpesaTransaction>,
    rangeState: AnalyticsRangeState,
    account: WalletAccount,
    marks: InsightMarks
): AnalyticsSummary {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val mpesa = account == WalletAccount.MPESA
    // A reversed payment and its reversal cancel out: neither is spend nor income.
    val txs = if (mpesa) withoutReversals(ledger) else ledger
    val availableYears = txs
        .map { Instant.ofEpochMilli(it.timestampMillis).atZone(zone).year }
        .distinct()
        .sortedDescending()

    val selectedYear = rangeState.year
        ?: availableYears.firstOrNull()
        ?: today.year

    val (from, to, label) = resolveBounds(
        range = rangeState.range,
        today = today,
        year = selectedYear,
        customStart = rangeState.customStart,
        customEnd = rangeState.customEnd
    )

    val filtered = txs.filter { tx ->
        val date = Instant.ofEpochMilli(tx.timestampMillis).atZone(zone).toLocalDate()
        !date.isBefore(from) && !date.isAfter(to)
    }

    val (income, expense) = flowTotals(filtered, account)
    val prior = previousAnalyticsBounds(rangeState.range, from, to)
    val previousTxs = if (prior == null) {
        emptyList()
    } else {
        val (prevFrom, prevTo) = prior
        txs.filter { tx ->
            val date = Instant.ofEpochMilli(tx.timestampMillis).atZone(zone).toLocalDate()
            !date.isBefore(prevFrom) && !date.isAfter(prevTo)
        }
    }
    val ledgerStart = txs.minOfOrNull {
        Instant.ofEpochMilli(it.timestampMillis).atZone(zone).toLocalDate()
    }
    // Compare only when the ledger covers the whole stretch before; a half-recorded
    // prior period makes every "+300% vs prior" and "kept more" wrong.
    val comparable = prior != null && ledgerStart != null && !ledgerStart.isAfter(prior.first)
    val (prevIncome, prevExpense) = if (prior == null) {
        0.0 to 0.0
    } else {
        flowTotals(previousTxs, account)
    }

    val byType = filtered
        .groupBy { it.type }
        .map { (type, list) ->
            TypeBreakdown(
                type = type,
                label = type.displayLabel(),
                count = list.size,
                total = list.sumOf { it.amount ?: 0.0 }
            )
        }
        .sortedByDescending { it.total }

    val counterparties = if (account == WalletAccount.MPESA) {
        topOutgoingCounterparties(filtered)
    } else {
        emptyList()
    }
    val (inflowByDate, outflowByDate) = flowByDate(txs, zone, account)
    val outflowDates = outflowByDate.filter { it.value > 0.005 }.keys
    val dayCount = ChronoUnit.DAYS.between(from, to) + 1
    // Days before the first text are unknown, not quiet.
    val coveredFrom = if (ledgerStart != null && ledgerStart.isAfter(from)) ledgerStart else from
    val quietDays = if (ledgerStart == null || ledgerStart.isAfter(to)) {
        0
    } else {
        quietDaysInRange(coveredFrom, to, outflowDates)
    }
    val priorQuietDays = prior?.takeIf { comparable }?.let { (prevFrom, prevTo) ->
        quietDaysInRange(prevFrom, prevTo, outflowDates)
    }
    val quietStreak = if (txs.isNotEmpty()) {
        quietStreakEnding(to, outflowDates, ledgerStart)
    } else {
        0
    }
    val busiest = busiestOutflow(outflowByDate, from, to)
    val fees = if (mpesa) feeSummary(filtered) else FeeSummary()
    val coveredDays = (ChronoUnit.DAYS.between(coveredFrom, to) + 1).coerceAtLeast(1)
    val regulars = if (mpesa) {
        detectRegulars(txs, zone, today, marks.essential, marks.watched)
    } else {
        emptyList()
    }
    val habit = if (mpesa) {
        topHabit(
            filtered,
            zone,
            exclude = regulars.mapTo(HashSet()) { it.key } + marks.essential,
            minVisits = habitMinVisits(dayCount)
        )
    } else {
        null
    }
    val kept = if (mpesa) {
        keptHeadline(
            net = income - expense,
            previousNet = prevIncome - prevExpense,
            hasPrior = comparable
        )
    } else {
        null
    }
    val top = counterparties.firstOrNull()
    val topShare = if (top != null && expense > 0.0) top.total / expense else null
    val story = moneyStory(
        fuliza = account == WalletAccount.FULIZA,
        hasLedger = txs.isNotEmpty(),
        quietDays = quietDays,
        quietStreak = quietStreak,
        priorQuietDays = priorQuietDays,
        expense = expense,
        income = income,
        previousExpense = prevExpense,
        comparable = comparable,
        topName = top?.name,
        topShare = topShare,
        busiestLabel = busiest?.let { busiestDayLabel(it.first) },
        endsToday = to == today
    )

    return AnalyticsSummary(
        incomeTotal = income,
        expenseTotal = expense,
        dayCount = dayCount,
        byType = byType,
        topCounterparties = counterparties,
        txnCount = filtered.size,
        range = rangeState.range,
        selectedYear = if (rangeState.range == AnalyticsRange.YEAR) selectedYear else rangeState.year,
        availableYears = availableYears,
        customStart = rangeState.customStart,
        customEnd = rangeState.customEnd,
        periodFrom = from,
        periodTo = to,
        rangeLabel = label,
        hasLedger = txs.isNotEmpty(),
        storyHeadline = story.headline,
        storyDetail = story.detail,
        quietDays = quietDays,
        quietStreak = quietStreak,
        busiestLabel = busiest?.let { busiestDayLabel(it.first) },
        busiestTotal = busiest?.second ?: 0.0,
        pulse = pulseCalendar(
            from = from,
            to = to,
            today = today,
            inflowByDate = inflowByDate,
            outflowByDate = outflowByDate,
            regulars = regulars
        ),
        keptHeadline = kept,
        fees = fees,
        regulars = regulars,
        habit = habit,
        periodTransactions = ledger.filter { tx ->
            val date = Instant.ofEpochMilli(tx.timestampMillis).atZone(zone).toLocalDate()
            !date.isBefore(from) && !date.isAfter(to)
        },
        heroLabel = buildString {
            append(formatPeriodSpan(from, to))
            append(" · ")
            append(coveredDays)
            append(if (coveredDays == 1L) " day" else " days")
        },
        badges = if (mpesa) {
            insightBadges(
                fees = fees.total,
                previousFees = feeSummary(previousTxs).total,
                hasPrior = comparable,
                quietStreak = quietStreak,
                regulars = regulars,
                earned = marks.earnedBadges
            )
        } else {
            emptyList()
        },
        periodBars = periodBars(coveredFrom, to, inflowByDate, outflowByDate)
    )
}

private fun resolveBounds(
    range: AnalyticsRange,
    today: LocalDate,
    year: Int,
    customStart: LocalDate?,
    customEnd: LocalDate?
): Triple<LocalDate, LocalDate, String> = when (range) {
    AnalyticsRange.DAYS_7 -> {
        val from = today.minusDays(6)
        Triple(from, today, "Last 7 days · ${formatPeriodSpan(from, today)}")
    }
    AnalyticsRange.DAYS_30 -> {
        val from = today.minusDays(29)
        Triple(from, today, "Last 30 days · ${formatPeriodSpan(from, today)}")
    }
    AnalyticsRange.DAYS_90 -> {
        val from = today.minusDays(89)
        Triple(from, today, "Last 90 days · ${formatPeriodSpan(from, today)}")
    }
    AnalyticsRange.THIS_YEAR -> Triple(
        LocalDate.of(today.year, 1, 1),
        today,
        "Year ${today.year}"
    )
    AnalyticsRange.YEAR -> Triple(
        LocalDate.of(year, 1, 1),
        LocalDate.of(year, 12, 31).coerceAtMost(today),
        "Year $year"
    )
    AnalyticsRange.CUSTOM -> {
        val start = customStart ?: today.minusDays(29)
        val end = customEnd ?: today
        Triple(start, end, formatPeriodSpan(start, end))
    }
    AnalyticsRange.ALL -> Triple(LocalDate.of(2010, 1, 1), today, "All time")
}

private fun flowByDate(
    txs: List<MpesaTransaction>,
    zone: ZoneId,
    account: WalletAccount
): Pair<Map<LocalDate, Double>, Map<LocalDate, Double>> {
    val inflow = mutableMapOf<LocalDate, Double>()
    val outflow = mutableMapOf<LocalDate, Double>()
    for (tx in txs) {
        val date = Instant.ofEpochMilli(tx.timestampMillis).atZone(zone).toLocalDate()
        if (account == WalletAccount.FULIZA) {
            outflow[date] = (outflow[date] ?: 0.0) + (tx.amount ?: 0.0)
            continue
        }
        val amount = tx.amount ?: continue
        // Same rule as flowTotals, so the calendar, quiet days and month bars match the totals.
        if (isOwnMoneyMove(tx)) continue
        if (tx.type.isOutgoing()) {
            outflow[date] = (outflow[date] ?: 0.0) + amount + (tx.cost ?: 0.0)
        } else {
            inflow[date] = (inflow[date] ?: 0.0) + amount
        }
    }
    return inflow to outflow
}

/**
 * Money: in vs out across the enabled wallets and banks.
 * Fuliza: amount drawn vs access fees. Outstanding stays on the balance, not in these totals.
 */
private fun flowTotals(
    txs: Iterable<MpesaTransaction>,
    account: WalletAccount
): Pair<Double, Double> {
    if (account == WalletAccount.FULIZA) {
        var drawn = 0.0
        var fees = 0.0
        for (tx in txs) {
            drawn += tx.amount ?: 0.0
            fees += tx.cost ?: 0.0
        }
        return drawn to fees
    }
    var income = 0.0
    var expense = 0.0
    for (tx in txs) {
        val amount = tx.amount ?: continue
        // Savings in and out, and Fuliza draws, are the user's own money moving.
        if (isOwnMoneyMove(tx)) continue
        // Fees left the wallet too, so they count as money out.
        if (tx.type.isOutgoing()) expense += amount + (tx.cost ?: 0.0) else income += amount
    }
    return income to expense
}

