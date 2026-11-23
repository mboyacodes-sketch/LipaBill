package com.lipabill.app.viewmodel

import com.lipabill.app.data.model.MpesaTransaction
import com.lipabill.app.data.model.TransactionType
import com.lipabill.app.engage.RecurringChoice
import com.lipabill.app.engage.RecurringPatterns
import com.lipabill.app.engage.SpendCategory
import com.lipabill.app.engage.resolveSpendCategory
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/**
 * The forward-looking side of Insights: what is safe to spend until payday, how each
 * category budget is pacing, how savings goals are filling, and what changed since the
 * last period. Pure functions over the ledger so they are cheap to test.
 */

/** Monthly limit for one spend category. */
data class CategoryBudget(val category: SpendCategory, val monthlyLimit: Double)

/** A named savings target, filled by the wallet's own savings transfers (M-Shwari, lock savings). */
data class SavingsGoal(
    val id: String,
    val name: String,
    val target: Double,
    val createdAtMillis: Long,
    val deadline: LocalDate? = null
)

enum class BudgetPace { ON_TRACK, WATCH, OVER }

data class BudgetStatus(
    val category: SpendCategory,
    val limit: Double,
    val spent: Double,
    /** Where the month ends at the current pace. */
    val projected: Double,
    val daysLeft: Int,
    val pace: BudgetPace
) {
    val usedFraction: Float get() = if (limit <= 0.0) 0f else (spent / limit).toFloat()
    /** Positive when the month is on track to end above the limit. */
    val projectedOver: Double get() = projected - limit
}

data class GoalProgress(
    val goal: SavingsGoal,
    val saved: Double,
    /** To set aside each week to reach the target by the deadline; null without a deadline or when done. */
    val perWeekNeeded: Double?
) {
    val fraction: Float get() = if (goal.target <= 0.0) 0f else (saved / goal.target).coerceIn(0.0, 1.0).toFloat()
    val done: Boolean get() = saved >= goal.target
}

data class SafeToSpend(
    /** Spendable per day until [until]. */
    val perDay: Double,
    /** Balance left after bills due before [until]. */
    val spendable: Double,
    val balance: Double,
    /** Regular bills expected before [until], already held back. */
    val reservedForBills: Double,
    val billsDue: Int,
    /** Next payday when one could be estimated, else the end of the month. */
    val until: LocalDate,
    val daysLeft: Int,
    val paydayEstimated: Boolean
)

data class CategoryChange(
    val category: SpendCategory,
    val now: Double,
    val before: Double
) {
    /** Percent change, or null when there was nothing before to compare with. */
    val percent: Int? get() = if (before < 1.0) null else (((now - before) / before) * 100).roundToInt()
}

/** Spend that counts against a budget: money out, excluding savings, Fuliza draws and cash-out. */
private fun countsAsSpend(type: TransactionType): Boolean = type.isOutgoing() &&
    type != TransactionType.SAVINGS &&
    type != TransactionType.FULIZA &&
    type != TransactionType.WITHDRAW

fun spendCategoryOf(tx: MpesaTransaction, overrides: Map<String, String>): SpendCategory =
    resolveSpendCategory(tx, payeeKey(tx), overrides)

/** Spend per category for [txs]. */
fun spendByCategory(
    txs: Iterable<MpesaTransaction>,
    overrides: Map<String, String>
): Map<SpendCategory, Double> {
    val out = LinkedHashMap<SpendCategory, Double>()
    for (tx in txs) {
        if (!countsAsSpend(tx.type)) continue
        val amount = tx.amount ?: continue
        val category = spendCategoryOf(tx, overrides)
        if (category == SpendCategory.INCOME) continue
        out[category] = (out[category] ?: 0.0) + amount
    }
    return out
}

private fun MpesaTransaction.date(zone: ZoneId): LocalDate =
    Instant.ofEpochMilli(timestampMillis).atZone(zone).toLocalDate()

/**
 * Payday from the ledger: the biggest money-in of each month, when it is clearly larger than
 * everyday receipts and lands on a similar day in at least two of the last three months.
 * Returns the next such day after [today], or null when there is no clear salary pattern.
 */
fun estimateNextPayday(
    ledger: Iterable<MpesaTransaction>,
    today: LocalDate,
    zone: ZoneId = ZoneId.systemDefault()
): LocalDate? {
    val inflows = ledger.filter {
        (it.type == TransactionType.RECEIVED || it.type == TransactionType.DEPOSIT) && (it.amount ?: 0.0) > 0.0
    }
    if (inflows.size < 3) return null
    val months = (1L..3L).map { YearMonth.from(today).minusMonths(it) }
    val biggest = months.mapNotNull { month ->
        inflows.filter { YearMonth.from(it.date(zone)) == month }.maxByOrNull { it.amount ?: 0.0 }
    }
    // "Everyday" receipts are everything except each month's biggest one.
    val everyday = (inflows - biggest.toSet()).mapNotNull { it.amount }.sorted()
    val typical = if (everyday.isEmpty()) 0.0 else everyday[everyday.size / 2]
    val paydays = biggest
        .filter { (it.amount ?: 0.0) >= typical * 3 }
        .map { it.date(zone).dayOfMonth }
    if (paydays.size < 2) return null
    val sorted = paydays.sorted()
    // Days that wander more than a few days are not a salary rhythm.
    if (sorted.last() - sorted.first() > 5) return null
    val day = sorted[sorted.size / 2]
    fun inMonth(month: YearMonth) = month.atDay(day.coerceAtMost(month.lengthOfMonth()))
    val thisMonth = inMonth(YearMonth.from(today))
    return if (thisMonth.isAfter(today)) thisMonth else inMonth(YearMonth.from(today).plusMonths(1))
}

/**
 * Balance minus the regular bills due before the next payday, spread over the days left.
 * Without a payday pattern the window ends with the month.
 */
fun safeToSpend(
    balance: Double?,
    regulars: List<RegularPayee>,
    payday: LocalDate?,
    today: LocalDate
): SafeToSpend? {
    val bal = balance ?: return null
    val until = payday ?: YearMonth.from(today).atEndOfMonth()
    val daysLeft = (ChronoUnit.DAYS.between(today, until) + 1).toInt().coerceAtLeast(1)
    val due = regulars.filter { billLikeComingUp(it) && !it.nextDue.isBefore(today) && !it.nextDue.isAfter(until) }
    val reserved = due.sumOf { it.typicalAmount }
    val spendable = (bal - reserved).coerceAtLeast(0.0)
    return SafeToSpend(
        perDay = spendable / daysLeft,
        spendable = spendable,
        balance = bal,
        reservedForBills = reserved,
        billsDue = due.size,
        until = until,
        daysLeft = daysLeft,
        paydayEstimated = payday != null
    )
}

/** This month's pace for each budget. WATCH from 80% of the limit or a projected overshoot. */
fun budgetStatuses(
    budgets: List<CategoryBudget>,
    ledger: Iterable<MpesaTransaction>,
    overrides: Map<String, String>,
    today: LocalDate,
    zone: ZoneId = ZoneId.systemDefault()
): List<BudgetStatus> {
    if (budgets.isEmpty()) return emptyList()
    val month = YearMonth.from(today)
    val spent = spendByCategory(ledger.filter { YearMonth.from(it.date(zone)) == month }, overrides)
    val dayOfMonth = today.dayOfMonth
    val days = month.lengthOfMonth()
    return budgets.map { budget ->
        val used = spent[budget.category] ?: 0.0
        // Early in the month one payment says little about pace; project from day 5 on.
        val projected = if (dayOfMonth < 5) used else used / dayOfMonth * days
        val pace = when {
            used >= budget.monthlyLimit -> BudgetPace.OVER
            used >= budget.monthlyLimit * 0.8 || projected > budget.monthlyLimit * 1.05 -> BudgetPace.WATCH
            else -> BudgetPace.ON_TRACK
        }
        BudgetStatus(
            category = budget.category,
            limit = budget.monthlyLimit,
            spent = used,
            projected = projected,
            daysLeft = days - dayOfMonth,
            pace = pace
        )
    }
}

/**
 * Savings transfers since each goal was made fill the goals oldest first, so one M-Shwari
 * habit tops up the first goal before the next.
 */
fun goalProgress(
    goals: List<SavingsGoal>,
    ledger: Iterable<MpesaTransaction>,
    today: LocalDate
): List<GoalProgress> {
    if (goals.isEmpty()) return emptyList()
    val oldestFirst = goals.sortedBy { it.createdAtMillis }
    val saved = HashMap<String, Double>()
    ledger
        .filter { it.type == TransactionType.SAVINGS && (it.amount ?: 0.0) > 0.0 }
        .sortedBy { it.timestampMillis }
        .forEach { tx ->
            var left = tx.amount ?: 0.0
            for (goal in oldestFirst) {
                if (left <= 0.0) break
                if (tx.timestampMillis < goal.createdAtMillis) continue
                val room = goal.target - (saved[goal.id] ?: 0.0)
                if (room <= 0.0) continue
                val put = minOf(room, left)
                saved[goal.id] = (saved[goal.id] ?: 0.0) + put
                left -= put
            }
        }
    return goals.map { goal ->
        val have = saved[goal.id] ?: 0.0
        val remaining = goal.target - have
        val perWeek = goal.deadline?.takeIf { remaining > 0.0 && it.isAfter(today) }?.let { deadline ->
            remaining / (ChronoUnit.DAYS.between(today, deadline) / 7.0).coerceAtLeast(1.0)
        }
        GoalProgress(goal, have, perWeek)
    }
}

/** Categories whose spend moved most between the previous period and this one. */
fun categoryChanges(
    now: Map<SpendCategory, Double>,
    before: Map<SpendCategory, Double>,
    limit: Int = 5
): List<CategoryChange> =
    (now.keys + before.keys)
        .map { CategoryChange(it, now[it] ?: 0.0, before[it] ?: 0.0) }
        .filter { it.now >= 1.0 || it.before >= 1.0 }
        .sortedByDescending { kotlin.math.abs(it.now - it.before) }
        .take(limit)

/** Prefs encoding for budgets: `categoryId=limit`. */
fun encodeBudgets(budgets: List<CategoryBudget>): Set<String> =
    budgets.filter { it.monthlyLimit > 0.0 }.mapTo(LinkedHashSet()) { "${it.category.id}=${it.monthlyLimit}" }

fun parseBudgets(encoded: Set<String>): List<CategoryBudget> =
    encoded.mapNotNull { entry ->
        val category = SpendCategory.fromId(entry.substringBefore('=', "")) ?: return@mapNotNull null
        val limit = entry.substringAfter('=', "").toDoubleOrNull()?.takeIf { it > 0.0 } ?: return@mapNotNull null
        CategoryBudget(category, limit)
    }.sortedBy { it.category.ordinal }

/** Prefs encoding for goals: a JSON array. */
fun encodeGoals(goals: List<SavingsGoal>): String {
    val array = org.json.JSONArray()
    goals.forEach { goal ->
        array.put(
            org.json.JSONObject()
                .put("id", goal.id)
                .put("name", goal.name)
                .put("target", goal.target)
                .put("created", goal.createdAtMillis)
                .apply { goal.deadline?.let { put("deadline", it.toString()) } }
        )
    }
    return array.toString()
}

fun parseGoals(encoded: String?): List<SavingsGoal> {
    if (encoded.isNullOrBlank()) return emptyList()
    return runCatching {
        val array = org.json.JSONArray(encoded)
        (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val name = o.optString("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            SavingsGoal(
                id = o.optString("id").ifBlank { "goal-$i" },
                name = name,
                target = o.optDouble("target", 0.0).takeIf { it > 0.0 } ?: return@mapNotNull null,
                createdAtMillis = o.optLong("created", 0L),
                deadline = o.optString("deadline").takeIf { it.isNotBlank() }
                    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            )
        }
    }.getOrDefault(emptyList())
}


/** Everything the plan surfaces show. */
data class MoneyPlanUi(
    val safe: SafeToSpend? = null,
    val budgets: List<BudgetStatus> = emptyList(),
    val goals: List<GoalProgress> = emptyList(),
    val isReady: Boolean = false
)

/** Balance across SIM-linked wallets (or the primary one), minus what is due, plus budgets and goals. */
internal fun buildMoneyPlan(
    ledger: List<MpesaTransaction>,
    balances: Map<com.lipabill.app.data.model.MoneySource, Double>,
    walletSources: List<com.lipabill.app.data.model.MoneySource>,
    primary: com.lipabill.app.data.model.MoneySource,
    budgets: List<CategoryBudget>,
    goals: List<SavingsGoal>,
    overrides: Map<String, String>,
    marks: InsightMarks,
    today: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault()
): MoneyPlanUi {
    val txs = withoutReversals(ledger)
    // Separate ledger window from Insights summary — own detectRegulars pass is intentional.
    val confirmed = marks.recurringChoices
        .filterValues { it == RecurringChoice.Confirmed }
        .keys
    val regulars = RecurringPatterns.applyChoices(
        detectRegulars(txs, zone, today, marks.essential, marks.watched + confirmed),
        marks.recurringChoices
    )
    val wallets = walletSources.ifEmpty { listOf(primary) }
    val known = wallets.mapNotNull { balances[it] }
    val balance = if (known.isEmpty()) null else known.sum()
    return MoneyPlanUi(
        safe = safeToSpend(balance, regulars, estimateNextPayday(txs, today, zone), today),
        budgets = budgetStatuses(budgets, txs, overrides, today, zone),
        goals = goalProgress(goals, txs, today),
        isReady = true
    )
}
