package com.lipabill.app.viewmodel

import com.lipabill.app.data.model.MoneySource
import com.lipabill.app.data.model.TransactionType
import com.lipabill.app.data.model.TransactionType.BUY_GOODS
import com.lipabill.app.data.model.TransactionType.FULIZA
import com.lipabill.app.data.model.TransactionType.PAYBILL
import com.lipabill.app.data.model.TransactionType.RECEIVED
import com.lipabill.app.data.model.TransactionType.REVERSED
import com.lipabill.app.data.model.TransactionType.SENT
import com.lipabill.app.testTransaction
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WrapCardsTest {

    private val zone = ZoneOffset.UTC
    private var n = 0
    private fun tx(
        date: String,
        amount: Double,
        name: String,
        type: TransactionType = BUY_GOODS,
        cost: Double? = null,
        hour: Int = 12,
        phone: String? = null,
        source: MoneySource = MoneySource.MPESA,
        code: String = "W${n++}"
    ) = testTransaction(
        code = code, type = type, amount = amount, counterpartyName = name, counterpartyPhone = phone,
        cost = cost, source = source,
        timestampMillis = LocalDate.parse(date).atTime(hour, 0).toInstant(zone).toEpochMilli()
    )

    private val oct1 = LocalDate.parse("2026-10-01")
    private val oct31 = LocalDate.parse("2026-10-31")

    private fun october(vararg extra: com.lipabill.app.data.model.MpesaTransaction) = listOf(
        tx("2026-09-15", 100.0, "OLD SHOP"), // ledger starts before October
        tx("2026-10-01", 50_000.0, "ACME LTD", type = RECEIVED),
        tx("2026-10-02", 1500.0, "NAIVAS WESTLANDS", cost = 0.0),
        tx("2026-10-02", 900.0, "JAVA HOUSE", cost = 0.0),
        tx("2026-10-09", 1500.0, "NAIVAS WESTLANDS"),
        tx("2026-10-13", 3000.0, "Mama Wanjiru", type = SENT, cost = 33.0, phone = "0712000001", hour = 1),
        tx("2026-10-20", 2000.0, "KPLC PREPAID", type = PAYBILL, cost = 0.0)
    ) + extra

    @Test
    fun emptyWindowIsNull() {
        assertNull(buildWrapCards(oct1, oct31, emptyList(), emptyMap(), zone))
        assertNull(buildWrapCards(oct1, oct31, listOf(tx("2026-09-01", 10.0, "X")), emptyMap(), zone))
    }

    @Test
    fun byTheNumbersAndFlow() {
        val c = buildWrapCards(oct1, oct31, october(), emptyMap(), zone)!!
        assertEquals(6, c.txnCount)
        assertEquals(31, c.coveredDays)
        assertFalse(c.partial)
        assertEquals(5, c.activeDays)
        assertEquals(3, c.typeCounts[BUY_GOODS])
        assertEquals(1, c.typeCounts[SENT])
        assertEquals(LocalDate.parse("2026-10-02") to 2, c.busiestDay)
        assertEquals(50_000.0, c.moneyIn, 0.01)
        // Fees left the wallet too.
        assertEquals(1500 + 900 + 1500 + 3033 + 2000.0, c.moneyOut, 0.01)
        assertEquals(c.moneyIn - c.moneyOut, c.net, 0.01)
        assertEquals(LocalDate.parse("2026-10-13"), c.heaviestOutDay!!.first)
        assertEquals(33.0, c.fees, 0.01)
        assertEquals(1, c.chargedTxnCount)
        assertEquals(33.0 / c.moneyOut, c.feeShareOfOut!!, 1e-9)
    }

    @Test
    fun peopleSpotsAndRhythm() {
        val c = buildWrapCards(oct1, oct31, october(), emptyMap(), zone)!!
        assertEquals("Mama Wanjiru", c.topPeople.single().name)
        assertEquals("NAIVAS WESTLANDS", c.topSpots.first().name)
        assertTrue(c.topSpots.size <= 5)
        assertEquals(1, c.afterMidnight)
        assertEquals("Mama Wanjiru", c.biggestPayment!!.name)
        assertEquals(3000.0, c.biggestPayment!!.amount, 0.01)
        assertEquals(DayOfWeek.FRIDAY, c.busiestWeekday) // 2 Oct + 9 Oct
        // A month gets weekly bars (Mon 28 Sep .. Mon 26 Oct = 5 weeks).
        assertEquals(5, c.bars.size)
        assertEquals("W3", c.bars[c.peakBar].label) // week of 12 Oct holds the 3,033 send
    }

    @Test
    fun topListsCapAtFive() {
        val many = (1..8).map { tx("2026-10-0${(it % 9) + 1}", 100.0 * it, "SHOP $it") }
        val c = buildWrapCards(oct1, oct31, october(*many.toTypedArray()), emptyMap(), zone)!!
        assertEquals(5, c.topSpots.size)
    }

    @Test
    fun reversalsAreDropped() {
        val ledger = october(
            tx("2026-10-25", 9999.0, "WRONG TILL", code = "QK12ABC34D"),
            testTransaction(code = "RVX2", type = REVERSED, amount = 9999.0, rawBody = "Reversal of original transaction QK12ABC34D has been done",
                timestampMillis = LocalDate.parse("2026-10-26").atTime(9, 0).toInstant(zone).toEpochMilli())
        )
        val c = buildWrapCards(oct1, oct31, ledger, emptyMap(), zone)!!
        assertEquals(null, c.typeCounts[REVERSED])
        assertEquals(6, c.txnCount)
        assertTrue(c.topSpots.none { it.name == "WRONG TILL" })
    }

    @Test
    fun yearWindowGetsMonthlyBarsAndPartialFlag() {
        val ledger = listOf(
            tx("2026-03-10", 500.0, "JAVA HOUSE"),
            tx("2026-05-10", 4000.0, "JAVA HOUSE"),
            tx("2026-11-10", 700.0, "JAVA HOUSE")
        )
        val c = buildWrapCards(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31"), ledger, emptyMap(), zone)!!
        assertEquals(12, c.bars.size)
        assertEquals("May", c.bars[c.peakBar].label)
        assertTrue(c.partial)
        assertEquals(297, c.coveredDays) // 10 Mar .. 31 Dec
    }

    @Test
    fun fulizaCardOnlyWithDraws() {
        val noFuliza = buildWrapCards(oct1, oct31, october(), emptyMap(), zone)!!
        assertNull(noFuliza.fuliza)
        val draws = (1..10).map { tx("2026-10-${10 + it}", 300.0 + it, "Fuliza", type = FULIZA) }
        val c = buildWrapCards(oct1, oct31, october(*draws.toTypedArray()), emptyMap(), zone)!!
        assertEquals(10, c.fuliza!!.count)
        assertEquals(3055.0 / 10, c.fuliza!!.average, 0.01)
        // Draws are the user's own money moving, never money in.
        assertEquals(noFuliza.moneyIn, c.moneyIn, 0.01)
        assertEquals(WrapPersonality.FULIZA_FREQUENT, c.personality)
    }

    @Test
    fun familyAnchor() {
        val ledger = listOf(
            tx("2026-10-01", 20_000.0, "Mama Wanjiru", type = SENT, phone = "0712000001", cost = 0.0),
            tx("2026-10-15", 20_000.0, "Mama Wanjiru", type = SENT, phone = "0712000001", cost = 0.0),
            tx("2026-10-20", 5_000.0, "Brian Otieno", type = SENT, phone = "0712000002", cost = 0.0),
            tx("2026-10-21", 3_000.0, "NAIVAS", cost = 0.0)
        )
        assertEquals(WrapPersonality.FAMILY_ANCHOR, buildWrapCards(oct1, oct31, ledger, emptyMap(), zone)!!.personality)
    }

    @Test
    fun feeHeavy() {
        val ledger = (1..20).map { tx("2026-10-%02d".format(it + 5), 200.0, "Person $it", type = SENT, phone = "07120000${10 + it}", cost = 7.0) }
        assertEquals(WrapPersonality.FEE_HEAVY, buildWrapCards(oct1, oct31, ledger, emptyMap(), zone)!!.personality)
    }

    @Test
    fun quietOperatorAndEverydayMover() {
        val quiet = listOf(tx("2026-10-03", 500.0, "NAIVAS", cost = 0.0), tx("2026-10-20", 500.0, "JAVA HOUSE", cost = 0.0))
        assertEquals(WrapPersonality.QUIET_OPERATOR, buildWrapCards(oct1, oct31, quiet, emptyMap(), zone)!!.personality)
        val busy = (1..31).map { tx("2026-10-%02d".format(it), 300.0, "SHOP ${it % 7}", cost = 0.0) }
        assertEquals(WrapPersonality.EVERYDAY_MOVER, buildWrapCards(oct1, oct31, busy, emptyMap(), zone)!!.personality)
    }

    @Test
    fun nonMpesaLedgerWrapsWithoutFuliza() {
        val ledger = listOf(
            tx("2026-10-01", 30_000.0, "EMPLOYER", type = RECEIVED, source = MoneySource.KCB),
            tx("2026-10-05", 2_000.0, "QUICKMART", source = MoneySource.AIRTEL_MONEY, cost = 0.0)
        )
        val c = buildWrapCards(oct1, oct31, ledger, emptyMap(), zone)!!
        assertNull(c.fuliza)
        assertEquals(30_000.0, c.moneyIn, 0.01)
        assertTrue(c.personality != WrapPersonality.FULIZA_FREQUENT)
    }

    @Test
    fun monthWrapComparesWithMonthBefore() {
        val ledger = listOf(
            tx("2026-09-01", 4000.0, "NAIVAS WESTLANDS", cost = 0.0),
            tx("2026-09-20", 2000.0, "GLOVO", cost = 30.0),
            tx("2026-10-02", 1500.0, "NAIVAS WESTLANDS"),
            tx("2026-10-09", 1500.0, "NAIVAS WESTLANDS"),
            tx("2026-10-16", 1000.0, "NAIVAS WESTLANDS"),
            tx("2026-10-29", 1000.0, "JAVA HOUSE", cost = 10.0)
        )
        val c = buildMonthWrap(java.time.YearMonth.of(2026, 10), ledger, emptyMap(), zone)!!
        assertEquals(5010.0, c.moneyOut, 0.01)
        assertEquals(6030.0, c.moneyOutBefore!!, 0.01)
        assertEquals(-16, c.outChange)
        assertEquals(30.0, c.feesBefore!!, 0.01)
        assertEquals(27, c.quietDays)
        assertEquals(com.lipabill.app.engage.SpendCategory.ESSENTIALS, c.biggestCategory)
        assertEquals(80, c.biggestCategoryShare)
    }

    @Test
    fun noComparisonWhenLedgerStartsLater() {
        val c = buildMonthWrap(java.time.YearMonth.of(2026, 10), october().drop(1), emptyMap(), zone)!!
        assertNull(c.moneyOutBefore)
        assertNull(c.outChange)
    }

    @Test
    fun wrappedLooksAtLastMonth() {
        assertEquals(java.time.YearMonth.of(2026, 10), wrappedMonth(LocalDate.parse("2026-11-18")))
    }

    @Test
    fun shareTextRespectsBothToggles() {
        val c = buildWrapCards(oct1, oct31, october(), emptyMap(), zone)!!
        fun text(amounts: Boolean, names: Boolean) = wrapShareText(c, "October 2026", "The Family Anchor", amounts, names)

        val open = text(amounts = true, names = false)
        assertTrue(open.contains("KSh"))
        assertTrue(open.contains("NAIVAS WESTLANDS"))
        assertTrue(open.contains("Mama Wanjiru"))
        assertTrue(open.contains("The Family Anchor"))

        val noAmounts = text(amounts = false, names = false)
        assertFalse(noAmounts.contains("KSh"))
        assertTrue(noAmounts.contains("Mama Wanjiru"))

        val noNames = text(amounts = true, names = true)
        assertTrue(noNames.contains("KSh"))
        assertFalse(noNames.contains("NAIVAS"))
        assertFalse(noNames.contains("Mama Wanjiru"))

        val locked = text(amounts = false, names = true)
        assertFalse(locked.contains("KSh"))
        assertFalse(locked.contains("Mama Wanjiru"))
        assertTrue(locked.startsWith("My October 2026 on LipaBill: 6 transactions on 5 of 31 days."))
    }

    @Test
    fun yearWindowFollowsTheCalendar() {
        fun d(s: String) = LocalDate.parse(s)
        assertEquals(d("2026-01-01") to d("2026-12-14"), yearWrapWindow(d("2026-12-15")))
        assertEquals(d("2025-01-01") to d("2025-12-31"), yearWrapWindow(d("2026-01-20")))
        assertEquals(d("2025-11-01") to d("2026-10-31"), yearWrapWindow(d("2026-11-27")))
        assertEquals(d("2025-03-01") to d("2026-02-28"), yearWrapWindow(d("2026-03-01")))
    }

    @Test
    fun yearWrapNeedsNinetyDays() {
        val today = LocalDate.parse("2026-11-27")
        val short = listOf(tx("2026-09-01", 500.0, "JAVA HOUSE"), tx("2026-10-20", 500.0, "JAVA HOUSE"))
        assertNull(buildYearWrap(today, short, emptyMap(), zone)) // 1 Sep .. 31 Oct = 61 days
        val longer = short + tx("2026-05-01", 900.0, "NAIVAS")
        val c = buildYearWrap(today, longer, emptyMap(), zone)!!
        assertTrue(c.partial)
        assertEquals(184, c.coveredDays) // 1 May .. 31 Oct
        assertEquals(12, c.bars.size)
        assertEquals(3, c.txnCount)
    }
}
