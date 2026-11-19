package com.lipabill.app.viewmodel

import com.lipabill.app.data.model.TransactionType
import com.lipabill.app.testTransaction
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchListTest {

    private val zone = ZoneOffset.UTC
    private val today = LocalDate.parse("2026-11-18")
    private var n = 0
    private fun pay(date: LocalDate, amount: Double, name: String, type: TransactionType = TransactionType.PAYBILL) =
        testTransaction(
            code = "T${n++}", type = type, amount = amount, counterpartyName = name,
            timestampMillis = date.atTime(10, 0).toInstant(zone).toEpochMilli()
        )

    private fun monthly(name: String, amounts: List<Double>, lastDate: LocalDate) =
        amounts.reversed().mapIndexed { i, amount -> pay(lastDate.minusMonths(i.toLong()), amount, name) }

    @Test
    fun billThatWentUp() {
        val dstv = monthly("DSTV KENYA", listOf(2000.0, 2000.0, 2000.0, 2200.0), LocalDate.parse("2026-11-10"))
        val change = regularChanges(dstv, today, zone).single() as RegularChange.PriceChanged
        assertEquals(2000.0, change.usual, 0.01)
        assertEquals(200.0, change.difference, 0.01)
    }

    @Test
    fun steadyBillIsNotAChange() {
        val dstv = monthly("DSTV KENYA", listOf(2000.0, 2000.0, 2000.0, 2020.0), LocalDate.parse("2026-11-10"))
        assertTrue(regularChanges(dstv, today, zone).isEmpty())
    }

    @Test
    fun newRecurringCharge() {
        val gym = monthly("ZOOM GYM", listOf(3000.0, 3000.0, 3000.0), LocalDate.parse("2026-11-05"))
        val change = regularChanges(gym, today, zone).single()
        assertTrue(change is RegularChange.NewCharge)
        assertEquals("ZOOM GYM", change.name)
    }

    @Test
    fun chargeThatStopped() {
        val netflix = monthly("NETFLIX", listOf(1100.0, 1100.0, 1100.0, 1100.0), LocalDate.parse("2026-08-20"))
        val change = regularChanges(netflix, today, zone).single() as RegularChange.Stopped
        assertEquals(LocalDate.parse("2026-08-20"), change.lastPaid)
    }

    @Test
    fun supermarketBasketGotDearer() {
        val start = LocalDate.parse("2026-05-02")
        val earlier = (0 until 6).map { pay(start.plusDays(it * 14L), 2000.0, "NAIVAS WESTLANDS", TransactionType.BUY_GOODS) }
        val recent = (0 until 3).map { pay(LocalDate.parse("2026-10-01").plusDays(it * 14L), 2200.0, "NAIVAS WESTLANDS", TransactionType.BUY_GOODS) }
        val trend = priceTrends(earlier + recent, today, zone).single()
        assertEquals(10, trend.percent)
        assertEquals(YearMonth.of(2026, 5), trend.since)
    }

    @Test
    fun fewVisitsNoTrend() {
        val visits = (0 until 4).map { pay(LocalDate.parse("2026-06-01").plusMonths(it.toLong()), 1000.0 + it * 300, "JAVA HOUSE", TransactionType.BUY_GOODS) }
        assertTrue(priceTrends(visits, today, zone).isEmpty())
    }
}
