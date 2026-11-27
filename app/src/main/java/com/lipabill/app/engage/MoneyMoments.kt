package com.lipabill.app.engage

import com.lipabill.app.data.model.MpesaTransaction
import com.lipabill.app.data.model.TransactionType
import com.lipabill.app.ui.util.formatKsh
import com.lipabill.app.viewmodel.CategoryBudget
import com.lipabill.app.viewmodel.GoalProgress
import com.lipabill.app.viewmodel.spendByCategory
import com.lipabill.app.viewmodel.spendCategoryOf
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.IsoFields

/**
 * Nudges right after a payment lands: a budget crossing 80% or its limit, the fourth food
 * delivery in a week, and payday (with a set-aside suggestion when a goal exists). Each has a
 * key per period, so it fires at most once (the caller remembers sent keys).
 */
object MoneyMoments {

    data class Moment(val key: String, val title: String, val body: String)

    private const val FOOD_DELIVERIES_PER_WEEK = 4
    private const val PAYDAY_MIN = 5_000.0

    fun evaluate(
        tx: MpesaTransaction,
        recent: List<MpesaTransaction>,
        budgets: List<CategoryBudget>,
        goals: List<GoalProgress>,
        overrides: Map<String, String>,
        alreadySent: Set<String>,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): List<Moment> {
        val moments = mutableListOf<Moment>()
        budgetMoment(tx, recent, budgets, overrides, today, zone)?.let(moments::add)
        foodMoment(tx, recent, today, zone)?.let(moments::add)
        paydayMoment(tx, recent, goals, today, zone)?.let(moments::add)
        priceRiseMoment(tx, recent, today, zone)?.let(moments::add)
        return moments.filter { it.key !in alreadySent }
    }

    private fun MpesaTransaction.date(zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(timestampMillis).atZone(zone).toLocalDate()

    private fun budgetMoment(
        tx: MpesaTransaction,
        recent: List<MpesaTransaction>,
        budgets: List<CategoryBudget>,
        overrides: Map<String, String>,
        today: LocalDate,
        zone: ZoneId
    ): Moment? {
        if (!tx.type.isOutgoing() || budgets.isEmpty()) return null
        val category = spendCategoryOf(tx, overrides)
        val budget = budgets.firstOrNull { it.category == category } ?: return null
        val month = YearMonth.from(today)
        val monthTxs = (recent + tx)
            .distinctBy { "${it.source}:${it.code}" }
            .filter { YearMonth.from(it.date(zone)) == month }
        val after = spendByCategory(monthTxs, overrides)[category] ?: return null
        val before = after - (tx.amount ?: 0.0)
        val limit = budget.monthlyLimit
        val daysLeft = month.lengthOfMonth() - today.dayOfMonth
        val period = month.toString()
        return when {
            before < limit && after >= limit -> Moment(
                key = "budget100:${category.id}:$period",
                title = "${category.label} budget used up",
                body = "${formatKsh(after)} of ${formatKsh(limit)} this month, with $daysLeft days to go."
            )
            before < limit * 0.8 && after >= limit * 0.8 -> Moment(
                key = "budget80:${category.id}:$period",
                title = "${category.label}: 80% of the budget",
                body = "${formatKsh(after)} of ${formatKsh(limit)} used, ${formatKsh(limit - after)} left for $daysLeft days."
            )
            else -> null
        }
    }

    private fun foodMoment(
        tx: MpesaTransaction,
        recent: List<MpesaTransaction>,
        today: LocalDate,
        zone: ZoneId
    ): Moment? {
        if (!tx.type.isOutgoing()) return null
        if (!CategoryMatcher.matches(CategoryRules.foodDelivery, tx.counterpartyName)) return null
        val weekAgo = today.minusDays(6)
        val count = (recent + tx)
            .distinctBy { "${it.source}:${it.code}" }
            .count {
                it.type.isOutgoing() &&
                    !it.date(zone).isBefore(weekAgo) &&
                    CategoryMatcher.matches(CategoryRules.foodDelivery, it.counterpartyName)
            }
        if (count < FOOD_DELIVERIES_PER_WEEK) return null
        val week = "${today.get(IsoFields.WEEK_BASED_YEAR)}-${today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}"
        return Moment(
            key = "food$FOOD_DELIVERIES_PER_WEEK:$week",
            title = "Food delivery #$count this week",
            body = "Try 3 days without it? Your Habits page has the challenge ready."
        )
    }

    /** A regular bill (3+ earlier payments to the same payee) that came in 5% / KSh 50 above usual. */
    private fun priceRiseMoment(
        tx: MpesaTransaction,
        recent: List<MpesaTransaction>,
        today: LocalDate,
        zone: ZoneId
    ): Moment? {
        if (tx.type != TransactionType.PAYBILL && tx.type != TransactionType.BUY_GOODS) return null
        val amount = tx.amount ?: return null
        val key = com.lipabill.app.viewmodel.payeeKey(tx) ?: return null
        val earlier = recent
            .filter { it.code != tx.code && com.lipabill.app.viewmodel.payeeKey(it) == key && (it.amount ?: 0.0) > 0.0 }
        // Only bills that repeat: at least three earlier payments of a steady size.
        if (earlier.size < 3) return null
        val amounts = earlier.mapNotNull { it.amount }.sorted()
        val usual = amounts[amounts.size / 2]
        if (amounts.any { kotlin.math.abs(it - usual) > usual * 0.1 }) return null
        val rise = amount - usual
        if (rise < 50.0 || rise < usual * 0.05) return null
        val name = tx.counterpartyName?.trim()?.ifBlank { null } ?: return null
        return Moment(
            key = "priceup:$key:${YearMonth.from(today)}",
            title = "$name went up ${formatKsh(rise)}",
            body = "You paid ${formatKsh(amount)}. It’s usually ${formatKsh(usual)}."
        )
    }

    private fun paydayMoment(
        tx: MpesaTransaction,
        recent: List<MpesaTransaction>,
        goals: List<GoalProgress>,
        today: LocalDate,
        zone: ZoneId
    ): Moment? {
        if (tx.type != TransactionType.RECEIVED && tx.type != TransactionType.DEPOSIT) return null
        val amount = tx.amount ?: return null
        if (amount < PAYDAY_MIN) return null
        val since = today.minusDays(90)
        val receipts = recent
            .filter { (it.type == TransactionType.RECEIVED || it.type == TransactionType.DEPOSIT) && !it.date(zone).isBefore(since) && it.code != tx.code }
            .mapNotNull { it.amount }
            .sorted()
        val typical = if (receipts.isEmpty()) 0.0 else receipts[receipts.size / 2]
        if (amount < typical * 3) return null
        val open = goals.firstOrNull { !it.done }
        val setAside = open?.let {
            val weekly = it.perWeekNeeded ?: (amount * 0.1 / 4)
            // Four weeks' worth, rounded to the nearest 100, never more than a quarter of the pay.
            (Math.round(weekly * 4 / 100.0) * 100.0).coerceIn(100.0, amount / 4)
        }
        return Moment(
            key = "payday:${YearMonth.from(today)}",
            title = "${formatKsh(amount)} in",
            body = if (open != null && setAside != null) {
                "Set aside ${formatKsh(setAside)} for ${open.goal.name} before it gets spent?"
            } else {
                "Your safe-to-spend has reset. Have a look at the plan for this month."
            }
        )
    }
}
