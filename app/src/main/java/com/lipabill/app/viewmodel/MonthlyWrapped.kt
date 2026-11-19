package com.lipabill.app.viewmodel

import com.lipabill.app.data.model.MpesaTransaction
import com.lipabill.app.data.model.TransactionType
import com.lipabill.app.engage.SpendCategory
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** A month told as story cards: what went out, where, the quiet stretch, and fees. */
data class MonthlyWrapped(
    val month: YearMonth,
    val spent: Double,
    /** Previous month's spend, when the ledger covers it. */
    val spentBefore: Double?,
    val topMerchant: TopMerchant?,
    val biggestCategory: SpendCategory?,
    val biggestCategoryShare: Int,
    val quietestWeekStart: LocalDate?,
    val quietestWeekSpent: Double,
    val noSpendDays: Int,
    val fees: Double,
    val feesBefore: Double?
) {
    data class TopMerchant(val name: String, val visits: Int, val total: Double)

    /** Percent change in spend against the month before; null without one. */
    val spendChange: Int?
        get() = spentBefore?.takeIf { it >= 1.0 }?.let { (((spent - it) / it) * 100).toInt() }
}

private fun MpesaTransaction.date(zone: ZoneId): LocalDate =
    Instant.ofEpochMilli(timestampMillis).atZone(zone).toLocalDate()

/** The month Wrapped looks back on: last month (the current one is not over yet). */
fun wrappedMonth(today: LocalDate): YearMonth = YearMonth.from(today).minusMonths(1)

fun buildWrapped(
    month: YearMonth,
    ledger: List<MpesaTransaction>,
    overrides: Map<String, String>,
    zone: ZoneId = ZoneId.systemDefault()
): MonthlyWrapped? {
    val txs = withoutReversals(ledger)
    val inMonth = txs.filter { YearMonth.from(it.date(zone)) == month }
    if (inMonth.isEmpty()) return null
    val before = month.minusMonths(1)
    val ledgerStart = txs.minOfOrNull { it.date(zone) }
    val coversBefore = ledgerStart != null && !ledgerStart.isAfter(before.atDay(1))
    val prior = txs.filter { YearMonth.from(it.date(zone)) == before }

    val byCategory = spendByCategory(inMonth, overrides)
    val spent = byCategory.values.sum()
    val biggest = byCategory.maxByOrNull { it.value }

    val merchants = inMonth
        .filter { (it.type == TransactionType.BUY_GOODS || it.type == TransactionType.PAYBILL) && (it.amount ?: 0.0) > 0.0 }
        .groupBy { payeeKey(it) }
        .filterKeys { it != null }
        .map { (_, visits) ->
            MonthlyWrapped.TopMerchant(
                name = payeeName(visits.last()),
                visits = visits.map { it.date(zone) }.distinct().size,
                total = visits.sumOf { it.amount ?: 0.0 }
            )
        }
        .maxWithOrNull(compareBy<MonthlyWrapped.TopMerchant> { it.visits }.thenBy { it.total })

    // Spend per day, then the Monday-to-Sunday week (at least 5 days inside the month) with the least.
    val spendDays = inMonth
        .filter { it.type.isOutgoing() && it.type != TransactionType.SAVINGS && it.type != TransactionType.FULIZA }
        .groupBy { it.date(zone) }
        .mapValues { (_, list) -> list.sumOf { it.amount ?: 0.0 } }
    val firstMonday = month.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val weeks = generateSequence(firstMonday) { it.plusWeeks(1) }
        .takeWhile { !it.isAfter(month.atEndOfMonth()) }
        .mapNotNull { start ->
            val days = (0L until 7L).map { start.plusDays(it) }.filter { YearMonth.from(it) == month }
            if (days.size < 5) null else start to days.sumOf { spendDays[it] ?: 0.0 }
        }
        .toList()
    val quietest = weeks.minByOrNull { it.second }
    val covered = (1..month.lengthOfMonth()).map { month.atDay(it) }
        .filter { ledgerStart == null || !it.isBefore(ledgerStart) }

    return MonthlyWrapped(
        month = month,
        spent = spent,
        spentBefore = if (coversBefore) spendByCategory(prior, overrides).values.sum() else null,
        topMerchant = merchants,
        biggestCategory = biggest?.key,
        biggestCategoryShare = if (biggest != null && spent > 0.0) ((biggest.value / spent) * 100).toInt() else 0,
        quietestWeekStart = quietest?.first,
        quietestWeekSpent = quietest?.second ?: 0.0,
        noSpendDays = covered.count { (spendDays[it] ?: 0.0) < 1.0 },
        fees = feeSummary(inMonth).total,
        feesBefore = if (coversBefore) feeSummary(prior).total else null
    )
}
