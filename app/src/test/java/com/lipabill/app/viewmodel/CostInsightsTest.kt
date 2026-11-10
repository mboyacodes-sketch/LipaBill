package com.lipabill.app.viewmodel

import com.lipabill.app.data.model.MpesaTransaction
import com.lipabill.app.data.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class CostInsightsTest {

    private val zone = ZoneOffset.UTC

    @Test
    fun reversal_and_the_payment_it_undid_both_drop_out() {
        val paid = tx(TransactionType.SENT, "Jane", 1_500.0, LocalDate.of(2026, 11, 2), code = "QK12ABC34D")
        val reversal = tx(
            TransactionType.REVERSED,
            null,
            1_500.0,
            LocalDate.of(2026, 11, 3),
            code = "QK13XYZ99Z",
            raw = "QK13XYZ99Z Confirmed. Transaction QK12ABC34D has been reversed. " +
                "Your original transaction QK12ABC34D in favour of JANE DOE has been reversed."
        )
        val kept = tx(TransactionType.PAYBILL, "KPLC", 900.0, LocalDate.of(2026, 11, 4), code = "QK14KPL00A")

        val clean = withoutReversals(listOf(paid, reversal, kept))

        assertEquals(listOf(kept), clean)
        assertEquals("QK12ABC34D", reversedOriginalCode(reversal))
    }

    @Test
    fun reversal_without_a_matching_payment_is_not_income() {
        val reversal = tx(TransactionType.REVERSED, null, 300.0, LocalDate.of(2026, 11, 3), raw = "reversed")
        assertTrue(withoutReversals(listOf(reversal)).isEmpty())
    }

    @Test
    fun fees_count_outgoing_costs_and_withdrawals_separately() {
        val fees = feeSummary(
            listOf(
                tx(TransactionType.WITHDRAW, "Agent", 1_000.0, LocalDate.of(2026, 11, 1), cost = 29.0),
                tx(TransactionType.WITHDRAW, "Agent", 2_000.0, LocalDate.of(2026, 11, 2), cost = 29.0),
                tx(TransactionType.SENT, "Jane", 500.0, LocalDate.of(2026, 11, 3), cost = 7.0),
                tx(TransactionType.RECEIVED, "Boss", 9_000.0, LocalDate.of(2026, 11, 4), cost = 50.0)
            )
        )
        assertEquals(65.0, fees.total, 0.001)
        assertEquals(2, fees.withdrawCount)
        assertEquals(58.0, fees.withdrawFees, 0.001)
    }

    @Test
    fun monthly_payee_is_a_regular_with_next_due_date() {
        val today = LocalDate.of(2026, 11, 10)
        val fibre = listOf(
            LocalDate.of(2026, 8, 14),
            LocalDate.of(2026, 9, 14),
            LocalDate.of(2026, 10, 14)
        ).map { tx(TransactionType.PAYBILL, "Safaricom Home", 2_999.0, it) }

        val regulars = detectRegulars(fibre, zone, today)

        assertEquals(1, regulars.size)
        val regular = regulars.single()
        assertEquals(Cadence.MONTHLY, regular.cadence)
        assertEquals(2_999.0, regular.typicalAmount, 0.001)
        assertEquals(LocalDate.of(2026, 11, 14), regular.nextDue)
        assertEquals(3, regular.payments)
    }

    @Test
    fun irregular_or_stopped_payees_are_not_regulars() {
        val today = LocalDate.of(2026, 11, 10)
        val scattered = listOf(1, 3, 20, 21).map {
            tx(TransactionType.SENT, "Kevin", 400.0, LocalDate.of(2026, 10, it))
        }
        val stopped = listOf(
            LocalDate.of(2026, 3, 1),
            LocalDate.of(2026, 4, 1),
            LocalDate.of(2026, 5, 1)
        ).map { tx(TransactionType.PAYBILL, "Old Gym", 3_000.0, it) }

        assertTrue(detectRegulars(scattered + stopped, zone, today).isEmpty())
    }

    @Test
    fun essential_payees_are_flagged() {
        val today = LocalDate.of(2026, 11, 10)
        val rent = listOf(5, 12, 19, 26).map {
            tx(TransactionType.SENT, "Landlord", 5_000.0, LocalDate.of(2026, 10, it))
        }
        val regular = detectRegulars(rent, zone, today, essentials = setOf("landlord")).single()
        assertEquals(Cadence.WEEKLY, regular.cadence)
        assertTrue(regular.essential)
    }

    @Test
    fun top_habit_is_the_most_visited_place_outside_regulars() {
        val day = LocalDate.of(2026, 11, 5)
        val txs = List(4) { tx(TransactionType.BUY_GOODS, "Java House", 450.0, day) } +
            List(5) { tx(TransactionType.PAYBILL, "Safaricom Home", 100.0, day) } +
            List(2) { tx(TransactionType.BUY_GOODS, "Naivas", 2_000.0, day) }

        val habit = topHabit(txs, exclude = setOf("safaricom home"))

        assertEquals("Java House", habit?.name)
        assertEquals(4, habit?.visits)
        assertEquals(1_800.0, habit?.total ?: 0.0, 0.001)
    }

    @Test
    fun kept_headline_compares_net_with_the_prior_stretch() {
        assertEquals(
            "You kept KSh 4,820 more than the stretch before.",
            keptHeadline(net = 5_820.0, previousNet = 1_000.0, hasPrior = true)
        )
        assertEquals(
            "You kept KSh 200 less than the stretch before.",
            keptHeadline(net = 800.0, previousNet = 1_000.0, hasPrior = true)
        )
        assertNull(keptHeadline(net = 800.0, previousNet = 0.0, hasPrior = false))
    }

    @Test
    fun due_dates_mark_pulse_cells() {
        val from = LocalDate.of(2026, 11, 1)
        val to = LocalDate.of(2026, 11, 30)
        val cells = pulseCalendar(
            from = from,
            to = to,
            today = LocalDate.of(2026, 11, 10),
            inflowByDate = emptyMap(),
            outflowByDate = emptyMap(),
            dueDates = setOf(LocalDate.of(2026, 11, 14))
        )
        assertEquals(listOf(LocalDate.of(2026, 11, 14)), cells.filter { it.isDue }.map { it.date })
    }

    @Test
    fun badges_track_fee_cuts_quiet_runs_and_reviewed_regulars() {
        val today = LocalDate.of(2026, 11, 10)
        val regulars = detectRegulars(
            listOf(5, 12, 19, 26).map { tx(TransactionType.SENT, "Landlord", 5_000.0, LocalDate.of(2026, 10, it)) },
            zone,
            today,
            essentials = setOf("landlord")
        )
        val badges = insightBadges(
            fees = 80.0,
            previousFees = 120.0,
            hasPrior = true,
            quietStreak = 12,
            regulars = regulars,
            earned = emptySet()
        ).associateBy { it.id }

        assertTrue(badges.getValue(BADGE_FEE_CUT).earned)
        assertEquals("12/30", badges.getValue(BADGE_QUIET_30).mark)
        assertTrue(badges.getValue(BADGE_REGULARS_REVIEWED).earned)
    }

    @Test
    fun earned_badges_stay_earned() {
        val badges = insightBadges(
            fees = 500.0,
            previousFees = 100.0,
            hasPrior = true,
            quietStreak = 0,
            regulars = emptyList(),
            earned = setOf(BADGE_QUIET_30)
        ).associateBy { it.id }
        assertTrue(badges.getValue(BADGE_QUIET_30).earned)
        assertEquals(false, badges.getValue(BADGE_FEE_CUT).earned)
    }

    @Test
    fun long_stretches_get_month_bars_and_short_ones_none() {
        val out = mapOf(
            LocalDate.of(2026, 9, 3) to 1_000.0,
            LocalDate.of(2026, 10, 3) to 2_000.0,
            LocalDate.of(2026, 10, 20) to 500.0
        )
        val bars = periodBars(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 11, 10), emptyMap(), out)
        assertEquals(listOf("Sep", "Oct", "Nov"), bars.map { it.label })
        assertEquals(2_500.0, bars[1].outflow, 0.001)
        assertTrue(periodBars(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 10), emptyMap(), out).isEmpty())
    }

    @Test
    fun average_daily_spend_looks_back_four_weeks_before_the_start() {
        val start = LocalDate.of(2026, 11, 1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val txs = listOf(
            tx(TransactionType.BUY_GOODS, "Java House", 560.0, LocalDate.of(2026, 10, 20)),
            tx(TransactionType.BUY_GOODS, "Java House", 560.0, LocalDate.of(2026, 10, 27)),
            tx(TransactionType.BUY_GOODS, "Java House", 999.0, LocalDate.of(2026, 11, 2)),
            tx(TransactionType.BUY_GOODS, "Naivas", 5_000.0, LocalDate.of(2026, 10, 25))
        )
        val daily = averageDailySpend(txs, beforeMillis = start) { it.counterpartyName == "Java House" }
        assertEquals(40.0, daily, 0.001)
    }

    private fun tx(
        type: TransactionType,
        name: String?,
        amount: Double,
        date: LocalDate,
        code: String = "A${date}${name.orEmpty()}",
        cost: Double? = null,
        raw: String = ""
    ) = MpesaTransaction(
        id = 0,
        code = code,
        type = type,
        amount = amount,
        counterpartyName = name,
        counterpartyPhone = null,
        timestampMillis = date.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli(),
        balance = null,
        cost = cost,
        rawBody = raw
    )
}
