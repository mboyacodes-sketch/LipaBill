package com.lipabill.app.viewmodel

import com.lipabill.app.data.model.MpesaTransaction
import com.lipabill.app.data.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    ) = MpesaTransaction(
        id = 0,
        code = code,
        type = type,
        amount = amount,
        counterpartyName = name,
        counterpartyPhone = phone,
        timestampMillis = at(daysAgo),
        balance = null,
        cost = cost,
        rawBody = ""
    )

    @Test
    fun empty_ledger_still_builds_a_story() {
        val summary = buildAnalyticsSummary(
            emptyList(),
            AnalyticsRangeState(AnalyticsRange.DAYS_30, null, null, null),
            WalletAccount.MPESA,
            InsightMarks()
        )
        assertFalse(summary.hasLedger)
        assertTrue(summary.storyHeadline.isNotBlank())
        assertTrue(summary.topPeople.isEmpty())
        assertTrue(summary.topBusinesses.isEmpty())
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
}
