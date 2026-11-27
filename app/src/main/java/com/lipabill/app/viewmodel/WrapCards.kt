package com.lipabill.app.viewmodel

import com.lipabill.app.data.model.MpesaTransaction
import com.lipabill.app.data.model.TransactionType
import com.lipabill.app.engage.SpendCategory
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.max

/**
 * Everything the Wrapped story cards show, for any stretch of days: a month or a year.
 * One model so monthly and yearly stories never disagree on the math.
 */
data class WrapCards(
    val from: LocalDate,
    val to: LocalDate,
    /** Days of [from]..[to] the ledger actually covers (it may start partway through). */
    val coveredDays: Int,
    // By the numbers
    val txnCount: Int,
    val activeDays: Int,
    val typeCounts: Map<TransactionType, Int>,
    /** Day with the most transactions, and how many. */
    val busiestDay: Pair<LocalDate, Int>?,
    // The flow
    val moneyIn: Double,
    val moneyOut: Double,
    /** Day the most money went out, and how much. */
    val heaviestOutDay: Pair<LocalDate, Double>?,
    // Quiet cost
    val fees: Double,
    val chargedTxnCount: Int,
    // Fuliza (null when there were no draws, e.g. Airtel- or bank-only ledgers)
    val fuliza: FulizaStat?,
    // People and spots
    val topPeople: List<CounterpartySpend>,
    val topSpots: List<CounterpartySpend>,
    val peopleTotal: Double,
    val biggestCategory: SpendCategory?,
    val biggestCategoryShare: Int,
    // Rhythm
    val bars: List<PeriodBar>,
    val busiestWeekday: DayOfWeek?,
    /** Payments made between midnight and 5 am. */
    val afterMidnight: Int,
    val biggestPayment: BiggestPayment?,
    // Personality
    val personality: WrapPersonality
) {
    data class FulizaStat(val count: Int, val total: Double) {
        val average: Double get() = if (count > 0) total / count else 0.0
    }

    data class BiggestPayment(val name: String, val amount: Double, val date: LocalDate)

    val net: Double get() = moneyIn - moneyOut

    val perDay: Double get() = if (coveredDays > 0) txnCount.toDouble() / coveredDays else 0.0

    /** Fees as a share of everything that went out, 0..1. Null when nothing went out. */
    val feeShareOfOut: Double? get() = if (moneyOut > 0.0) fees / moneyOut else null

    /** Index into [bars] of the heaviest money-out bar, or -1. */
    val peakBar: Int get() = bars.indices.maxByOrNull { bars[it].outflow }?.takeIf { bars[it].outflow > 0.0 } ?: -1

    /** True when the ledger starts after [from], so copy must not claim the whole stretch. */
    val partial: Boolean get() = coveredDays < (ChronoUnit.DAYS.between(from, to) + 1).toInt()
}

/** Money personality ids; titles and lines live in strings.xml as `wrap_personality_<id>`. */
enum class WrapPersonality(val id: String) {
    FULIZA_FREQUENT("fuliza_frequent"),
    FAMILY_ANCHOR("family_anchor"),
    FEE_HEAVY("fee_heavy"),
    QUIET_OPERATOR("quiet_operator"),
    EVERYDAY_MOVER("everyday_mover")
}

private fun MpesaTransaction.date(zone: ZoneId): LocalDate =
    Instant.ofEpochMilli(timestampMillis).atZone(zone).toLocalDate()

private fun MpesaTransaction.hour(zone: ZoneId): Int =
    Instant.ofEpochMilli(timestampMillis).atZone(zone).hour

/** Hours counted as "after midnight": 00:00 to 04:59. */
internal val AFTER_MIDNIGHT_HOURS = 0 until 5

/**
 * Builds the cards for [from]..[to] (inclusive) from [ledger], which should already be
 * limited to the user's enabled rails. Fuliza rows may be mixed in; they feed the Fuliza
 * card only and never count as money in or out. Returns null when nothing happened.
 */
fun buildWrapCards(
    from: LocalDate,
    to: LocalDate,
    ledger: List<MpesaTransaction>,
    overrides: Map<String, String>,
    zone: ZoneId = ZoneId.systemDefault()
): WrapCards? {
    val txs = withoutReversals(ledger)
    val inWindow = txs.filter { val d = it.date(zone); !d.isBefore(from) && !d.isAfter(to) }
    if (inWindow.isEmpty()) return null

    val ledgerStart = txs.minOf { it.date(zone) }
    val coveredFrom = if (ledgerStart.isAfter(from)) ledgerStart else from
    val coveredDays = (ChronoUnit.DAYS.between(coveredFrom, to) + 1).toInt().coerceAtLeast(1)

    val byDate = inWindow.groupBy { it.date(zone) }
    val busiestDay = byDate.maxByOrNull { it.value.size }?.let { it.key to it.value.size }

    val (moneyIn, moneyOut) = flowTotals(inWindow, WalletAccount.MPESA)
    val (inflowByDate, outflowByDate) = flowByDate(inWindow, zone, WalletAccount.MPESA)

    val fees = feeSummary(inWindow).total
    val charged = inWindow.count { it.type.isOutgoing() && (it.cost ?: 0.0) > 0.0 }

    val fulizaRows = inWindow.filter { it.type == TransactionType.FULIZA && (it.amount ?: 0.0) > 0.0 }
    val fuliza = fulizaRows.takeIf { it.isNotEmpty() }
        ?.let { WrapCards.FulizaStat(it.size, it.sumOf { tx -> tx.amount ?: 0.0 }) }

    val split = outgoingPayeeSplit(inWindow, limitPerKind = 5)

    val byCategory = spendByCategory(inWindow, overrides)
    val spent = byCategory.values.sum()
    val biggest = byCategory.maxByOrNull { it.value }

    val spendRows = inWindow.filter {
        countsAsSpendOutflow(it.type, it.counterpartyName, (it.amount ?: 0.0) > 0.0)
    }
    val biggestPayment = spendRows.maxByOrNull { it.amount ?: 0.0 }?.let {
        WrapCards.BiggestPayment(payeeName(it), it.amount ?: 0.0, it.date(zone))
    }

    val cards = WrapCards(
        from = from,
        to = to,
        coveredDays = coveredDays,
        txnCount = inWindow.size,
        activeDays = byDate.size,
        typeCounts = inWindow.groupingBy { it.type }.eachCount(),
        busiestDay = busiestDay,
        moneyIn = moneyIn,
        moneyOut = moneyOut,
        heaviestOutDay = busiestOutflow(outflowByDate, from, to),
        fees = fees,
        chargedTxnCount = charged,
        fuliza = fuliza,
        topPeople = split.people,
        topSpots = split.businesses,
        peopleTotal = split.peopleTotal,
        biggestCategory = biggest?.key,
        biggestCategoryShare = if (biggest != null && spent > 0.0) ((biggest.value / spent) * 100).toInt() else 0,
        bars = wrapBars(from, to, inflowByDate, outflowByDate),
        busiestWeekday = inWindow.groupingBy { it.date(zone).dayOfWeek }.eachCount().maxByOrNull { it.value }?.key,
        afterMidnight = spendRows.count { it.hour(zone) in AFTER_MIDNIGHT_HOURS },
        biggestPayment = biggestPayment,
        personality = WrapPersonality.EVERYDAY_MOVER
    )
    return cards.copy(personality = wrapPersonality(cards))
}

/**
 * Rhythm bars: one per month for stretches longer than a month ([periodBars]),
 * otherwise one per Monday-to-Sunday week clipped to the stretch.
 */
internal fun wrapBars(
    from: LocalDate,
    to: LocalDate,
    inflowByDate: Map<LocalDate, Double>,
    outflowByDate: Map<LocalDate, Double>
): List<PeriodBar> {
    periodBars(from, to, inflowByDate, outflowByDate).takeIf { it.isNotEmpty() }?.let { return it }
    val firstMonday = from.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return generateSequence(firstMonday) { it.plusWeeks(1) }
        .takeWhile { !it.isAfter(to) }
        .mapIndexed { i, start ->
            val end = start.plusDays(6)
            fun inWeek(d: LocalDate) = !d.isBefore(max(start, from)) && !d.isAfter(minOf(end, to))
            PeriodBar(
                label = "W${i + 1}",
                inflow = inflowByDate.filterKeys(::inWeek).values.sum(),
                outflow = outflowByDate.filterKeys(::inWeek).values.sum()
            )
        }
        .toList()
}

private fun max(a: LocalDate, b: LocalDate): LocalDate = if (a.isAfter(b)) a else b

/**
 * First matching rule wins. Thresholds scale with the covered stretch so a month and a
 * year can both earn the same personality.
 */
fun wrapPersonality(c: WrapCards): WrapPersonality {
    val weeks = max(1.0, c.coveredDays / 7.0)
    val fulizaCount = c.fuliza?.count ?: 0
    if (fulizaCount >= 4 && fulizaCount >= weeks * 1.5) return WrapPersonality.FULIZA_FREQUENT

    val topPerson = c.topPeople.firstOrNull()?.total ?: 0.0
    if (c.moneyOut > 0.0 && c.peopleTotal > 0.0 &&
        topPerson / c.peopleTotal >= 0.4 && topPerson / c.moneyOut >= 0.15
    ) return WrapPersonality.FAMILY_ANCHOR

    if ((c.feeShareOfOut ?: 0.0) >= 0.01) return WrapPersonality.FEE_HEAVY

    if (c.activeDays.toDouble() / c.coveredDays < 0.4) return WrapPersonality.QUIET_OPERATOR

    return WrapPersonality.EVERYDAY_MOVER
}
