package com.lipabill.app.viewmodel

import com.lipabill.app.data.model.MpesaTransaction
import com.lipabill.app.data.model.TransactionType
import com.lipabill.app.engage.CategoryMatcher
import com.lipabill.app.engage.CategoryRules
import com.lipabill.app.ui.util.formatKsh
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToLong

/** "original transaction QK12ABC34D in favour of ... has been reversed". */
private val REVERSED_ORIGINAL = Regex(
    "original\\s+transaction\\s+([A-Z0-9]{8,12})",
    RegexOption.IGNORE_CASE
)

/** Code of the payment a reversal undid. Null for anything that is not a reversal. */
fun reversedOriginalCode(tx: MpesaTransaction): String? {
    if (tx.type != TransactionType.REVERSED) return null
    return REVERSED_ORIGINAL.find(tx.rawBody)?.groupValues?.get(1)?.uppercase(Locale.US)
}

/** Codes of payments that a reversal in [txs] undid. */
fun reversedCodes(txs: Iterable<MpesaTransaction>): Set<String> =
    txs.mapNotNullTo(HashSet()) { reversedOriginalCode(it) }

/**
 * Drops reversals and the payments they undid. Money that came back is neither
 * spend nor income, so it must not inflate either side of the picture.
 */
fun withoutReversals(txs: List<MpesaTransaction>): List<MpesaTransaction> {
    val undone = reversedCodes(txs)
    return txs.filter { tx ->
        tx.type != TransactionType.REVERSED && tx.code.uppercase(Locale.US) !in undone
    }
}

/**
 * Money moving between the user's own pots: into M-Shwari / Lock Savings and back out,
 * and Fuliza draws that the payment they funded already counts. None of it is income or spend.
 */
fun isOwnMoneyMove(tx: MpesaTransaction): Boolean = when (tx.type) {
    TransactionType.SAVINGS, TransactionType.FULIZA -> true
    TransactionType.DEPOSIT -> tx.counterpartyName?.startsWith("M-Shwari", ignoreCase = true) == true
    else -> false
}

data class FeeSummary(
    val total: Double = 0.0,
    val withdrawCount: Int = 0,
    val withdrawFees: Double = 0.0
)

/** Transaction costs on money that went out. Fuliza access fees live on the Fuliza ledger. */
fun feeSummary(txs: Iterable<MpesaTransaction>): FeeSummary {
    var total = 0.0
    var withdrawCount = 0
    var withdrawFees = 0.0
    for (tx in txs) {
        if (!tx.type.isOutgoing()) continue
        val cost = tx.cost?.takeIf { it > 0.0 } ?: 0.0
        total += cost
        if (tx.type == TransactionType.WITHDRAW) {
            withdrawCount++
            withdrawFees += cost
        }
    }
    return FeeSummary(total, withdrawCount, withdrawFees)
}

/** Stable key for "the same payee": name when the SMS has one, else the phone digits. */
fun payeeKey(tx: MpesaTransaction): String? {
    val name = tx.counterpartyName?.trim()?.takeIf { it.isNotEmpty() }
    if (name != null) return name.lowercase(Locale.US).replace(Regex("\\s+"), " ")
    val digits = tx.counterpartyPhone?.filter { it.isDigit() }?.takeIf { it.isNotEmpty() }
    return digits?.let { "tel:$it" }
}

private fun payeeName(tx: MpesaTransaction): String =
    tx.counterpartyName?.trim()?.takeIf { it.isNotEmpty() }
        ?: tx.counterpartyPhone?.trim()?.takeIf { it.isNotEmpty() }
        ?: "Unknown"

enum class Cadence(val days: Int, val label: String) {
    WEEKLY(7, "Weekly"),
    FORTNIGHTLY(14, "Every 2 weeks"),
    MONTHLY(30, "Monthly")
}

data class RegularPayee(
    val key: String,
    val name: String,
    val cadence: Cadence,
    val typicalAmount: Double,
    val monthlyCost: Double,
    val lastPaid: LocalDate,
    val nextDue: LocalDate,
    val payments: Int,
    val essential: Boolean = false,
    val watched: Boolean = false
)

private val PAYEE_TYPES = setOf(
    TransactionType.SENT,
    TransactionType.PAYBILL,
    TransactionType.BUY_GOODS,
    TransactionType.POCHI,
    TransactionType.AIRTIME
)

private fun cadenceFor(medianDays: Long): Cadence? = when (medianDays) {
    in 5L..9L -> Cadence.WEEKLY
    in 12L..17L -> Cadence.FORTNIGHTLY
    in 26L..35L -> Cadence.MONTHLY
    else -> null
}

private fun List<Double>.median(): Double {
    val sorted = sorted()
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2.0
}

/**
 * Bills and standing payments: the same payee, a similar amount, on a steady rhythm
 * (weekly, fortnightly or monthly) across the whole ledger. Several payments on one day
 * count as one. Needs [minPayments] separate days, most gaps close to the typical gap,
 * most amounts close to the typical amount (a shop with changing baskets is a habit,
 * not a regular), and a payment recent enough that the rhythm has not stopped.
 * Most expensive per month first.
 */
fun detectRegulars(
    txs: Iterable<MpesaTransaction>,
    zone: ZoneId,
    today: LocalDate,
    essentials: Set<String> = emptySet(),
    watched: Set<String> = emptySet(),
    minPayments: Int = 3
): List<RegularPayee> {
    val byPayee = txs
        .filter { it.type in PAYEE_TYPES && (it.amount ?: 0.0) > 0.0 }
        .groupBy { payeeKey(it) }
    val regulars = mutableListOf<RegularPayee>()
    for ((key, payments) in byPayee) {
        if (key == null) continue
        val ordered = payments.sortedBy { it.timestampMillis }
        val byDay = ordered
            .groupBy { Instant.ofEpochMilli(it.timestampMillis).atZone(zone).toLocalDate() }
            .mapValues { (_, list) -> list.sumOf { it.amount ?: 0.0 } }
            .toSortedMap()
        val dates = byDay.keys.toList()
        if (dates.size < minPayments) continue
        val gaps = dates.zipWithNext { a, b -> ChronoUnit.DAYS.between(a, b) }
        val typicalGap = gaps.map { it.toDouble() }.median().roundToLong()
        val cadence = cadenceFor(typicalGap) ?: continue
        val tolerance = max(2.0, typicalGap * 0.35)
        val steady = gaps.count { abs(it - typicalGap) <= tolerance }
        if (steady < ceil(gaps.size * 2.0 / 3.0)) continue
        val amounts = byDay.values.toList()
        val typical = amounts.median()
        val amountTolerance = max(50.0, typical * 0.4)
        val similar = amounts.count { abs(it - typical) <= amountTolerance }
        if (similar < ceil(amounts.size * 2.0 / 3.0)) continue
        val lastPaid = dates.last()
        if (ChronoUnit.DAYS.between(lastPaid, today) > typicalGap * 2 + 3) continue
        regulars += RegularPayee(
            key = key,
            name = payeeName(ordered.last()),
            cadence = cadence,
            typicalAmount = typical,
            monthlyCost = typical * (365.25 / 12.0) / cadence.days,
            lastPaid = lastPaid,
            nextDue = lastPaid.plusDays(typicalGap),
            payments = dates.size,
            essential = key in essentials,
            watched = key in watched
        )
    }
    return regulars.sortedByDescending { it.monthlyCost }
}

fun regularCadenceCopy(regular: RegularPayee, today: LocalDate): String {
    val due = regular.nextDue
    val timing = when {
        due.isBefore(today) -> "was due ${busiestDayLabel(due)}"
        due == today -> "due today"
        else -> "next ~${busiestDayLabel(due)}"
    }
    return "${regular.cadence.label} · $timing"
}

data class SpendingHabit(
    val name: String,
    /** Separate days with a payment there. */
    val visits: Int,
    val total: Double
)

/** Shops and merchants. People, airtime and cash are not habits to pause. */
private val HABIT_TYPES = setOf(
    TransactionType.BUY_GOODS,
    TransactionType.POCHI,
    TransactionType.PAYBILL
)

/**
 * The shop visited on the most separate days in the period, outside the regular bills,
 * payees marked essential ([exclude]) and everyday essentials like groceries and power.
 */
fun topHabit(
    txs: Iterable<MpesaTransaction>,
    zone: ZoneId,
    exclude: Set<String> = emptySet(),
    minVisits: Int = 3
): SpendingHabit? =
    txs
        .filter { it.type in HABIT_TYPES && (it.amount ?: 0.0) > 0.0 }
        .filterNot { CategoryMatcher.matches(CategoryRules.everydayEssentials, it.counterpartyName) }
        .groupBy { payeeKey(it) }
        .filterKeys { it != null && it !in exclude }
        .map { (_, list) ->
            SpendingHabit(
                name = payeeName(list.maxBy { it.timestampMillis }),
                visits = list.map { Instant.ofEpochMilli(it.timestampMillis).atZone(zone).toLocalDate() }
                    .distinct()
                    .size,
                total = list.sumOf { it.amount ?: 0.0 }
            )
        }
        .filter { it.visits >= minVisits }
        .maxWithOrNull(compareBy<SpendingHabit> { it.visits }.thenBy { it.total })

/** "You kept KES X more than the stretch before." Null when there is nothing to compare. */
fun keptHeadline(net: Double, previousNet: Double, hasPrior: Boolean): String? {
    if (!hasPrior) return null
    val diff = net - previousNet
    return when {
        abs(diff) < 1.0 -> "You kept about the same as the stretch before."
        diff > 0 -> "You kept ${formatKsh(diff)} more than the stretch before."
        else -> "You kept ${formatKsh(-diff)} less than the stretch before."
    }
}

data class InsightBadge(
    val id: String,
    val name: String,
    val mark: String,
    val earned: Boolean
)

const val BADGE_FEE_CUT = "fee_cut"
const val BADGE_QUIET_30 = "quiet_30"
const val BADGE_REGULARS_REVIEWED = "regulars_reviewed"

/**
 * Badges for things that actually help: paying less in fees than the stretch before,
 * 30 days in a row with no money out, and deciding on every regular payee.
 * Anything in [earned] stays earned.
 */
fun insightBadges(
    fees: Double,
    previousFees: Double,
    hasPrior: Boolean,
    quietStreak: Int,
    regulars: List<RegularPayee>,
    earned: Set<String>
): List<InsightBadge> {
    val feeCut = BADGE_FEE_CUT in earned || (hasPrior && previousFees > 0.0 && fees < previousFees)
    val quiet = BADGE_QUIET_30 in earned || quietStreak >= 30
    val decided = regulars.count { it.essential || it.watched }
    val reviewed = BADGE_REGULARS_REVIEWED in earned ||
        (regulars.isNotEmpty() && decided == regulars.size)
    return listOf(
        InsightBadge(BADGE_FEE_CUT, "First fee cut", if (feeCut) "1st" else "—", feeCut),
        InsightBadge(BADGE_QUIET_30, "30 quiet days", if (quiet) "30" else "${quietStreak.coerceAtMost(30)}/30", quiet),
        InsightBadge(
            BADGE_REGULARS_REVIEWED,
            "Reviewed all regulars",
            when {
                reviewed -> "All"
                regulars.isEmpty() -> "—"
                else -> "$decided/${regulars.size}"
            },
            reviewed
        )
    )
}

data class PeriodBar(
    val label: String,
    val inflow: Double,
    val outflow: Double
)

private val monthLabel: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM", Locale.US)
private val monthYearLabel: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM yy", Locale.US)

/**
 * Money in and out per month for stretches longer than a month, or per year when
 * the stretch covers more than two years. Empty for short stretches (the day grid covers those).
 */
fun periodBars(
    from: LocalDate,
    to: LocalDate,
    inflowByDate: Map<LocalDate, Double>,
    outflowByDate: Map<LocalDate, Double>
): List<PeriodBar> {
    if (ChronoUnit.DAYS.between(from, to) + 1 <= 31) return emptyList()
    val first = YearMonth.from(from)
    val last = YearMonth.from(to)
    val months = ChronoUnit.MONTHS.between(first, last) + 1
    fun inRange(date: LocalDate) = !date.isBefore(from) && !date.isAfter(to)
    if (months > 24) {
        return (from.year..to.year).map { year ->
            PeriodBar(
                label = year.toString(),
                inflow = inflowByDate.filterKeys { it.year == year && inRange(it) }.values.sum(),
                outflow = outflowByDate.filterKeys { it.year == year && inRange(it) }.values.sum()
            )
        }
    }
    val spansYears = first.year != last.year
    return generateSequence(first) { it.plusMonths(1) }
        .takeWhile { !it.isAfter(last) }
        .map { month ->
            PeriodBar(
                label = month.format(if (spansYears) monthYearLabel else monthLabel),
                inflow = inflowByDate.filterKeys { YearMonth.from(it) == month && inRange(it) }.values.sum(),
                outflow = outflowByDate.filterKeys { YearMonth.from(it) == month && inRange(it) }.values.sum()
            )
        }
        .toList()
}

/** Average money out per day to payees [matches] picks, over the [days] before [beforeMillis]. */
fun averageDailySpend(
    txs: Iterable<MpesaTransaction>,
    beforeMillis: Long,
    days: Int = 28,
    matches: (MpesaTransaction) -> Boolean
): Double {
    val since = beforeMillis - days * 24L * 60L * 60L * 1000L
    val total = txs
        .filter { it.timestampMillis in since until beforeMillis && it.type.isOutgoing() && matches(it) }
        .sumOf { it.amount ?: 0.0 }
    return total / days
}
