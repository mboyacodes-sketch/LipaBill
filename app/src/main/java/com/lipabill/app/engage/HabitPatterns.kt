package com.lipabill.app.engage

import com.lipabill.app.data.model.TransactionType
import java.time.DayOfWeek
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** Something the ledger keeps showing. Each one can become a challenge or be waved off. */
sealed interface HabitPattern {
    /** Stable id for "Not me". Payee names stay on the phone with the rest of the ledger. */
    val id: String

    data class FoodPeakDay(val day: DayOfWeek, val sharePercent: Int, val weeklyCents: Long) : HabitPattern {
        override val id = "food_peak"
    }

    data class FulizaLateMonth(val lateMonths: Int, val months: Int, val draws: Int) : HabitPattern {
        override val id = "fuliza_late"
    }

    data class FrequentPayee(val payee: String, val count: Int, val cents: Long) : HabitPattern {
        override val id = "regular:" + payee.lowercase()
    }

    data class AirtimeDrip(val count: Int, val averageCents: Long) : HabitPattern {
        override val id = "airtime_drip"
    }

    /** Spike in spend in the first 48h after a large credit. */
    data class PaydayRush(val events: Int, val spendCents: Long) : HabitPattern {
        override val id = "payday_rush"
    }
}

data class CatchUp(
    val daysAway: Int,
    val payments: Int,
    val outCents: Long,
    /** Distinct new shops paid while away (not seen in the prior 30 days). */
    val newRegulars: Int = 0,
    /** Essentials bill label with a recent cadence, e.g. KPLC. */
    val nextBillLabel: String? = null
)

object HabitPatterns {

    const val WINDOW_DAYS = 90
    private const val DAY = 24L * 60L * 60L * 1000L
    private const val MONTH_DAYS = 30L
    const val CATCH_UP_DAYS = 3
    private const val PAYDAY_HOURS = 48L
    private const val LARGE_CREDIT_CENTS = 5_000_00L
    private const val PAYDAY_SPEND_FLOOR_CENTS = 1_500_00L

    /** Same shape as Insights' payee key, so payees marked essential there match here. */
    fun payeeKey(name: String): String = name.trim().lowercase().replace(Regex("\\s+"), " ")

    /**
     * [transactions] should cover the last [WINDOW_DAYS]; older rows are ignored.
     * [essentialKeys] are payees the user marked essential in Insights.
     */
    fun detect(
        transactions: List<ChallengeTransaction>,
        now: Long,
        zone: ZoneId,
        essentialKeys: Set<String> = emptySet()
    ): List<HabitPattern> {
        val window = transactions.filter { it.timestampMillis in (now - WINDOW_DAYS * DAY)..now }
        return listOfNotNull(
            foodPeak(window, now, zone),
            fulizaLate(window, zone),
            regulars(window, now, limit = 1, essentialKeys = essentialKeys).firstOrNull(),
            airtimeDrip(window, now),
            paydayRush(window, now)
        )
    }

    private fun foodPeak(window: List<ChallengeTransaction>, now: Long, zone: ZoneId): HabitPattern? {
        val food = window.filter { CategoryMatcher.matches(CategoryRules.foodDelivery, it) }
        if (food.size < 4) return null
        val byDay = food.groupingBy { Instant.ofEpochMilli(it.timestampMillis).atZone(zone).dayOfWeek }.eachCount()
        val (day, count) = byDay.maxWithOrNull(compareBy<Map.Entry<DayOfWeek, Int>> { it.value }.thenBy { -it.key.value })
            ?: return null
        val share = count * 100 / food.size
        if (share < 35) return null
        val first = food.minOf { it.timestampMillis }
        val weeks = ((now - first).toDouble() / (7 * DAY)).coerceAtLeast(1.0)
        return HabitPattern.FoodPeakDay(day, share, (food.sumOf { it.amountCents } / weeks).toLong())
    }

    private fun fulizaLate(window: List<ChallengeTransaction>, zone: ZoneId): HabitPattern? {
        val draws = window.filter { it.type == TransactionType.FULIZA && it.amountCents > 0L }
        if (draws.isEmpty()) return null
        fun monthOf(tx: ChallengeTransaction) = YearMonth.from(Instant.ofEpochMilli(tx.timestampMillis).atZone(zone))
        val late = draws
            .filter { Instant.ofEpochMilli(it.timestampMillis).atZone(zone).dayOfMonth >= 20 }
            .map(::monthOf)
            .toSet()
        if (late.size < 2) return null
        val months = window.map(::monthOf).toSet().size
        return HabitPattern.FulizaLateMonth(late.size, months, draws.size)
    }

    private fun airtimeDrip(window: List<ChallengeTransaction>, now: Long): HabitPattern? {
        val airtime = window.filter {
            it.type == TransactionType.AIRTIME && it.amountCents > 0L && it.timestampMillis >= now - MONTH_DAYS * DAY
        }
        if (airtime.size < 10) return null
        return HabitPattern.AirtimeDrip(airtime.size, airtime.sumOf { it.amountCents } / airtime.size)
    }

    /**
     * Large credit (salary-like) followed by a spend spike in the next 48 hours.
     * Needs at least two such events in the window.
     */
    private fun paydayRush(window: List<ChallengeTransaction>, now: Long): HabitPattern.PaydayRush? {
        val credits = window.filter {
            (it.type == TransactionType.RECEIVED || it.type == TransactionType.DEPOSIT) &&
                it.amountCents >= LARGE_CREDIT_CENTS
        }
        if (credits.isEmpty()) return null
        val events = credits.mapNotNull { credit ->
            val end = credit.timestampMillis + PAYDAY_HOURS * 3_600_000L
            val spend = window
                .asSequence()
                .filter { it.timestampMillis > credit.timestampMillis && it.timestampMillis <= end }
                .sumOf { WeeklySummaryBuilder.signedSpendCents(it) }
                .coerceAtLeast(0L)
            spend.takeIf { it >= PAYDAY_SPEND_FLOOR_CENTS }
        }
        if (events.size < 2) return null
        return HabitPattern.PaydayRush(events = events.size, spendCents = events.sum())
    }

    /**
     * Shops paid at least four times in 30 days, most often first. Food delivery has its own
     * challenge, and Paybill / Send Money are left out: rent, bills and family are not habits to skip.
     * Groceries, health, school and anything marked essential are left out too.
     */
    fun regulars(
        transactions: List<ChallengeTransaction>,
        now: Long,
        limit: Int = 3,
        essentialKeys: Set<String> = emptySet()
    ): List<HabitPattern.FrequentPayee> = transactions
        .asSequence()
        .filter { it.timestampMillis in (now - MONTH_DAYS * DAY)..now }
        .filter { it.type == TransactionType.BUY_GOODS && it.amountCents > 0L }
        .filter { !it.counterpartyName.isNullOrBlank() }
        .filterNot { CategoryMatcher.matches(CategoryRules.foodDelivery, it.counterpartyName) }
        .filterNot { CategoryMatcher.matches(CategoryRules.everydayEssentials, it.counterpartyName) }
        .filterNot { payeeKey(it.counterpartyName!!) in essentialKeys }
        .groupBy { payeeKey(it.counterpartyName!!) }
        .values
        .filter { it.size >= 4 }
        .map { rows ->
            HabitPattern.FrequentPayee(
                payee = rows.first().counterpartyName!!.trim(),
                count = rows.size,
                cents = rows.sumOf { it.amountCents }
            )
        }
        .sortedWith(compareByDescending<HabitPattern.FrequentPayee> { it.count }.thenByDescending { it.cents })
        .take(limit)
        .toList()

    /** What happened while Habits was closed for [CATCH_UP_DAYS] or more. Null on a first visit. */
    fun catchUp(transactions: List<ChallengeTransaction>, lastVisit: Long, now: Long): CatchUp? {
        if (lastVisit <= 0L || now - lastVisit < CATCH_UP_DAYS * DAY) return null
        val since = transactions.filter { it.timestampMillis in lastVisit..now }
        if (since.isEmpty()) return null
        val priorStart = lastVisit - MONTH_DAYS * DAY
        val priorPayees = transactions
            .asSequence()
            .filter { it.timestampMillis in priorStart until lastVisit }
            .filter { it.type == TransactionType.BUY_GOODS && !it.counterpartyName.isNullOrBlank() }
            .map { payeeKey(it.counterpartyName!!) }
            .toSet()
        val newRegulars = since
            .asSequence()
            .filter { it.type == TransactionType.BUY_GOODS && !it.counterpartyName.isNullOrBlank() }
            .map { payeeKey(it.counterpartyName!!) }
            .filterNot { it in priorPayees }
            .toSet()
            .size
        return CatchUp(
            daysAway = ((now - lastVisit) / DAY).toInt(),
            payments = since.size,
            outCents = since.sumOf { WeeklySummaryBuilder.signedSpendCents(it) }.coerceAtLeast(0L),
            newRegulars = newRegulars,
            nextBillLabel = nextBillLabel(transactions, now)
        )
    }

    /** Essentials payee seen at least twice in 90 days with a roughly monthly gap. */
    private fun nextBillLabel(transactions: List<ChallengeTransaction>, now: Long): String? {
        val window = transactions.filter { it.timestampMillis in (now - WINDOW_DAYS * DAY)..now }
        val groups = window
            .asSequence()
            .filter { it.amountCents > 0L && !it.counterpartyName.isNullOrBlank() }
            .filter { CategoryMatcher.matches(CategoryRules.everydayEssentials, it.counterpartyName) }
            .groupBy { payeeKey(it.counterpartyName!!) }
        for ((_, rows) in groups.entries.sortedByDescending { it.value.size }) {
            if (rows.size < 2) continue
            val times = rows.map { it.timestampMillis }.sorted()
            val gaps = times.zipWithNext { a, b -> (b - a) / DAY }
            if (gaps.any { it in 20L..40L }) {
                val name = rows.maxBy { it.timestampMillis }.counterpartyName!!.trim()
                return billShortLabel(name)
            }
        }
        return null
    }

    private fun billShortLabel(name: String): String {
        val key = payeeKey(name)
        return when {
            "kplc" in key || "kenya power" in key -> "KPLC"
            "water" in key || "sewerage" in key -> "Water"
            else -> name.split(" ").firstOrNull()?.take(12) ?: name.take(12)
        }
    }

    /**
     * How much Habits has taught the user: kept challenges and taught payees raise the score,
     * waved-off patterns lower it. Clamped to 0..100.
     */
    fun learningScore(dismissedCount: Int, confirmedViaChallenge: Int, taughtPayees: Int): Int {
        val raw = confirmedViaChallenge * 18 + taughtPayees * 22 - dismissedCount * 8 + 10
        return raw.coerceIn(0, 100)
    }

    /**
     * Mon–Sun counts of food delivery in the last 30 days. Empty when there were none.
     */
    fun foodDayBars(
        transactions: List<ChallengeTransaction>,
        zone: ZoneId,
        now: Long
    ): List<Int> {
        val food = transactions.filter {
            it.timestampMillis in (now - MONTH_DAYS * DAY)..now &&
                CategoryMatcher.matches(CategoryRules.foodDelivery, it)
        }
        if (food.isEmpty()) return emptyList()
        val counts = IntArray(7)
        for (tx in food) {
            val dow = Instant.ofEpochMilli(tx.timestampMillis).atZone(zone).dayOfWeek
            counts[dow.value - 1]++
        }
        return counts.toList()
    }

    /**
     * Fees avoided by kept zero-fee weeks: the user's usual weekly transaction costs over
     * [transactions] (the last [WINDOW_DAYS]), once per kept week. Zero when there is no fee
     * history to go on; nothing is made up.
     */
    fun feesDodgedCents(
        transactions: List<ChallengeTransaction>,
        keptTemplateIds: List<String>
    ): Long {
        val keptWeeks = keptTemplateIds.count { it == ChallengeTemplates.zeroFee7Days.id }
        if (keptWeeks == 0 || transactions.isEmpty()) return 0L
        val fees = transactions.filter { it.type.isOutgoing() }.sumOf { it.feeCents }
        if (fees <= 0L) return 0L
        val first = transactions.minOf { it.timestampMillis }
        val last = transactions.maxOf { it.timestampMillis }
        val weeks = ((last - first).toDouble() / (7 * DAY)).coerceAtLeast(1.0)
        return (fees / weeks).toLong() * keptWeeks
    }
}
