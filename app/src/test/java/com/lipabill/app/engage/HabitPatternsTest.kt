package com.lipabill.app.engage

import com.lipabill.app.data.model.TransactionType
import com.lipabill.app.viewmodel.ChallengeSubject
import com.lipabill.app.viewmodel.challengeOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

class HabitPatternsTest {

    private val zone = ZoneId.of("Africa/Nairobi")
    private val day = 24L * 60L * 60L * 1000L
    /** Wednesday 11 Nov 2026, noon. */
    private val now = LocalDate.of(2026, 11, 11).atStartOfDay(zone).toInstant().toEpochMilli() + 12 * 3_600_000L
    private var nextId = 1L

    private fun tx(at: Long, name: String?, type: TransactionType, cents: Long) =
        ChallengeTransaction(nextId++, at, name, type, cents)

    private fun on(date: LocalDate, hour: Int = 20) =
        date.atStartOfDay(zone).toInstant().toEpochMilli() + hour * 3_600_000L

    @Test
    fun food_orders_bunched_on_one_day_are_a_pattern() {
        val fridays = (1..5).map { tx(on(LocalDate.of(2026, 11, 6).minusWeeks(it.toLong() - 1)), "GLOVO KE", TransactionType.BUY_GOODS, 60_000) }
        val tuesday = tx(on(LocalDate.of(2026, 11, 3)), "Uber Eats", TransactionType.BUY_GOODS, 40_000)
        val pattern = HabitPatterns.detect(fridays + tuesday, now, zone).first()
        pattern as HabitPattern.FoodPeakDay
        assertEquals(DayOfWeek.FRIDAY, pattern.day)
        assertEquals(83, pattern.sharePercent)
    }

    @Test
    fun scattered_or_few_food_orders_are_not() {
        val few = (0..2).map { tx(now - it * day, "Glovo", TransactionType.BUY_GOODS, 50_000) }
        assertTrue(HabitPatterns.detect(few, now, zone).none { it is HabitPattern.FoodPeakDay })
        val spread = (0..6).map { tx(now - it * day, "Glovo", TransactionType.BUY_GOODS, 50_000) }
        assertTrue(HabitPatterns.detect(spread, now, zone).none { it is HabitPattern.FoodPeakDay })
    }

    @Test
    fun late_month_fuliza_in_two_months_is_a_pattern() {
        val rows = listOf(
            tx(on(LocalDate.of(2026, 9, 24)), null, TransactionType.FULIZA, 30_000),
            tx(on(LocalDate.of(2026, 10, 27)), null, TransactionType.FULIZA, 50_000),
            tx(on(LocalDate.of(2026, 11, 2)), "Naivas", TransactionType.BUY_GOODS, 10_000)
        )
        val pattern = HabitPatterns.detect(rows, now, zone).filterIsInstance<HabitPattern.FulizaLateMonth>().single()
        assertEquals(2, pattern.lateMonths)
        assertEquals(3, pattern.months)
        assertEquals(2, pattern.draws)
    }

    @Test
    fun regulars_are_shops_only_and_skip_food_delivery() {
        val rows = (0..4).map { tx(now - it * day, "Java House", TransactionType.BUY_GOODS, 45_000) } +
            (0..5).map { tx(now - it * day, "KPLC", TransactionType.PAYBILL, 100_000) } +
            (0..5).map { tx(now - it * day, "Glovo", TransactionType.BUY_GOODS, 50_000) } +
            (0..3).map { tx(now - 40 * day - it * day, "Naivas", TransactionType.BUY_GOODS, 50_000) }
        val regulars = HabitPatterns.regulars(rows, now)
        assertEquals(listOf("Java House"), regulars.map { it.payee })
        assertEquals(225_000L, regulars.single().cents)
    }

    @Test
    fun groceries_and_marked_essentials_are_never_suggested() {
        val rows = (0..4).map { tx(now - it * day, "GRAMOS SUPERMARKET LIMITED", TransactionType.BUY_GOODS, 40_000) } +
            (0..4).map { tx(now - it * day, "Naivas Westlands", TransactionType.BUY_GOODS, 40_000) } +
            (0..4).map { tx(now - it * day, "Java  House", TransactionType.BUY_GOODS, 45_000) }
        assertEquals(listOf("Java  House"), HabitPatterns.regulars(rows, now).map { it.payee })
        assertTrue(HabitPatterns.regulars(rows, now, essentialKeys = setOf("java house")).isEmpty())
    }

    @Test
    fun airtime_drip_needs_ten_top_ups_in_a_month() {
        val nine = (0..8).map { tx(now - it * day, null, TransactionType.AIRTIME, 5_000) }
        assertTrue(HabitPatterns.detect(nine, now, zone).none { it is HabitPattern.AirtimeDrip })
        val ten = nine + tx(now - 9 * day, null, TransactionType.AIRTIME, 5_000)
        val drip = HabitPatterns.detect(ten, now, zone).filterIsInstance<HabitPattern.AirtimeDrip>().single()
        assertEquals(10, drip.count)
        assertEquals(5_000L, drip.averageCents)
    }

    @Test
    fun payday_rush_needs_two_credit_then_spend_spikes() {
        val credit1 = on(LocalDate.of(2026, 9, 28))
        val credit2 = on(LocalDate.of(2026, 10, 28))
        val rows = listOf(
            tx(credit1, "Payroll", TransactionType.RECEIVED, 50_000_00L),
            tx(credit1 + 3_600_000L, "Glovo", TransactionType.BUY_GOODS, 80_000),
            tx(credit1 + 5_600_000L, "Java House", TransactionType.BUY_GOODS, 90_000),
            tx(credit2, "Payroll", TransactionType.RECEIVED, 55_000_00L),
            tx(credit2 + 2_600_000L, "Uber Eats", TransactionType.BUY_GOODS, 2_000_00L)
        )
        val rush = HabitPatterns.detect(rows, now, zone).filterIsInstance<HabitPattern.PaydayRush>().single()
        assertEquals(2, rush.events)
        assertTrue(rush.spendCents >= 150_000L)
    }

    @Test
    fun one_payday_spike_is_not_a_pattern() {
        val credit = on(LocalDate.of(2026, 10, 28))
        val rows = listOf(
            tx(credit, "Payroll", TransactionType.RECEIVED, 50_000_00L),
            tx(credit + 2_600_000L, "Glovo", TransactionType.BUY_GOODS, 2_000_00L)
        )
        assertTrue(HabitPatterns.detect(rows, now, zone).none { it is HabitPattern.PaydayRush })
    }

    @Test
    fun payday_spend_under_floor_or_small_credit_is_ignored() {
        val credit = on(LocalDate.of(2026, 10, 28))
        val smallSpend = listOf(
            tx(credit, "Payroll", TransactionType.RECEIVED, 50_000_00L),
            tx(credit + 2_600_000L, "Glovo", TransactionType.BUY_GOODS, 1_000_00L)
        )
        assertTrue(HabitPatterns.detect(smallSpend, now, zone).none { it is HabitPattern.PaydayRush })
        val smallCredit = listOf(
            tx(credit, "Payroll", TransactionType.RECEIVED, 4_000_00L),
            tx(credit + 2_600_000L, "Glovo", TransactionType.BUY_GOODS, 2_000_00L)
        )
        assertTrue(HabitPatterns.detect(smallCredit, now, zone).none { it is HabitPattern.PaydayRush })
    }

    @Test
    fun detect_ignores_rows_older_than_window() {
        val old = now - (HabitPatterns.WINDOW_DAYS + 5) * day
        val rows = listOf(
            tx(old, "GLOVO KE", TransactionType.BUY_GOODS, 60_000),
            tx(old - day, "GLOVO KE", TransactionType.BUY_GOODS, 60_000),
            tx(old - 2 * day, "GLOVO KE", TransactionType.BUY_GOODS, 60_000),
            tx(old - 3 * day, "GLOVO KE", TransactionType.BUY_GOODS, 60_000),
            tx(old - 4 * day, "GLOVO KE", TransactionType.BUY_GOODS, 60_000)
        )
        assertTrue(HabitPatterns.detect(rows, now, zone).none { it is HabitPattern.FoodPeakDay })
    }

    @Test
    fun catch_up_needs_a_previous_visit_three_days_back() {
        val rows = listOf(
            tx(now - 2 * day, "Naivas", TransactionType.BUY_GOODS, 30_000),
            tx(now - day, null, TransactionType.RECEIVED, 100_000)
        )
        assertNull(HabitPatterns.catchUp(rows, lastVisit = 0L, now = now))
        assertNull(HabitPatterns.catchUp(rows, lastVisit = now - day, now = now))
        val away = HabitPatterns.catchUp(rows, lastVisit = now - 5 * day, now = now)!!
        assertEquals(5, away.daysAway)
        assertEquals(2, away.payments)
        assertEquals(30_000L, away.outCents)
        assertEquals(1, away.newRegulars)
    }

    @Test
    fun catch_up_flags_kplc_when_essentials_have_monthly_cadence() {
        val rows = listOf(
            tx(on(LocalDate.of(2026, 9, 10)), "KPLC Prepaid", TransactionType.PAYBILL, 80_000),
            tx(on(LocalDate.of(2026, 10, 8)), "KPLC Prepaid", TransactionType.PAYBILL, 90_000),
            tx(now - 2 * day, "New Cafe", TransactionType.BUY_GOODS, 20_000)
        )
        val away = HabitPatterns.catchUp(rows, lastVisit = now - 5 * day, now = now)!!
        assertEquals("KPLC", away.nextBillLabel)
        assertEquals(1, away.newRegulars)
    }

    @Test
    fun learning_score_is_clamped() {
        assertEquals(10, HabitPatterns.learningScore(0, 0, 0))
        assertEquals(100, HabitPatterns.learningScore(0, 20, 20))
        assertEquals(0, HabitPatterns.learningScore(50, 0, 0))
        assertTrue(HabitPatterns.learningScore(1, 2, 1) in 0..100)
    }

    @Test
    fun food_day_bars_are_mon_to_sun_or_empty() {
        assertTrue(HabitPatterns.foodDayBars(emptyList(), zone, now).isEmpty())
        val friday = on(LocalDate.of(2026, 11, 6))
        val monday = on(LocalDate.of(2026, 11, 9))
        val bars = HabitPatterns.foodDayBars(
            listOf(
                tx(friday, "Glovo", TransactionType.BUY_GOODS, 40_000),
                tx(friday + 1_000, "Uber Eats", TransactionType.BUY_GOODS, 30_000),
                tx(monday, "Glovo", TransactionType.BUY_GOODS, 20_000)
            ),
            zone,
            now
        )
        assertEquals(7, bars.size)
        assertEquals(2, bars[DayOfWeek.FRIDAY.value - 1])
        assertEquals(1, bars[DayOfWeek.MONDAY.value - 1])
        assertEquals(0, bars[DayOfWeek.SUNDAY.value - 1])
    }

    @Test
    fun fees_dodged_is_the_usual_weekly_fee_per_kept_zero_fee_week() {
        assertEquals(0L, HabitPatterns.feesDodgedCents(emptyList(), emptyList()))
        // Four weeks of history with KSh 30 a week in fees.
        val rows = (0..3).map {
            ChallengeTransaction(100L + it, now - it * 7 * day, "Jane", TransactionType.SENT, 100_000, feeCents = 3_000)
        }
        val kept = listOf(ChallengeTemplates.zeroFee7Days.id)
        assertEquals(4_000L, HabitPatterns.feesDodgedCents(rows, kept))
        // Kept, but no fee history: nothing is invented.
        val noFees = rows.map { it.copy(feeCents = 0L) }
        assertEquals(0L, HabitPatterns.feesDodgedCents(noFees, kept))
        // Fuliza weeks are about borrowing, not transaction costs.
        assertEquals(0L, HabitPatterns.feesDodgedCents(rows, listOf(ChallengeTemplates.fulizaFree7Days.id)))
    }

    @Test
    fun options_put_the_strongest_pattern_first() {
        val regular = HabitPattern.FrequentPayee("Java House", 5, 225_000)
        val options = challengeOptions(
            patterns = listOf(HabitPattern.FulizaLateMonth(2, 3, 2), regular),
            regulars = listOf(regular)
        )
        assertEquals(ChallengeSubject.FULIZA, options.first().subject)
        assertTrue(options.first().suggested)
        assertEquals(1, options.count { it.suggested })
        assertEquals("Java House", options.single { it.subject == ChallengeSubject.PAYEE }.payee)
        assertTrue(options.any { it.templateId == ChallengeTemplates.quietDays7.id })
        assertTrue(options.any { it.templateId == ChallengeTemplates.zeroFee7Days.id })
        assertTrue(options.any { it.templateId == ChallengeTemplates.fulizaUntilPayday(7).id })
    }

    @Test
    fun options_include_payday_and_diy() {
        val options = challengeOptions(
            patterns = listOf(HabitPattern.PaydayRush(2, 400_000)),
            regulars = emptyList(),
            diyIds = setOf(ChallengeTemplates.diy(CategoryRules.foodDelivery.categoryId, 4, 0L).id)
        )
        assertEquals(ChallengeSubject.PAYDAY, options.first().subject)
        assertTrue(options.any { it.subject == ChallengeSubject.DIY })
    }
}
