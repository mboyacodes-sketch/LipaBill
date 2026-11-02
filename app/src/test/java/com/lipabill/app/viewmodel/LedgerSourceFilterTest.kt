package com.lipabill.app.viewmodel

import com.lipabill.app.data.model.AppRegion
import com.lipabill.app.data.model.MoneySource
import com.lipabill.app.data.model.MpesaTransaction
import com.lipabill.app.data.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerSourceFilterTest {

    private val kenya = listOf(
        MoneySource.MPESA,
        MoneySource.AIRTEL_MONEY,
        MoneySource.KCB
    )

    @Test
    fun empty_ledger_still_lists_the_region_catalog() {
        assertEquals(
            listOf(MoneySource.MPESA, MoneySource.AIRTEL_MONEY, MoneySource.KCB),
            availableMoneySources(emptyList(), MoneySource.MPESA, kenya)
        )
    }

    @Test
    fun kenya_catalog_is_more_than_mpesa_alone() {
        val chips = availableMoneySources(
            emptyList(),
            AppRegion.KENYA.primaryWallet,
            AppRegion.KENYA.sources
        )
        assertTrue(chips.contains(MoneySource.MPESA))
        assertTrue(chips.contains(MoneySource.KCB))
        assertTrue(chips.contains(MoneySource.AIRTEL_MONEY))
        assertEquals(MoneySource.MPESA, chips.first())
    }

    @Test
    fun primary_wallet_leads_then_frequency_then_label() {
        val txs = listOf(
            sample(MoneySource.KCB, "K1"),
            sample(MoneySource.KCB, "K2"),
            sample(MoneySource.EQUITY, "E1"),
            sample(MoneySource.MPESA, "M1")
        )
        assertEquals(
            listOf(
                MoneySource.MPESA,
                MoneySource.KCB,
                MoneySource.EQUITY,
                MoneySource.AIRTEL_MONEY
            ),
            availableMoneySources(txs, MoneySource.MPESA, kenya + MoneySource.EQUITY)
        )
    }

    @Test
    fun unused_catalog_entries_sort_by_label_after_activity() {
        val txs = listOf(sample(MoneySource.NCBA, "N1"))
        assertEquals(
            listOf(MoneySource.MPESA, MoneySource.NCBA, MoneySource.ABSA),
            availableMoneySources(
                txs,
                MoneySource.MPESA,
                listOf(MoneySource.MPESA, MoneySource.ABSA, MoneySource.NCBA)
            )
        )
    }

    private fun sample(source: MoneySource, code: String) = MpesaTransaction(
        id = 0,
        code = code,
        type = TransactionType.SENT,
        amount = 100.0,
        counterpartyName = "Jane",
        counterpartyPhone = null,
        timestampMillis = 1L,
        balance = null,
        cost = null,
        rawBody = "",
        source = source
    )
}
