package com.lipabill.app.engage

import com.lipabill.app.data.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class WeeklyCheckInTest {

    private val zone = ZoneId.of("Africa/Nairobi")
    private val monday = LocalDate.of(2026, 10, 5)
    private val weekStart = monday.atStartOfDay(zone).toInstant().toEpochMilli()
    private val weekEnd = monday.plusDays(7).atStartOfDay(zone).toInstant().toEpochMilli()
    private val wednesdayNoon = monday.plusDays(2).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun empty_week_has_nothing_to_ask() {
        val summary = build(emptyList(), previous = 0L, now = wednesdayNoon)
        assertEquals(0L, summary.spendCents)
        assertTrue(summary.byCategory.isEmpty())
        assertEquals(summary.daysCounted, summary.quietDays)
        assertTrue(QuizGenerator.generate(summary) { it.name }.isEmpty())
    }

    @Test
    fun one_category_skips_the_top_question() {
        val summary = build(
            listOf(tx(1, weekStart + 3_600_000, TransactionType.SENT, 10_000)),
            previous = 0L,
            now = wednesdayNoon
        )
        val questions = QuizGenerator.generate(summary) { it.name }
        assertTrue(questions.none { it.kind == QuizKind.TOP_CATEGORY })
        assertEquals(SpendCompare.MORE.name, questions.first { it.kind == QuizKind.WEEK_COMPARE }.correctOptionId)
    }

    @Test
    fun a_tie_skips_the_top_question() {
        val summary = build(
            listOf(
                tx(1, weekStart + 1_000, TransactionType.SENT, 8_000),
                tx(2, weekStart + 2_000, TransactionType.PAYBILL, 8_000)
            ),
            previous = 8_000,
            now = wednesdayNoon
        )
        val questions = QuizGenerator.generate(summary) { it.name }
        assertTrue(questions.none { it.kind == QuizKind.TOP_CATEGORY })
        assertEquals(SpendCompare.MORE.name, questions.first { it.kind == QuizKind.WEEK_COMPARE }.correctOptionId)
    }

    @Test
    fun reversal_reduces_the_week_total() {
        val summary = build(
            listOf(
                tx(1, weekStart + 1_000, TransactionType.BUY_GOODS, 5_000),
                tx(2, weekStart + 2_000, TransactionType.REVERSED, 2_000)
            ),
            previous = 5_000,
            now = wednesdayNoon
        )
        assertEquals(3_000L, summary.spendCents)
        assertEquals(1, summary.byCategory.size)
        assertEquals(TransactionType.BUY_GOODS, summary.byCategory.first().type)
        val compare = QuizGenerator.generate(summary) { it.name }
            .first { it.kind == QuizKind.WEEK_COMPARE }
        assertEquals(SpendCompare.LESS.name, compare.correctOptionId)
    }

    @Test
    fun same_week_does_not_increment_the_streak() {
        val current = CheckInStreak(count = 3, freezeUsed = false, lastWeekId = "2026-W41")
        assertEquals(current, CheckInStreaks.advance(current, "2026-W41"))
    }

    @Test
    fun the_next_week_adds_one_and_returns_the_freeze() {
        val next = CheckInStreaks.advance(
            CheckInStreak(count = 2, freezeUsed = true, lastWeekId = "2026-W40"),
            "2026-W41"
        )
        assertEquals(3, next.count)
        assertEquals(false, next.freezeUsed)
    }

    @Test
    fun one_missed_week_is_forgiven_once() {
        val forgiven = CheckInStreaks.advance(
            CheckInStreak(count = 4, freezeUsed = false, lastWeekId = "2026-W39"),
            "2026-W41"
        )
        assertEquals(5, forgiven.count)
        assertTrue(forgiven.freezeUsed)
        val reset = CheckInStreaks.advance(
            forgiven.copy(lastWeekId = "2026-W39"),
            "2026-W41"
        )
        assertEquals(1, reset.count)
        assertEquals(false, reset.freezeUsed)
    }

    @Test
    fun year_boundary_weeks_are_one_apart() {
        assertEquals("2025-W52", ChallengeClock.previousWeekId("2026-W01"))
        assertEquals(1, CheckInStreaks.weekGap("2025-W52", "2026-W01"))
    }

    private fun build(
        transactions: List<ChallengeTransaction>,
        previous: Long,
        now: Long
    ) = WeeklySummaryBuilder.build(
        weekStart = weekStart,
        weekEnd = weekEnd,
        weekId = ChallengeClock.isoWeekId(weekStart, zone),
        previousSpendCents = previous,
        transactions = transactions,
        now = now,
        zone = zone
    )

    private fun tx(id: Long, at: Long, type: TransactionType, cents: Long) =
        ChallengeTransaction(id, at, "Shop", type, cents)
}
