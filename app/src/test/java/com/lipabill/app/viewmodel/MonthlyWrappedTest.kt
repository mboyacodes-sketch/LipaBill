package com.lipabill.app.viewmodel

import com.lipabill.app.data.model.TransactionType
import com.lipabill.app.engage.SpendCategory
import com.lipabill.app.testTransaction
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MonthlyWrappedTest {

    private val zone = ZoneOffset.UTC
    private var n = 0
    private fun tx(date: String, amount: Double, name: String, type: TransactionType = TransactionType.BUY_GOODS, cost: Double? = null) =
        testTransaction(
            code = "W${n++}", type = type, amount = amount, counterpartyName = name, cost = cost,
            timestampMillis = LocalDate.parse(date).atTime(12, 0).toInstant(zone).toEpochMilli()
        )

    @Test
    fun wrapsLastMonth() {
        val ledger = listOf(
            tx("2026-09-01", 4000.0, "NAIVAS WESTLANDS", cost = 0.0),
            tx("2026-09-20", 2000.0, "GLOVO", cost = 30.0),
            tx("2026-10-02", 1500.0, "NAIVAS WESTLANDS"),
            tx("2026-10-09", 1500.0, "NAIVAS WESTLANDS"),
            tx("2026-10-16", 1000.0, "NAIVAS WESTLANDS"),
            tx("2026-10-29", 1000.0, "JAVA HOUSE", cost = 10.0)
        )
        val w = buildWrapped(YearMonth.of(2026, 10), ledger, emptyMap(), zone)!!
        assertEquals(5000.0, w.spent, 0.01)
        assertEquals(6000.0, w.spentBefore!!, 0.01)
        assertEquals(-16, w.spendChange)
        assertEquals("NAIVAS WESTLANDS", w.topMerchant!!.name)
        assertEquals(3, w.topMerchant!!.visits)
        assertEquals(SpendCategory.ESSENTIALS, w.biggestCategory)
        assertEquals(80, w.biggestCategoryShare)
        assertEquals(27, w.noSpendDays)
        assertEquals(LocalDate.parse("2026-10-19"), w.quietestWeekStart)
        assertEquals(0.0, w.quietestWeekSpent, 0.01)
    }

    @Test
    fun noHistoryNoWrapped() {
        assertNull(buildWrapped(YearMonth.of(2026, 10), emptyList(), emptyMap(), zone))
    }

    @Test
    fun wrappedLooksAtLastMonth() {
        assertEquals(YearMonth.of(2026, 10), wrappedMonth(LocalDate.parse("2026-11-18")))
    }
}
