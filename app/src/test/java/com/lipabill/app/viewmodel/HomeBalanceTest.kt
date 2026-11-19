package com.lipabill.app.viewmodel

import com.lipabill.app.data.model.AppRegion
import com.lipabill.app.data.model.MoneySource
import com.lipabill.app.ussd.SimLine
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeBalanceTest {

    private fun line(id: Int, carrier: String, mcc: Int, mnc: Int) = SimLine(
        subscriptionId = id,
        simSlotIndex = id - 1,
        displayName = carrier,
        carrierName = carrier,
        numberHint = null,
        label = "SIM $id",
        isSafaricom = carrier == "Safaricom",
        mcc = mcc,
        mnc = mnc
    )

    private val safaricom = line(1, "Safaricom", 639, 2)
    private val airtel = line(2, "Airtel", 639, 3)
    private val enabled = setOf(MoneySource.MPESA, MoneySource.AIRTEL_MONEY, MoneySource.KCB)

    @Test
    fun dualSimOffersBothWalletsPrimaryFirst() {
        assertEquals(
            listOf(MoneySource.MPESA, MoneySource.AIRTEL_MONEY),
            balanceWalletChoices(AppRegion.KENYA, enabled, listOf(airtel, safaricom))
        )
    }

    @Test
    fun walletWithoutItsSimGetsNoCard() {
        // Airtel on in Settings, only Safaricom SIM in the phone: only SIM-linked wallets are active.
        assertEquals(
            listOf(MoneySource.MPESA),
            balanceWalletChoices(AppRegion.KENYA, enabled, listOf(safaricom))
        )
    }

    @Test
    fun singleNetworkOffersOneWallet() {
        assertEquals(
            listOf(MoneySource.MPESA),
            balanceWalletChoices(AppRegion.KENYA, setOf(MoneySource.MPESA), listOf(safaricom))
        )
    }

    @Test
    fun walletNotInLedgerIsNotOffered() {
        assertEquals(
            listOf(MoneySource.MPESA),
            balanceWalletChoices(AppRegion.KENYA, setOf(MoneySource.MPESA), listOf(safaricom, airtel))
        )
    }

    @Test
    fun pickedWalletShowsWhileStillEnabled() {
        val choices = listOf(MoneySource.MPESA, MoneySource.AIRTEL_MONEY)
        assertEquals(
            MoneySource.AIRTEL_MONEY,
            resolveBalanceSource(
                active = MoneySource.MPESA,
                choices = choices,
                pillPick = MoneySource.AIRTEL_MONEY,
                primary = MoneySource.MPESA
            )
        )
        // Airtel turned off in Settings: fall back to dial / primary.
        assertEquals(
            MoneySource.MPESA,
            resolveBalanceSource(
                active = MoneySource.AIRTEL_MONEY,
                choices = listOf(MoneySource.MPESA),
                pillPick = MoneySource.AIRTEL_MONEY,
                primary = MoneySource.MPESA
            )
        )
    }

    @Test
    fun pillPickWinsOverDialWallet() {
        assertEquals(
            MoneySource.AIRTEL_MONEY,
            resolveBalanceSource(
                active = MoneySource.MPESA,
                choices = listOf(MoneySource.MPESA, MoneySource.AIRTEL_MONEY),
                pillPick = MoneySource.AIRTEL_MONEY,
                primary = MoneySource.MPESA
            )
        )
    }

    @Test
    fun sheetShowsTheDialWalletBalanceOnly() {
        val balances = mapOf(MoneySource.MPESA to 12340.0, MoneySource.AIRTEL_MONEY to 950.0)
        assertEquals(950.0, sheetBalance(com.lipabill.app.ussd.PaymentWallet.AIRTEL_KENYA, balances, 12340.0))
        // Airtel picked but no Airtel SMS yet: no figure, not M-Pesa's.
        assertEquals(null, sheetBalance(com.lipabill.app.ussd.PaymentWallet.AIRTEL_KENYA, mapOf(MoneySource.MPESA to 1.0), 1.0))
        assertEquals(12340.0, sheetBalance(null, balances, 12340.0))
    }

    @Test
    fun cardsOnePerLineWalletWithLineDetails() {
        val cards = walletCards(
            AppRegion.KENYA,
            listOf(MoneySource.MPESA, MoneySource.AIRTEL_MONEY),
            MoneySource.MPESA,
            listOf(safaricom.copy(numberHint = "0712 345 678"), airtel),
            mapOf(MoneySource.MPESA to 100.0)
        )
        assertEquals(listOf(MoneySource.MPESA, MoneySource.AIRTEL_MONEY), cards.map { it.source })
        assertEquals("Safaricom · SIM 1", cards[0].lineLabel)
        assertEquals("5678", cards[0].lastDigits)
        assertEquals(100.0, cards[0].balance!!, 0.0)
        assertEquals(null, cards[1].balance)
    }

    @Test
    fun bankFilterShowsASingleCard() {
        val cards = walletCards(
            AppRegion.KENYA,
            listOf(MoneySource.MPESA, MoneySource.AIRTEL_MONEY),
            MoneySource.KCB,
            listOf(safaricom, airtel),
            mapOf(MoneySource.KCB to 5.0)
        )
        assertEquals(listOf(MoneySource.KCB), cards.map { it.source })
    }
}
