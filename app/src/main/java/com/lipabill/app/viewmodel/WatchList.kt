package com.lipabill.app.viewmodel

import com.lipabill.app.data.model.MpesaTransaction
import com.lipabill.app.data.model.TransactionType
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * What changed in money that repeats: a regular bill that went up (or down), a new recurring
 * charge, one that stopped, and how the price of a usual shop has moved over time.
 */

sealed class RegularChange {
    abstract val name: String

    /** The latest payment differs from what this payee usually costs. */
    data class PriceChanged(override val name: String, val usual: Double, val latest: Double) : RegularChange() {
        val difference: Double get() = latest - usual
    }

    /** Became regular in the last few months. */
    data class NewCharge(override val name: String, val amount: Double, val cadence: Cadence) : RegularChange()

    /** Was regular, and the payment has not come round again. */
    data class Stopped(override val name: String, val lastPaid: LocalDate, val amount: Double) : RegularChange()
}

/** Average spend per visit at a merchant, now against its earlier visits. */
data class PriceTrend(
    val name: String,
    val recentAverage: Double,
    val earlierAverage: Double,
    val since: YearMonth
) {
    val percent: Int get() = (((recentAverage - earlierAverage) / earlierAverage) * 100).roundToInt()
}

private const val LOOKBACK_DAYS = 60L
private const val NEW_WITHIN_DAYS = 120L
private const val MIN_CHANGE_SHILLINGS = 50.0
private const val MIN_CHANGE_SHARE = 0.05

private fun MpesaTransaction.date(zone: ZoneId): LocalDate =
    Instant.ofEpochMilli(timestampMillis).atZone(zone).toLocalDate()

private fun List<Double>.middle(): Double = sorted().let { if (it.isEmpty()) 0.0 else it[it.size / 2] }

fun regularChanges(
    txs: List<MpesaTransaction>,
    today: LocalDate,
    zone: ZoneId = ZoneId.systemDefault()
): List<RegularChange> {
    val now = detectRegulars(txs, zone, today)
    val thenDay = today.minusDays(LOOKBACK_DAYS)
    val then = detectRegulars(txs.filter { !it.date(zone).isAfter(thenDay) }, zone, thenDay)
    val nowKeys = now.mapTo(HashSet()) { it.key }
    val thenKeys = then.mapTo(HashSet()) { it.key }
    val byPayee = txs.filter { (it.amount ?: 0.0) > 0.0 }.groupBy { payeeKey(it) }

    val changes = mutableListOf<RegularChange>()
    for (regular in now) {
        val payments = byPayee[regular.key].orEmpty()
            .groupBy { it.date(zone) }
            .mapValues { (_, list) -> list.sumOf { it.amount ?: 0.0 } }
            .toSortedMap()
            .values.toList()
        if (payments.size >= 3) {
            val latest = payments.last()
            val usual = payments.dropLast(1).middle()
            val diff = latest - usual
            if (abs(diff) >= MIN_CHANGE_SHILLINGS && abs(diff) >= usual * MIN_CHANGE_SHARE) {
                changes += RegularChange.PriceChanged(regular.name, usual, latest)
                continue
            }
        }
        val first = byPayee[regular.key].orEmpty().minOfOrNull { it.date(zone) }
        if (regular.key !in thenKeys && first != null && ChronoUnit.DAYS.between(first, today) <= NEW_WITHIN_DAYS) {
            changes += RegularChange.NewCharge(regular.name, regular.typicalAmount, regular.cadence)
        }
    }
    for (old in then) {
        if (old.key in nowKeys) continue
        val paidSince = byPayee[old.key].orEmpty().any { it.date(zone).isAfter(thenDay) }
        if (!paidSince) changes += RegularChange.Stopped(old.name, old.lastPaid, old.typicalAmount)
    }
    // Rises first (they cost money), then new charges, then stops.
    return changes.sortedBy {
        when (it) {
            is RegularChange.PriceChanged -> if (it.difference > 0) 0 else 3
            is RegularChange.NewCharge -> 1
            is RegularChange.Stopped -> 2
        }
    }
}

/**
 * Merchants you pay often (tills and paybills, 6+ visits over 3+ months): the average visit in
 * the last 60 days against the visits before. Moves under 5% are left out.
 */
fun priceTrends(
    txs: List<MpesaTransaction>,
    today: LocalDate,
    zone: ZoneId = ZoneId.systemDefault(),
    limit: Int = 4
): List<PriceTrend> {
    val cutoff = today.minusDays(LOOKBACK_DAYS)
    return txs
        .filter { (it.type == TransactionType.BUY_GOODS || it.type == TransactionType.PAYBILL) && (it.amount ?: 0.0) > 0.0 }
        .groupBy { payeeKey(it) }
        .mapNotNull { (key, visits) ->
            if (key == null || visits.size < 6) return@mapNotNull null
            val dated = visits.sortedBy { it.timestampMillis }
            val first = dated.first().date(zone)
            if (ChronoUnit.DAYS.between(first, today) < 90) return@mapNotNull null
            val recent = dated.filter { it.date(zone).isAfter(cutoff) }.mapNotNull { it.amount }
            val earlier = dated.filter { !it.date(zone).isAfter(cutoff) }.mapNotNull { it.amount }
            if (recent.size < 2 || earlier.size < 3) return@mapNotNull null
            val trend = PriceTrend(
                name = payeeName(dated.last { !it.counterpartyName.isNullOrBlank() || it == dated.last() }),
                recentAverage = recent.average(),
                earlierAverage = earlier.average(),
                since = YearMonth.from(first)
            )
            trend.takeIf { abs(it.recentAverage - it.earlierAverage) >= it.earlierAverage * MIN_CHANGE_SHARE }
        }
        .sortedByDescending { abs(it.percent) }
        .take(limit)
}
