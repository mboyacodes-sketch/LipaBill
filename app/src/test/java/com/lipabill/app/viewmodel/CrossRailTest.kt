package com.lipabill.app.viewmodel

import com.lipabill.app.data.model.MoneySource
import com.lipabill.app.data.model.MpesaTransaction
import com.lipabill.app.data.model.TransactionType
import com.lipabill.app.data.model.TransactionType.BUY_GOODS
import com.lipabill.app.data.model.TransactionType.PAYBILL
import com.lipabill.app.data.model.TransactionType.RECEIVED
import com.lipabill.app.data.model.TransactionType.SENT
import com.lipabill.app.testTransaction
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Shapes seen on a real dual-rail ledger (M-PESA with KCB, Equity, Co-op, Airtel Money). */
class CrossRailTest {

    private val zone = ZoneOffset.UTC
    private var n = 0
    private fun tx(
        source: MoneySource,
        type: TransactionType,
        amount: Double,
        name: String?,
        minute: Int = 0,
        second: Int = 0,
        cost: Double? = null,
        day: String = "2026-10-10"
    ): MpesaTransaction = testTransaction(
        id = (++n).toLong(), code = "C$n", type = type, amount = amount, counterpartyName = name, cost = cost,
        source = source,
        timestampMillis = LocalDate.parse(day).atTime(12, minute, second).toInstant(zone).toEpochMilli()
    )

    @Test
    fun bankCopyOfWalletPaybillIsOnePayment() {
        val mpesa = tx(MoneySource.MPESA, PAYBILL, 2_500.0, "EQUITY PAYBILL ACCOUNT", cost = 23.0)
        val equity = tx(MoneySource.EQUITY, BUY_GOODS, 2_500.0, "JAVA HOUSE", second = 20)
        val pairs = findCrossRailPairs(listOf(mpesa, equity))
        assertEquals(CrossRailPair.Kind.TWIN, pairs.single().kind)
        assertEquals(mpesa, pairs.single().keep)
        assertEquals(listOf(mpesa), reconcileCrossRail(listOf(mpesa, equity)))
    }

    @Test
    fun bankAmountThatIncludesWalletFeeStillPairs() {
        // Bank advisory often quotes what left the bank (principal + M-PESA fee).
        val mpesa = tx(MoneySource.MPESA, PAYBILL, 2_500.0, "NAIVAS", cost = 23.0)
        val equity = tx(MoneySource.EQUITY, BUY_GOODS, 2_523.0, "NAIVAS TILL", second = 25)
        val out = reconcileCrossRail(listOf(mpesa, equity))
        assertEquals(listOf(mpesa), out)
        assertEquals(2_523.0, flowTotals(out, WalletAccount.MPESA).second, 0.01)
    }

    @Test
    fun bothRailsSayingReceivedIsOneArrival() {
        val mpesa = tx(MoneySource.MPESA, RECEIVED, 10_000.0, "KCB MONEY TRANSFER")
        val kcb = tx(MoneySource.KCB, RECEIVED, 10_000.0, "EMPLOYER LTD", second = 30)
        assertEquals(listOf(mpesa), reconcileCrossRail(listOf(kcb, mpesa)))
    }

    @Test
    fun bankToWalletTopUpIsOwnMoney() {
        val kcb = tx(MoneySource.KCB, SENT, 5_000.0, "M-PESA 0712***678", cost = 0.0)
        val mpesa = tx(MoneySource.MPESA, RECEIVED, 5_000.0, "KCB", second = 40)
        val out = reconcileCrossRail(listOf(kcb, mpesa))
        assertEquals(2, out.size)
        assertTrue(out.all { it.ownTransfer })
        assertTrue(out.all { isOwnMoneyMove(it) })
    }

    @Test
    fun walletToAirtelLineIsOwnMoney() {
        val mpesa = tx(MoneySource.MPESA, PAYBILL, 300.0, "AIRTEL MONEY", cost = 5.0)
        val airtel = tx(MoneySource.AIRTEL_MONEY, RECEIVED, 300.0, "M-PESA", second = 10)
        assertEquals(CrossRailPair.Kind.OWN_TRANSFER, findCrossRailPairs(listOf(mpesa, airtel)).single().kind)
    }

    @Test
    fun namedOwnTransferStillPairsAfterHalfAnHour() {
        val kcb = tx(MoneySource.KCB, SENT, 8_000.0, "M-PESA")
        val mpesa = tx(MoneySource.MPESA, RECEIVED, 8_000.0, "KCB", minute = 45)
        val out = reconcileCrossRail(listOf(kcb, mpesa))
        assertEquals(2, out.size)
        assertTrue(out.all { it.ownTransfer })
    }

    @Test
    fun guardsAgainstCoincidences() {
        // Same rail never pairs.
        assertTrue(findCrossRailPairs(listOf(
            tx(MoneySource.MPESA, SENT, 500.0, "A"), tx(MoneySource.MPESA, RECEIVED, 500.0, "B", second = 5)
        )).isEmpty())
        // Different amounts never pair.
        assertTrue(findCrossRailPairs(listOf(
            tx(MoneySource.MPESA, PAYBILL, 500.0, "SHOP"), tx(MoneySource.KCB, SENT, 520.0, "SHOP", second = 5)
        )).isEmpty())
        // Opposite direction without naming the other rail: two real payments, not a transfer.
        assertTrue(findCrossRailPairs(listOf(
            tx(MoneySource.MPESA, RECEIVED, 5_000.0, "JANE DOE"),
            tx(MoneySource.KCB, SENT, 5_000.0, "LANDLORD", second = 30)
        )).isEmpty())
        // Five minutes apart with nothing naming the other rail: likely two real payments.
        assertTrue(findCrossRailPairs(listOf(
            tx(MoneySource.MPESA, RECEIVED, 1_000.0, "JANE DOE"), tx(MoneySource.KCB, SENT, 1_000.0, "LANDLORD", minute = 5)
        )).isEmpty())
        // Same gap but the wallet row names the bank: one transfer.
        assertEquals(1, findCrossRailPairs(listOf(
            tx(MoneySource.MPESA, RECEIVED, 1_000.0, "KCB BANK"), tx(MoneySource.KCB, SENT, 1_000.0, "M-PESA", minute = 5)
        )).size)
    }

    @Test
    fun eachRowJoinsOnePairClosestFirst() {
        val mpesa = tx(MoneySource.MPESA, PAYBILL, 800.0, "SHOP")
        val far = tx(MoneySource.EQUITY, BUY_GOODS, 800.0, "SHOP", minute = 2)
        val near = tx(MoneySource.COOP, SENT, 800.0, "SHOP", second = 15)
        val pairs = findCrossRailPairs(listOf(mpesa, far, near))
        assertEquals(near, pairs.single().other)
        assertEquals(listOf(mpesa, far), reconcileCrossRail(listOf(mpesa, far, near)))
    }

    @Test
    fun homeSourceChipAfterReconcileHidesBankTwin() {
        // Home loads all rails, reconciles, then the History chip filters — same as Insights.
        val mpesa = tx(MoneySource.MPESA, PAYBILL, 2_500.0, "EQUITY PAYBILL", cost = 23.0)
        val twin = tx(MoneySource.EQUITY, BUY_GOODS, 2_500.0, "JAVA HOUSE", second = 20)
        val equityOnly = tx(MoneySource.EQUITY, SENT, 400.0, "FRIEND", minute = 3)
        val home = reconcileCrossRail(listOf(mpesa, twin, equityOnly))
        assertEquals(listOf(equityOnly), home.filter { it.source == MoneySource.EQUITY })
        assertEquals(listOf(mpesa), home.filter { it.source == MoneySource.MPESA })
    }

    @Test
    fun moneyPictureLedgerIsIdempotentAndDropsTwins() {
        val ledger = listOf(
            tx(MoneySource.MPESA, PAYBILL, 1_200.0, "SHOP", cost = 10.0),
            tx(MoneySource.EQUITY, BUY_GOODS, 1_200.0, "SHOP", second = 12)
        )
        val once = moneyPictureLedger(ledger)
        assertEquals(once, moneyPictureLedger(once))
        assertEquals(1_210.0, flowTotals(once, WalletAccount.MPESA).second, 0.01)
    }

    @Test
    fun wrapCountsEachPaymentOnceAndLeavesOwnTransfersOut() {
        val ledger = listOf(
            tx(MoneySource.MPESA, RECEIVED, 50_000.0, "EMPLOYER", day = "2026-10-01"),
            // One paybill, texted by M-PESA and by the merchant's bank.
            tx(MoneySource.MPESA, PAYBILL, 2_000.0, "EQUITY PAYBILL", cost = 23.0, day = "2026-10-05"),
            tx(MoneySource.EQUITY, BUY_GOODS, 2_000.0, "NAIVAS", second = 20, day = "2026-10-05"),
            // KCB → M-PESA top-up.
            tx(MoneySource.KCB, SENT, 7_000.0, "M-PESA", day = "2026-10-08"),
            tx(MoneySource.MPESA, RECEIVED, 7_000.0, "KCB", second = 30, day = "2026-10-08"),
            // M-PESA → own Airtel line, with a fee.
            tx(MoneySource.MPESA, PAYBILL, 1_000.0, "AIRTEL MONEY", cost = 15.0, day = "2026-10-09"),
            tx(MoneySource.AIRTEL_MONEY, RECEIVED, 1_000.0, "M-PESA", second = 10, day = "2026-10-09")
        )
        // buildWrapCards now reconciles itself; calling with raw ledger must still be correct.
        val c = buildWrapCards(
            LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31"),
            ledger, emptyMap(), zone
        )!!
        assertEquals(6, c.txnCount) // 7 messages, one bank copy dropped
        assertEquals(50_000.0, c.moneyIn, 0.01) // top-ups are not income
        assertEquals(2_023.0 + 15.0, c.moneyOut, 0.01) // the paybill once, plus the transfer fee
        assertFalse(c.topSpots.any { it.name == "AIRTEL MONEY" })
        assertFalse(c.topPeople.any { it.name == "KCB" || it.name == "M-PESA" })
        assertEquals(2_000.0, c.biggestPayment!!.amount, 0.01)
    }

    @Test
    fun flowTotalsAndFlowByDateAgreeOnFeesAndOwnTransfers() {
        val day = LocalDate.parse("2026-10-10")
        val ledger = moneyPictureLedger(listOf(
            tx(MoneySource.MPESA, RECEIVED, 10_000.0, "BOSS"),
            tx(MoneySource.MPESA, PAYBILL, 1_000.0, "SHOP", cost = 15.0, minute = 1),
            // Fee-only row (amount already zeroed elsewhere): still money out.
            tx(MoneySource.MPESA, SENT, 0.0, "X", cost = 7.0, minute = 2).let {
                // eligible() requires amount > 0 for pairing; for flow, amount 0 + fee counts.
                it
            },
            tx(MoneySource.KCB, SENT, 2_000.0, "M-PESA", minute = 3),
            tx(MoneySource.MPESA, RECEIVED, 2_000.0, "KCB", minute = 3, second = 20)
        ))
        val (inn, out) = flowTotals(ledger, WalletAccount.MPESA)
        val (byIn, byOut) = flowByDate(ledger, zone, WalletAccount.MPESA)
        assertEquals(inn, byIn.values.sum(), 0.01)
        assertEquals(out, byOut.values.sum(), 0.01)
        assertEquals(10_000.0, inn, 0.01)
        assertEquals(1_015.0 + 7.0, out, 0.01)
        assertEquals(day, byOut.keys.single())
    }
}
