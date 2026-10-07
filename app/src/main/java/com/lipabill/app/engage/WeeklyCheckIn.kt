package com.lipabill.app.engage

import com.lipabill.app.data.model.TransactionType
import java.time.ZoneId

data class WeeklySummary(
    val weekId: String,
    val spendCents: Long,
    val previousSpendCents: Long,
    val byCategory: List<CategorySpend>,
    val quietDays: Int,
    val daysCounted: Int
)

data class CategorySpend(
    val type: TransactionType,
    val cents: Long
)

enum class SpendCompare { MORE, LESS, SAME }

enum class QuizKind { TOP_CATEGORY, WEEK_COMPARE, QUIET_DAYS }

data class QuizOption(val id: String, val label: String)

data class QuizQuestion(
    val id: String,
    val kind: QuizKind,
    val options: List<QuizOption>,
    val correctOptionId: String
)

data class CheckInStreak(
    val count: Int = 0,
    val freezeUsed: Boolean = false,
    val lastWeekId: String? = null
)

object WeeklySummaryBuilder {

    fun build(
        weekStart: Long,
        weekEnd: Long,
        weekId: String,
        previousSpendCents: Long,
        transactions: List<ChallengeTransaction>,
        now: Long,
        zone: ZoneId
    ): WeeklySummary {
        val inWeek = transactions.filter { it.timestampMillis >= weekStart && it.timestampMillis < weekEnd }
        var spend = 0L
        val categories = linkedMapOf<TransactionType, Long>()
        for (tx in inWeek) {
            val signed = signedSpendCents(tx)
            spend += signed
            if (tx.type.isOutgoing() && tx.amountCents > 0L) {
                categories[tx.type] = (categories[tx.type] ?: 0L) + tx.amountCents
            }
        }
        val ranked = categories
            .map { CategorySpend(it.key, it.value) }
            .sortedWith(compareByDescending<CategorySpend> { it.cents }.thenBy { it.type.name })
        val (quiet, counted) = quietDays(weekStart, weekEnd, inWeek, now, zone)
        return WeeklySummary(
            weekId = weekId,
            spendCents = spend,
            previousSpendCents = previousSpendCents,
            byCategory = ranked,
            quietDays = quiet,
            daysCounted = counted
        )
    }

    /** Outgoing amounts add. Reversals and negative amounts subtract. */
    fun signedSpendCents(tx: ChallengeTransaction): Long = when {
        tx.type == TransactionType.REVERSED -> -kotlin.math.abs(tx.amountCents)
        tx.amountCents < 0L -> tx.amountCents
        tx.type.isOutgoing() -> tx.amountCents
        else -> 0L
    }

    private fun quietDays(
        weekStart: Long,
        weekEnd: Long,
        transactions: List<ChallengeTransaction>,
        now: Long,
        zone: ZoneId
    ): Pair<Int, Int> {
        val lastInstant = if (now < weekEnd) now else weekEnd - 1
        if (lastInstant < weekStart) return 0 to 0
        var date = ChallengeClock.localDate(weekStart, zone)
        val lastDate = ChallengeClock.localDate(lastInstant, zone)
        var quiet = 0
        var counted = 0
        while (!date.isAfter(lastDate)) {
            counted++
            val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val spent = transactions.any { tx ->
                tx.timestampMillis >= dayStart &&
                    tx.timestampMillis < dayEnd &&
                    tx.type.isOutgoing() &&
                    tx.amountCents > 0L
            }
            if (!spent) quiet++
            date = date.plusDays(1)
        }
        return quiet to counted
    }
}

object QuizGenerator {

    fun generate(summary: WeeklySummary, categoryLabel: (TransactionType) -> String): List<QuizQuestion> {
        if (summary.spendCents == 0L && summary.previousSpendCents == 0L && summary.byCategory.isEmpty()) {
            return emptyList()
        }
        val questions = ArrayList<QuizQuestion>(3)
        topCategory(summary, categoryLabel)?.let { questions.add(it) }
        compare(summary)?.let { questions.add(it) }
        quietDays(summary)?.let { questions.add(it) }
        return questions
    }

    private fun topCategory(
        summary: WeeklySummary,
        categoryLabel: (TransactionType) -> String
    ): QuizQuestion? {
        val ranked = summary.byCategory.filter { it.cents > 0L }
        if (ranked.size < 2) return null
        val top = ranked.first()
        if (ranked.drop(1).any { it.cents == top.cents }) return null
        val options = ranked.take(4).map { QuizOption(it.type.name, categoryLabel(it.type)) }
        return QuizQuestion(
            id = "top_category",
            kind = QuizKind.TOP_CATEGORY,
            options = options,
            correctOptionId = top.type.name
        )
    }

    private fun compare(summary: WeeklySummary): QuizQuestion? {
        if (summary.spendCents == 0L && summary.previousSpendCents == 0L) return null
        val correct = when {
            summary.spendCents > summary.previousSpendCents -> SpendCompare.MORE
            summary.spendCents < summary.previousSpendCents -> SpendCompare.LESS
            else -> SpendCompare.SAME
        }
        return QuizQuestion(
            id = "week_compare",
            kind = QuizKind.WEEK_COMPARE,
            options = SpendCompare.entries.map { QuizOption(it.name, it.name) },
            correctOptionId = correct.name
        )
    }

    private fun quietDays(summary: WeeklySummary): QuizQuestion? {
        if (summary.daysCounted <= 0) return null
        if (summary.spendCents == 0L && summary.byCategory.isEmpty()) return null
        val correct = summary.quietDays
        val candidates = linkedSetOf(correct)
        if (correct > 0) candidates.add(correct - 1)
        if (correct + 1 <= summary.daysCounted) candidates.add(correct + 1)
        if (correct + 2 <= summary.daysCounted) candidates.add(correct + 2)
        while (candidates.size < 3 && candidates.size <= summary.daysCounted) {
            val next = (0..summary.daysCounted).firstOrNull { it !in candidates } ?: break
            candidates.add(next)
        }
        if (candidates.size < 2) return null
        return QuizQuestion(
            id = "quiet_days",
            kind = QuizKind.QUIET_DAYS,
            options = candidates.map { QuizOption(it.toString(), it.toString()) },
            correctOptionId = correct.toString()
        )
    }
}

object CheckInStreaks {

    /** How many ISO weeks separate [last] from [current]. 0 is the same week, 1 is the next week. */
    fun weekGap(last: String, current: String): Int {
        if (last == current) return 0
        var cursor = current
        var steps = 0
        while (steps < 80) {
            cursor = ChallengeClock.previousWeekId(cursor) ?: return Int.MAX_VALUE
            steps++
            if (cursor == last) return steps
        }
        return Int.MAX_VALUE
    }

    /**
     * One missed week is forgiven once per streak. Completing the following week
     * gives the freeze back. Completing the same week twice does not change the count.
     */
    fun advance(previous: CheckInStreak, completedWeekId: String): CheckInStreak {
        val last = previous.lastWeekId
        if (last == null) {
            return CheckInStreak(count = 1, freezeUsed = false, lastWeekId = completedWeekId)
        }
        if (last == completedWeekId) return previous
        val gap = weekGap(last, completedWeekId)
        return when {
            gap == 1 -> previous.copy(
                count = previous.count + 1,
                freezeUsed = false,
                lastWeekId = completedWeekId
            )
            gap == 2 && !previous.freezeUsed -> previous.copy(
                count = previous.count + 1,
                freezeUsed = true,
                lastWeekId = completedWeekId
            )
            else -> CheckInStreak(count = 1, freezeUsed = false, lastWeekId = completedWeekId)
        }
    }
}
