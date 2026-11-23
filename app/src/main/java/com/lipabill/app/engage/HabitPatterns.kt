package com.lipabill.app.engage

import com.lipabill.app.data.foldMerchantName
import com.lipabill.app.data.foldPayeeName
import com.lipabill.app.data.model.TransactionType
import com.lipabill.app.viewmodel.isOwnMoneyMove
import com.lipabill.app.viewmodel.markedPayee
import com.lipabill.app.viewmodel.payeeKey as insightsPayeeKey
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
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
        override val id = "regular:" + foldPayeeName(payee)
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
    /** Bill label with a recent cadence, e.g. KPLC (from RecurringPatterns). */
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
    /** More than this many large credits in one calendar month → gifts / cash-ins, not salary. */
    private const val MAX_LARGE_CREDITS_PER_MONTH = 2

    private val SALARY_NAME_CUES = listOf(
        "payroll", "salary", "salaries", "wages", "payslip", "pay slip", "stipend"
    )

    /**
     * Same identity as Insights [payeeKey]: phone-first when known, else folded name.
     * Name-only overload kept for bill short labels and older call sites.
     */
    fun payeeKey(name: String): String = insightsPayeeKey(name, null) ?: foldPayeeName(name)

    fun payeeKey(name: String?, phone: String?): String =
        insightsPayeeKey(name, phone) ?: name?.let(::foldPayeeName).orEmpty()

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
            regulars(window, now, zone, limit = 1, essentialKeys = essentialKeys).firstOrNull(),
            airtimeDrip(window, now),
            paydayRush(window, now, zone)
        )
    }

    private fun foodPeak(window: List<ChallengeTransaction>, now: Long, zone: ZoneId): HabitPattern? {
        val food = window.filter { it.amountCents > 0L && isFoodSpend(it.type, it.counterpartyName) }
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
     * Large salary-like credit followed by a spend spike in the next 48 hours.
     * Needs at least two such events. Own-money deposits, agent cash-ins, and busy
     * gift months are left out.
     */
    private fun paydayRush(
        window: List<ChallengeTransaction>,
        now: Long,
        zone: ZoneId
    ): HabitPattern.PaydayRush? {
        val credits = window.filter { looksLikePaydayCredit(it, window, zone) }
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

    private fun looksLikePaydayCredit(
        tx: ChallengeTransaction,
        window: List<ChallengeTransaction>,
        zone: ZoneId
    ): Boolean {
        if (tx.type != TransactionType.RECEIVED && tx.type != TransactionType.DEPOSIT) return false
        if (tx.amountCents < LARGE_CREDIT_CENTS) return false
        if (isOwnMoneyMove(tx.type, tx.counterpartyName)) return false
        val name = foldMerchantName(tx.counterpartyName).orEmpty()
        if (name.contains("agent")) return false
        if (SALARY_NAME_CUES.any { name.contains(it) }) return true
        // Rare large credits in that calendar month (salary-ish); frequent = gifts / top-ups.
        val month = YearMonth.from(Instant.ofEpochMilli(tx.timestampMillis).atZone(zone))
        val largeInMonth = window.count { other ->
            (other.type == TransactionType.RECEIVED || other.type == TransactionType.DEPOSIT) &&
                other.amountCents >= LARGE_CREDIT_CENTS &&
                !isOwnMoneyMove(other.type, other.counterpartyName) &&
                YearMonth.from(Instant.ofEpochMilli(other.timestampMillis).atZone(zone)) == month
        }
        return largeInMonth <= MAX_LARGE_CREDITS_PER_MONTH
    }

    /**
     * Shops paid on at least four distinct calendar days in 30 days, most often first.
     * Food delivery has its own challenge; Paybill / Send Money are left out. Groceries,
     * health, school and anything marked essential (Insights phone- or name-key) are left out.
     */
    fun regulars(
        transactions: List<ChallengeTransaction>,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        limit: Int = 3,
        essentialKeys: Set<String> = emptySet()
    ): List<HabitPattern.FrequentPayee> = transactions
        .asSequence()
        .filter { it.timestampMillis in (now - MONTH_DAYS * DAY)..now }
        .filter { it.type == TransactionType.BUY_GOODS && it.amountCents > 0L }
        .filter { !it.counterpartyName.isNullOrBlank() }
        .filterNot { isFoodSpend(it.type, it.counterpartyName) }
        .filterNot { CategoryMatcher.matches(CategoryRules.everydayEssentials, it.counterpartyName) }
        .filterNot { markedPayee(it.counterpartyName, it.counterpartyPhone, essentialKeys) }
        .groupBy { payeeKey(it.counterpartyName, it.counterpartyPhone) }
        .values
        .mapNotNull { rows ->
            val days = rows.mapTo(linkedSetOf()) { dayOf(it.timestampMillis, zone) }
            if (days.size < 4) return@mapNotNull null
            HabitPattern.FrequentPayee(
                payee = rows.first().counterpartyName!!.trim(),
                count = days.size,
                cents = rows.sumOf { it.amountCents }
            )
        }
        .sortedWith(compareByDescending<HabitPattern.FrequentPayee> { it.count }.thenByDescending { it.cents })
        .take(limit)
        .toList()

    private fun dayOf(millis: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    /** What happened while Habits was closed for [CATCH_UP_DAYS] or more. Null on a first visit. */
    fun catchUp(
        transactions: List<ChallengeTransaction>,
        lastVisit: Long,
        now: Long,
        recurringChoices: Map<String, RecurringChoice> = emptyMap(),
        essentials: Set<String> = emptySet()
    ): CatchUp? {
        if (lastVisit <= 0L || now - lastVisit < CATCH_UP_DAYS * DAY) return null
        val since = transactions.filter { it.timestampMillis in lastVisit..now }
        if (since.isEmpty()) return null
        val priorStart = lastVisit - MONTH_DAYS * DAY
        val priorPayees = transactions
            .asSequence()
            .filter { it.timestampMillis in priorStart until lastVisit }
            .filter { it.type == TransactionType.BUY_GOODS && !it.counterpartyName.isNullOrBlank() }
            .map { payeeKey(it.counterpartyName, it.counterpartyPhone) }
            .filter { it.isNotEmpty() }
            .toSet()
        val newRegulars = since
            .asSequence()
            .filter { it.type == TransactionType.BUY_GOODS && !it.counterpartyName.isNullOrBlank() }
            .map { payeeKey(it.counterpartyName, it.counterpartyPhone) }
            .filter { it.isNotEmpty() }
            .filterNot { it in priorPayees }
            .toSet()
            .size
        return CatchUp(
            daysAway = ((now - lastVisit) / DAY).toInt(),
            payments = since.size,
            outCents = since.sumOf { WeeklySummaryBuilder.signedSpendCents(it) }.coerceAtLeast(0L),
            newRegulars = newRegulars,
            nextBillLabel = nextBillLabel(
                transactions,
                now,
                recurringChoices = recurringChoices,
                essentials = essentials
            )
        )
    }

    /**
     * Next bill short label from [RecurringPatterns] (same cadence detector as Insights).
     * [recurringChoices] / [essentials] come from prefs when Habits opens.
     */
    fun nextBillLabel(
        transactions: List<ChallengeTransaction>,
        now: Long,
        recurringChoices: Map<String, RecurringChoice> = emptyMap(),
        essentials: Set<String> = emptySet(),
        zone: ZoneId = ZoneId.systemDefault()
    ): String? {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val window = transactions.filter { it.timestampMillis in (now - WINDOW_DAYS * DAY)..now }
        val patterns = RecurringPatterns.detectFromChallenge(
            rows = window,
            today = today,
            zone = zone,
            choices = recurringChoices,
            essentials = essentials
        )
        return RecurringPatterns.nextBillLabel(patterns, today)
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
                it.amountCents > 0L &&
                isFoodSpend(it.type, it.counterpartyName)
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
