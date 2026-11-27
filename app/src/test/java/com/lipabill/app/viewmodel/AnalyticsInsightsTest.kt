package com.lipabill.app.viewmodel

import com.lipabill.app.testTransaction
import com.lipabill.app.data.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

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
        assertEquals("+18% vs last time", vsPriorCopy(118.0, 100.0, comparable = true))
        assertEquals("-25% vs last time", vsPriorCopy(75.0, 100.0, comparable = true))
        assertEquals("New since last time", vsPriorCopy(40.0, 0.0, comparable = true))
        assertNull(vsPriorCopy(40.0, 10.0, comparable = false))
    }

    @Test
    fun percent_change_and_pulse_bounds() {
        assertEquals(18, percentChange(118.0, 100.0))
        assertNull(percentChange(40.0, 0.0))
        assertTrue(shouldShowPulse(1))
        assertTrue(shouldShowPulse(31))
        assertFalse(shouldShowPulse(32))
        assertFalse(shouldShowPulse(0))
    }

    @Test
    fun busiest_outflow_picks_the_largest_day_in_range() {
        val from = LocalDate.of(2026, 11, 1)
        val to = LocalDate.of(2026, 11, 10)
        val busiest = busiestOutflow(
            mapOf(
                LocalDate.of(2026, 11, 3) to 400.0,
                LocalDate.of(2026, 11, 7) to 900.0,
                LocalDate.of(2026, 10, 31) to 5_000.0
            ),
            from,
            to
        )
        assertEquals(LocalDate.of(2026, 11, 7) to 900.0, busiest)
        assertNull(busiestOutflow(emptyMap(), from, to))
    }

    @Test
    fun due_copy_and_spend_share_edges() {
        val today = LocalDate.of(2026, 11, 11)
        assertEquals("Due today", dueCopy(today, today))
        assertTrue(dueCopy(today.plusDays(5), today).startsWith("Due "))
        assertEquals(0.5, spendShareOfIncome(500.0, 1_000.0)!!, 0.0)
        assertNull(spendShareOfIncome(500.0, 0.0))
        assertEquals(0.0, dailyAverage(100.0, 0), 0.0)
        assertEquals(10.0, dailyAverage(100.0, 10), 0.0)
    }

    @Test
    fun pulse_calendar_empty_past_31_days_and_marks_due() {
        val today = LocalDate.of(2026, 11, 11)
        assertTrue(
            pulseCalendar(
                from = today.minusDays(40),
                to = today,
                today = today,
                inflowByDate = emptyMap(),
                outflowByDate = emptyMap()
            ).isEmpty()
        )
        val regular = RegularPayee(
            key = "bill:1",
            name = "Fibre",
            cadence = Cadence.MONTHLY,
            typicalAmount = 100.0,
            monthlyCost = 100.0,
            lastPaid = today.minusDays(30),
            nextDue = today,
            payments = 3
        )
        val cells = pulseCalendar(
            from = today.minusDays(6),
            to = today,
            today = today,
            inflowByDate = mapOf(today to 200.0),
            outflowByDate = mapOf(today.minusDays(1) to 50.0),
            regulars = listOf(regular)
        )
        assertTrue(cells.isNotEmpty())
        assertTrue(cells.any { it.isToday && it.due.single().name == "Fibre" })
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
    fun period_span_names_the_months() {
        assertEquals(
            "1–10 Nov 2026",
            formatPeriodSpan(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 10))
        )
        assertEquals(
            "12 Oct – 10 Nov 2026",
            formatPeriodSpan(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 11, 10))
        )
        assertEquals(
            "12 Dec 2025 – 10 Jan 2026",
            formatPeriodSpan(LocalDate.of(2025, 12, 12), LocalDate.of(2026, 1, 10))
        )
        assertEquals(
            "12 Oct – 10 Nov",
            shortPeriodSpan(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 11, 10))
        )
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
        assertTrue(cells.first { it.date == from.plusDays(1) }.isFuture)
        assertFalse(cells.first { it.date == from.plusDays(1) }.isActive)
    }

    @Test
    fun month_pulse_calendar_starts_on_the_first_and_greys_future_days() {
        val today = LocalDate.of(2026, 11, 17)
        val month = YearMonth.of(2026, 11)
        val cells = monthPulseCalendar(
            month = month,
            today = today,
            inflowByDate = mapOf(LocalDate.of(2026, 11, 3) to 500.0),
            outflowByDate = mapOf(LocalDate.of(2026, 11, 10) to 200.0),
            activeFrom = month.atDay(1),
            activeTo = today
        )
        val active = cells.filter { it.isActive }
        assertEquals(1, active.first().date.dayOfMonth)
        assertEquals(17, active.last().date.dayOfMonth)
        assertEquals(DayOfWeek.MONDAY, cells.first().date.dayOfWeek)
        assertEquals(DayOfWeek.SUNDAY, cells.last().date.dayOfWeek)
        assertTrue(cells.first { it.date == today }.isToday)
        assertTrue(cells.first { it.date == today.plusDays(1) }.isFuture)
        assertFalse(cells.first { it.date == today.plusDays(1) }.isActive)
        assertTrue(cells.first { it.date == LocalDate.of(2026, 11, 10) }.isActive)
        assertEquals(200.0, cells.first { it.date == LocalDate.of(2026, 11, 10) }.outflow, 0.0)
        // Padding days from adjacent months stay out of range.
        assertTrue(cells.any { !it.inRange && it.date.monthValue == 10 })
    }

    @Test
    fun month_pulse_respects_a_shorter_filter_window() {
        val today = LocalDate.of(2026, 11, 17)
        val cells = monthPulseCalendar(
            month = YearMonth.of(2026, 11),
            today = today,
            inflowByDate = emptyMap(),
            outflowByDate = mapOf(
                LocalDate.of(2026, 11, 5) to 50.0,
                LocalDate.of(2026, 11, 15) to 80.0
            ),
            activeFrom = today.minusDays(6),
            activeTo = today
        )
        assertFalse(cells.first { it.date == LocalDate.of(2026, 11, 5) }.isActive)
        assertTrue(cells.first { it.date == LocalDate.of(2026, 11, 15) }.isActive)
        assertEquals(80.0, cells.first { it.date == LocalDate.of(2026, 11, 15) }.outflow, 0.0)
    }

    @Test
    fun this_month_prior_bounds_are_the_previous_calendar_month() {
        val from = LocalDate.of(2026, 11, 1)
        val to = LocalDate.of(2026, 11, 17)
        assertEquals(
            LocalDate.of(2026, 10, 1) to LocalDate.of(2026, 10, 31),
            previousAnalyticsBounds(AnalyticsRange.THIS_MONTH, from, to)
        )
    }

    @Test
    fun whole_custom_month_prior_is_the_previous_calendar_month() {
        // Past months from the calendar swipe are CUSTOM 1st→end, not a rolling window.
        assertTrue(isWholeCalendarMonth(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)))
        assertFalse(isWholeCalendarMonth(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 17)))
        assertEquals(
            LocalDate.of(2026, 9, 1) to LocalDate.of(2026, 9, 30),
            previousAnalyticsBounds(
                AnalyticsRange.CUSTOM,
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 31)
            )
        )
        // February (non-leap) still maps to a full January.
        assertEquals(
            LocalDate.of(2025, 1, 1) to LocalDate.of(2025, 1, 31),
            previousAnalyticsBounds(
                AnalyticsRange.CUSTOM,
                LocalDate.of(2025, 2, 1),
                LocalDate.of(2025, 2, 28)
            )
        )
    }

    @Test
    fun partial_custom_range_keeps_a_same_length_prior() {
        val from = LocalDate.of(2026, 10, 5)
        val to = LocalDate.of(2026, 10, 20)
        assertEquals(
            LocalDate.of(2026, 9, 19) to LocalDate.of(2026, 10, 4),
            previousAnalyticsBounds(AnalyticsRange.CUSTOM, from, to)
        )
    }

    @Test
    fun past_month_pulse_prefills_ledger_amounts() {
        val today = LocalDate.of(2026, 11, 17)
        val cells = monthPulseCalendar(
            month = YearMonth.of(2026, 10),
            today = today,
            inflowByDate = mapOf(LocalDate.of(2026, 10, 5) to 1_000.0),
            outflowByDate = mapOf(LocalDate.of(2026, 10, 12) to 350.0)
        )
        assertFalse(cells.any { it.isFuture && it.inRange })
        assertEquals(1_000.0, cells.first { it.date == LocalDate.of(2026, 10, 5) }.inflow, 0.0)
        assertEquals(350.0, cells.first { it.date == LocalDate.of(2026, 10, 12) }.outflow, 0.0)
        assertTrue(cells.first { it.date == LocalDate.of(2026, 10, 12) }.isActive)
    }

    @Test
    fun pulse_month_span_covers_ledger_and_upcoming_dues() {
        val today = LocalDate.of(2026, 11, 17)
        val months = pulseMonthSpan(
            today = today,
            inflowByDate = mapOf(LocalDate.of(2025, 6, 1) to 10.0),
            outflowByDate = emptyMap(),
            regulars = listOf(
                RegularPayee(
                    key = "bill:1",
                    name = "Fibre",
                    cadence = Cadence.MONTHLY,
                    typicalAmount = 100.0,
                    monthlyCost = 100.0,
                    lastPaid = today.minusDays(30),
                    nextDue = LocalDate.of(2027, 1, 5),
                    payments = 3
                )
            ),
            pastMonths = 24,
            futureMonths = 3
        )
        assertEquals(YearMonth.of(2025, 6), months.first())
        assertTrue(months.contains(YearMonth.of(2026, 11)))
        assertTrue(months.contains(YearMonth.of(2027, 1)))
        assertEquals(YearMonth.of(2027, 2), months.last())
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
        assertEquals("Nothing went out up to today.", story.detail)
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
        assertEquals("KPLC got the most.", story.headline)
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
        assertEquals("Your summary builds from your payment messages.", story.headline)
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
        assertEquals("4 days without Fuliza.", story.headline)
        assertEquals("Nothing borrowed up to the end of this period.", story.detail)
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
                sample(TransactionType.BUY_GOODS, null, 50.0, phone = "0700111222", code = "F"),
                sample(TransactionType.SENT, "JANE ", 100.0, code = "G"),
                sample(TransactionType.WITHDRAW, "Agent", 5_000.0, code = "H"),
                sample(TransactionType.AIRTIME, null, 80.0, code = "I")
            )
        )
        assertEquals(
            listOf("Jane", "KPLC", "0700111222"),
            ranked.map { it.name }
        )
        assertEquals(600.0, ranked[0].total, 0.0)
        assertEquals(3, ranked[0].count)
        assertTrue(!ranked[0].business)
        assertTrue(ranked[1].business)
        assertTrue(ranked[2].business)
    }

    @Test
    fun outgoing_payee_split_separates_people_and_businesses() {
        val split = outgoingPayeeSplit(
            listOf(
                sample(TransactionType.SENT, "Mary Wanjiku", 500.0, phone = "0711000111"),
                sample(TransactionType.SENT, "JOHN KAMAU", 300.0, phone = "0712345678", code = "B"),
                sample(TransactionType.PAYBILL, "Safaricom Limited", 200.0, code = "C"),
                sample(TransactionType.BUY_GOODS, "Java House", 150.0, code = "D"),
                sample(TransactionType.AIRTIME, "Airtime", 50.0, code = "E")
            )
        )
        assertEquals(listOf("Mary Wanjiku", "JOHN KAMAU"), split.people.map { it.name })
        assertEquals(800.0, split.peopleTotal, 0.0)
        assertEquals(listOf("Safaricom Limited", "Java House", "Airtime"), split.businesses.map { it.name })
        assertEquals(400.0, split.businessTotal, 0.0)
    }

    private fun sample(
        type: TransactionType,
        name: String?,
        amount: Double,
        phone: String? = null,
        code: String = "A"
    ) = testTransaction(
        code = code,
        type = type,
        amount = amount,
        counterpartyName = name,
        counterpartyPhone = phone,
        timestampMillis = 1L
    )
}
