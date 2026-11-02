package com.lipabill.app.viewmodel

import com.lipabill.app.data.model.MpesaTransaction
import com.lipabill.app.data.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class AnalyticsInsightsTest {

    @Test
    fun rolling_window_uses_the_same_length_immediately_before() {
        val from = LocalDate.of(2026, 9, 25)
        val to = LocalDate.of(2026, 10, 24)
        val previous = previousAnalyticsBounds(AnalyticsRange.DAYS_30, from, to)
        assertEquals(LocalDate.of(2026, 8, 26) to LocalDate.of(2026, 9, 24), previous)
    }

    @Test
    fun this_year_compares_the_same_dates_last_year() {
        val from = LocalDate.of(2026, 1, 1)
        val to = LocalDate.of(2026, 10, 24)
        val previous = previousAnalyticsBounds(AnalyticsRange.THIS_YEAR, from, to)
        assertEquals(LocalDate.of(2025, 1, 1) to LocalDate.of(2025, 10, 24), previous)
    }

    @Test
    fun all_time_has_no_prior_window() {
        assertNull(
            previousAnalyticsBounds(
                AnalyticsRange.ALL,
                LocalDate.of(2010, 1, 1),
                LocalDate.of(2026, 10, 24)
            )
        )
    }

    @Test
    fun vs_prior_copy_rounds_percent_and_marks_new_activity() {
        assertEquals("+18% vs prior", vsPriorCopy(118.0, 100.0, comparable = true))
        assertEquals("-25% vs prior", vsPriorCopy(75.0, 100.0, comparable = true))
        assertEquals("New vs prior", vsPriorCopy(40.0, 0.0, comparable = true))
        assertNull(vsPriorCopy(40.0, 10.0, comparable = false))
    }

    @Test
    fun daily_average_divides_by_inclusive_day_count() {
        assertEquals(10.0, dailyAverage(300.0, 30), 0.0)
        assertEquals(0.0, dailyAverage(50.0, 0), 0.0)
    }

    @Test
    fun spend_share_needs_income() {
        assertEquals(0.8, spendShareOfIncome(80.0, 100.0)!!, 0.0)
        assertNull(spendShareOfIncome(80.0, 0.0))
    }

    @Test
    fun quiet_days_subtract_outflow_dates_inside_the_window() {
        val from = LocalDate.of(2026, 10, 1)
        val to = LocalDate.of(2026, 10, 7)
        val outflow = setOf(
            LocalDate.of(2026, 10, 2),
            LocalDate.of(2026, 10, 4),
            LocalDate.of(2026, 9, 30)
        )
        assertEquals(5, quietDaysInRange(from, to, outflow))
    }

    @Test
    fun quiet_streak_counts_from_the_day_after_the_last_outflow() {
        val end = LocalDate.of(2026, 10, 28)
        val outflow = setOf(LocalDate.of(2026, 10, 25))
        assertEquals(3, quietStreakEnding(end, outflow, ledgerStart = LocalDate.of(2026, 1, 1)))
        assertEquals(
            0,
            quietStreakEnding(end, setOf(end), ledgerStart = LocalDate.of(2026, 1, 1))
        )
    }

    @Test
    fun quiet_streak_without_outflow_runs_from_ledger_start() {
        val end = LocalDate.of(2026, 10, 10)
        assertEquals(
            10,
            quietStreakEnding(
                end,
                outflowDates = emptySet(),
                ledgerStart = LocalDate.of(2026, 10, 1)
            )
        )
        assertEquals(0, quietStreakEnding(end, emptySet(), ledgerStart = null))
    }

    @Test
    fun calendar_amount_label_is_compact_like_a_fare() {
        assertEquals("", calendarAmountLabel(0.0))
        assertEquals("450", calendarAmountLabel(450.4))
        assertEquals("4,200", calendarAmountLabel(4200.0))
        assertEquals("12k", calendarAmountLabel(12_450.0))
        assertEquals("1.5M", calendarAmountLabel(1_500_000.0))
    }

    @Test
    fun pulse_calendar_pads_monday_to_sunday() {
        val from = LocalDate.of(2026, 10, 28)
        val to = LocalDate.of(2026, 11, 3)
        val cells = pulseCalendar(
            from,
            to,
            today = from,
            inflowByDate = emptyMap(),
            outflowByDate = mapOf(from to 200.0)
        )
        assertEquals(DayOfWeek.MONDAY, cells.first().date.dayOfWeek)
        assertEquals(DayOfWeek.SUNDAY, cells.last().date.dayOfWeek)
        assertEquals(0, cells.size % 7)
        assertTrue(cells.first { it.date == from }.hasOutflow)
        assertTrue(cells.first { it.date == from }.isToday)
        assertFalse(cells.first().inRange)
    }

    @Test
    fun story_leads_with_a_quiet_streak_before_who_took_the_most() {
        val story = moneyStory(
            fuliza = false,
            hasLedger = true,
            quietDays = 4,
            quietStreak = 5,
            priorQuietDays = 1,
            expense = 800.0,
            income = 1000.0,
            previousExpense = 400.0,
            comparable = true,
            topName = "KPLC",
            topShare = 0.7,
            busiestLabel = "12 Oct",
            endsToday = true
        )
        assertEquals("5 quiet days in a row.", story.headline)
        assertEquals("No money out through today.", story.detail)
    }

    @Test
    fun story_names_who_shaped_the_stretch_when_there_is_no_streak() {
        val story = moneyStory(
            fuliza = false,
            hasLedger = true,
            quietDays = 1,
            quietStreak = 0,
            priorQuietDays = 1,
            expense = 800.0,
            income = 1000.0,
            previousExpense = 800.0,
            comparable = true,
            topName = "KPLC",
            topShare = 0.55,
            busiestLabel = "12 Oct",
            endsToday = true
        )
        assertEquals("KPLC shaped this stretch.", story.headline)
    }

    @Test
    fun empty_ledger_does_not_invent_quiet_days() {
        val story = moneyStory(
            fuliza = false,
            hasLedger = false,
            quietDays = 7,
            quietStreak = 7,
            priorQuietDays = null,
            expense = 0.0,
            income = 0.0,
            previousExpense = 0.0,
            comparable = false,
            topName = null,
            topShare = null,
            busiestLabel = null,
            endsToday = true
        )
        assertEquals("The picture fills from your texts.", story.headline)
    }

    @Test
    fun fuliza_story_uses_draw_language() {
        val story = moneyStory(
            fuliza = true,
            hasLedger = true,
            quietDays = 6,
            quietStreak = 4,
            priorQuietDays = 1,
            expense = 20.0,
            income = 400.0,
            previousExpense = 80.0,
            comparable = true,
            topName = null,
            topShare = null,
            busiestLabel = "12 Oct",
            endsToday = false
        )
        assertEquals("4 days without a Fuliza draw.", story.headline)
        assertEquals("Quiet through the end of this stretch.", story.detail)
    }

    @Test
    fun top_counterparties_rank_outgoing_spend_and_skip_savings() {
        val ranked = topOutgoingCounterparties(
            listOf(
                sample(TransactionType.SENT, "Jane", 400.0),
                sample(TransactionType.SENT, "Jane", 100.0, code = "B"),
                sample(TransactionType.PAYBILL, "KPLC", 200.0, code = "C"),
                sample(TransactionType.RECEIVED, "Jane", 900.0, code = "D"),
                sample(TransactionType.SAVINGS, "M-Shwari", 700.0, code = "E"),
                sample(TransactionType.BUY_GOODS, null, 50.0, phone = "0700111222", code = "F")
            )
        )
        assertEquals(
            listOf("Jane", "KPLC", "0700111222"),
            ranked.map { it.name }
        )
        assertEquals(500.0, ranked[0].total, 0.0)
        assertEquals(2, ranked[0].count)
    }

    private fun sample(
        type: TransactionType,
        name: String?,
        amount: Double,
        phone: String? = null,
        code: String = "A"
    ) = MpesaTransaction(
        id = 0,
        code = code,
        type = type,
        amount = amount,
        counterpartyName = name,
        counterpartyPhone = phone,
        timestampMillis = 1L,
        balance = null,
        cost = null,
        rawBody = ""
    )
}
