package com.lipabill.app.viewmodel

import com.lipabill.app.testTransaction
import com.lipabill.app.data.model.MoneySource
import com.lipabill.app.data.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class AnalyticsSummaryBuilderTest {

    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.now(zone)

    private fun at(daysAgo: Int, hour: Int = 12): Long =
        today.minusDays(daysAgo.toLong()).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    private fun tx(
        type: TransactionType,
        name: String?,
        amount: Double,
        daysAgo: Int,
        code: String = "C$daysAgo$type",
        phone: String? = null,
        cost: Double? = null
    ) = testTransaction(
        code = code,
        type = type,
        amount = amount,
        counterpartyName = name,
        counterpartyPhone = phone,
        timestampMillis = at(daysAgo),
        cost = cost
    )

    @Test
    fun empty_ledger_still_builds_a_story() {
        val summary = buildAnalyticsSummary(
            emptyList(),
            AnalyticsRangeState(AnalyticsRange.THIS_MONTH, null, null, null),
            WalletAccount.MPESA,
            InsightMarks()
        )
        assertFalse(summary.hasLedger)
        assertTrue(summary.storyHeadline.isNotBlank())
        assertTrue(summary.topPeople.isEmpty())
        assertTrue(summary.topBusinesses.isEmpty())
    }

    @Test
    fun this_month_range_starts_on_the_first() {
        val ledger = listOf(
            tx(TransactionType.BUY_GOODS, "Old Shop", 9_000.0, today.dayOfMonth + 5),
            tx(TransactionType.BUY_GOODS, "New Shop", 100.0, 1)
        )
        val summary = buildAnalyticsSummary(
            ledger,
            AnalyticsRangeState(AnalyticsRange.THIS_MONTH, null, null, null),
            WalletAccount.MPESA,
            InsightMarks()
        )
        assertEquals(today.withDayOfMonth(1), summary.periodFrom)
        assertEquals(today, summary.periodTo)
        assertEquals(AnalyticsRange.THIS_MONTH, summary.range)
        assertEquals(100.0, summary.expenseTotal, 0.0)
        assertTrue(summary.pulse.isNotEmpty())
        assertTrue(summary.pulse.any { it.isToday })
    }

    @Test
    fun thirty_day_summary_splits_people_and_business_spend() {
        val ledger = listOf(
            tx(TransactionType.SENT, "Mary Wanjiku", 800.0, 2, phone = "0711000111"),
            tx(TransactionType.SENT, "JOHN KAMAU", 200.0, 5, phone = "0712345678"),
            tx(TransactionType.PAYBILL, "DSTV Kenya", 2_450.0, 3),
            tx(TransactionType.BUY_GOODS, "Java House", 450.0, 4),
            tx(TransactionType.BUY_GOODS, "Java House", 480.0, 6),
            tx(TransactionType.BUY_GOODS, "Java House", 500.0, 8),
            tx(TransactionType.RECEIVED, "Payroll", 20_000.0, 1),
            tx(TransactionType.WITHDRAW, "Agent", 1_000.0, 7, cost = 29.0)
        )
        val summary = buildAnalyticsSummary(
            ledger,
            AnalyticsRangeState(AnalyticsRange.DAYS_30, null, null, null),
            WalletAccount.MPESA,
            InsightMarks()
        )
        assertTrue(summary.hasLedger)
        assertEquals(20_000.0, summary.incomeTotal, 0.0)
        assertTrue(summary.expenseTotal > 0.0)
        assertEquals(listOf("Mary Wanjiku", "JOHN KAMAU"), summary.topPeople.map { it.name })
        assertTrue(summary.topBusinesses.any { it.name == "DSTV Kenya" })
        assertTrue(summary.peopleSpendTotal > summary.businessSpendTotal || summary.businessSpendTotal > 0.0)
        assertTrue(summary.byType.isNotEmpty())
        assertTrue(summary.fees.withdrawCount >= 1)
        val habit = summary.habit
        assertTrue(habit == null || habit.name.contains("Java", ignoreCase = true))
    }

    @Test
    fun seven_day_range_ignores_older_rows() {
        val ledger = listOf(
            tx(TransactionType.BUY_GOODS, "Old Shop", 9_000.0, 20),
            tx(TransactionType.BUY_GOODS, "New Shop", 100.0, 1)
        )
        val summary = buildAnalyticsSummary(
            ledger,
            AnalyticsRangeState(AnalyticsRange.DAYS_7, null, null, null),
            WalletAccount.MPESA,
            InsightMarks()
        )
        assertEquals(100.0, summary.expenseTotal, 0.0)
        assertTrue(summary.topBusinesses.none { it.name == "Old Shop" })
        assertTrue(summary.topBusinesses.any { it.name == "New Shop" })
    }

    @Test
    fun fuliza_ledger_skips_people_business_split() {
        val ledger = listOf(
            tx(TransactionType.FULIZA, "Due tomorrow", 500.0, 1)
        )
        val summary = buildAnalyticsSummary(
            ledger,
            AnalyticsRangeState(AnalyticsRange.DAYS_30, null, null, null),
            WalletAccount.FULIZA,
            InsightMarks()
        )
        assertTrue(summary.topPeople.isEmpty())
        assertTrue(summary.topBusinesses.isEmpty())
        assertTrue(summary.regulars.isEmpty())
    }

    @Test
    fun custom_year_and_all_ranges_resolve() {
        val ledger = listOf(
            tx(TransactionType.BUY_GOODS, "Shop", 100.0, 3),
            tx(TransactionType.RECEIVED, "Pay", 1_000.0, 2)
        )
        val customFrom = today.minusDays(3)
        val custom = buildAnalyticsSummary(
            ledger,
            AnalyticsRangeState(AnalyticsRange.CUSTOM, null, customFrom, today),
            WalletAccount.MPESA,
            InsightMarks()
        )
        assertEquals(customFrom, custom.periodFrom)
        assertEquals(today, custom.periodTo)

        val year = buildAnalyticsSummary(
            ledger,
            AnalyticsRangeState(AnalyticsRange.YEAR, today.year, null, null),
            WalletAccount.MPESA,
            InsightMarks()
        )
        assertEquals(today.year, year.selectedYear)
        assertTrue(year.rangeLabel.contains(today.year.toString()))

        val all = buildAnalyticsSummary(
            ledger,
            AnalyticsRangeState(AnalyticsRange.ALL, null, null, null),
            WalletAccount.MPESA,
            InsightMarks()
        )
        assertEquals("All time", all.rangeLabel)
        assertNull(previousAnalyticsBounds(AnalyticsRange.ALL, all.periodFrom!!, all.periodTo!!))
    }

    @Test
    fun short_ledger_history_is_not_comparable_to_prior() {
        val ledger = listOf(tx(TransactionType.BUY_GOODS, "Shop", 100.0, 2))
        val summary = buildAnalyticsSummary(
            ledger,
            AnalyticsRangeState(AnalyticsRange.DAYS_30, null, null, null),
            WalletAccount.MPESA,
            InsightMarks()
        )
        assertNull(summary.keptHeadline)
    }

    /**
     * Regulars are all-time cadence estimates: period pills must not change them.
     * Rail filtering works by narrowing the ledger before build — that must change them.
     */
    @Test
    fun regulars_ignore_period_but_follow_source_filtered_ledger() {
        fun monthlyPaybill(
            name: String,
            amount: Double,
            source: MoneySource,
            dayOffsets: List<Int>
        ) = dayOffsets.mapIndexed { i, daysAgo ->
            testTransaction(
                code = "${source.name}-$name-$i",
                type = TransactionType.PAYBILL,
                amount = amount,
                counterpartyName = name,
                timestampMillis = at(daysAgo),
                source = source
            )
        }
        // ~30-day gaps so cadence maps to MONTHLY.
        val mpesaFibre = monthlyPaybill(
            "Safaricom Home",
            2_999.0,
            MoneySource.MPESA,
            listOf(90, 60, 30)
        )
        val airtelDstv = monthlyPaybill(
            "DStv Kenya",
            5_500.0,
            MoneySource.AIRTEL_MONEY,
            listOf(88, 58, 28)
        )
        // One-off spend inside the last week so period totals move while regulars stay put.
        val recentShop = testTransaction(
            code = "SHOP-RECENT",
            type = TransactionType.BUY_GOODS,
            amount = 400.0,
            counterpartyName = "Naivas",
            timestampMillis = at(3),
            source = MoneySource.MPESA
        )
        val both = mpesaFibre + airtelDstv + listOf(recentShop)
        val marks = InsightMarks()
        val month = buildAnalyticsSummary(
            both,
            AnalyticsRangeState(AnalyticsRange.THIS_MONTH, null, null, null),
            WalletAccount.MPESA,
            marks
        )
        val week = buildAnalyticsSummary(
            both,
            AnalyticsRangeState(AnalyticsRange.DAYS_7, null, null, null),
            WalletAccount.MPESA,
            marks
        )
        assertEquals(2, month.regulars.size)
        assertEquals(month.regulars.map { it.key to it.monthlyCost }, week.regulars.map { it.key to it.monthlyCost })
        // Period spend can move; regulars stay the same list/amounts.
        assertTrue(month.txnCount >= week.txnCount)
        assertEquals(week.expenseTotal, 400.0, 0.001)

        val mpesaOnly = buildAnalyticsSummary(
            both.filter { it.source == MoneySource.MPESA },
            AnalyticsRangeState(AnalyticsRange.THIS_MONTH, null, null, null),
            WalletAccount.MPESA,
            marks
        )
        val airtelOnly = buildAnalyticsSummary(
            both.filter { it.source == MoneySource.AIRTEL_MONEY },
            AnalyticsRangeState(AnalyticsRange.THIS_MONTH, null, null, null),
            WalletAccount.MPESA,
            marks
        )
        assertEquals(1, mpesaOnly.regulars.size)
        assertEquals("Safaricom Home", mpesaOnly.regulars.single().name)
        assertEquals(1, airtelOnly.regulars.size)
        assertEquals("DStv Kenya", airtelOnly.regulars.single().name)
        assertNotEquals(
            mpesaOnly.regulars.single().monthlyCost,
            airtelOnly.regulars.single().monthlyCost,
            0.001
        )
    }
}
