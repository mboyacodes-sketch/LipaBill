package com.lipabill.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerSourcesTest {

    @Test
    fun missing_storage_keeps_every_rail_in_the_country() {
        assertEquals(
            AppRegion.KENYA.sources,
            resolvedLedgerSources(AppRegion.KENYA, stored = null)
        )
    }

    @Test
    fun stored_set_is_clipped_to_the_country() {
        assertEquals(
            setOf(MoneySource.MPESA, MoneySource.KCB),
            resolvedLedgerSources(
                AppRegion.KENYA,
                setOf(MoneySource.MPESA, MoneySource.KCB, MoneySource.MTN_MOMO)
            )
        )
    }

    @Test
    fun empty_stored_set_falls_back_to_the_primary_wallet() {
        assertEquals(
            setOf(MoneySource.MPESA),
            resolvedLedgerSources(AppRegion.KENYA, emptySet())
        )
        assertEquals(
            setOf(MoneySource.MTN_MOMO),
            resolvedLedgerSources(AppRegion.UGANDA, emptySet())
        )
    }

    @Test
    fun toggle_will_not_clear_the_last_provider() {
        val one = setOf(MoneySource.MPESA)
        assertEquals(
            one,
            toggleLedgerSource(one, MoneySource.MPESA, AppRegion.KENYA.sources)
        )
    }

    @Test
    fun toggle_adds_and_removes_inside_the_catalog() {
        val start = setOf(MoneySource.MPESA)
        val added = toggleLedgerSource(start, MoneySource.KCB, AppRegion.KENYA.sources)
        assertEquals(setOf(MoneySource.MPESA, MoneySource.KCB), added)
        assertEquals(
            setOf(MoneySource.MPESA),
            toggleLedgerSource(added, MoneySource.KCB, AppRegion.KENYA.sources)
        )
    }

    @Test
    fun toggle_will_not_add_a_sixth_rail() {
        val five = setOf(
            MoneySource.MPESA,
            MoneySource.AIRTEL_MONEY,
            MoneySource.KCB,
            MoneySource.EQUITY,
            MoneySource.COOP
        )
        assertEquals(
            five,
            toggleLedgerSource(five, MoneySource.NCBA, AppRegion.KENYA.sources)
        )
        assertEquals(
            five - MoneySource.EQUITY,
            toggleLedgerSource(five, MoneySource.EQUITY, AppRegion.KENYA.sources)
        )
    }

    @Test
    fun chip_row_keeps_the_active_rail_when_capped() {
        val many = listOf(
            MoneySource.MPESA,
            MoneySource.AIRTEL_MONEY,
            MoneySource.KCB,
            MoneySource.EQUITY,
            MoneySource.COOP,
            MoneySource.NCBA,
            MoneySource.ABSA
        )
        assertEquals(
            listOf(
                MoneySource.NCBA,
                MoneySource.MPESA,
                MoneySource.AIRTEL_MONEY,
                MoneySource.KCB,
                MoneySource.EQUITY
            ),
            visibleLedgerSources(many, selected = MoneySource.NCBA, max = 5)
        )
        assertEquals(5, clipLedgerSources(many.toSet(), AppRegion.KENYA).size)
        assertTrue(MoneySource.MPESA in clipLedgerSources(many.toSet(), AppRegion.KENYA))
    }

    @Test
    fun default_rails_mix_wallet_and_common_banks() {
        val ke = defaultLedgerSources(AppRegion.KENYA)
        assertEquals(MAX_ENABLED_LEDGER_SOURCES, ke.size)
        assertTrue(MoneySource.MPESA in ke)
        assertTrue(MoneySource.AIRTEL_MONEY in ke)
        assertTrue(MoneySource.KCB in ke)
        assertTrue(MoneySource.EQUITY in ke)

        val ug = defaultLedgerSources(AppRegion.UGANDA)
        assertTrue(MoneySource.MTN_MOMO in ug)
        assertTrue(MoneySource.AIRTEL_MONEY in ug)
        assertTrue(MoneySource.CENTENARY in ug)
    }

    @Test
    fun catalog_lists_the_primary_wallet_first() {
        val chips = providerCatalog(AppRegion.KENYA)
        assertEquals(MoneySource.MPESA, chips.first())
        assertTrue(chips.contains(MoneySource.KCB))
        assertTrue(chips.contains(MoneySource.AIRTEL_MONEY))
        val groups = groupedProviderCatalog(AppRegion.KENYA)
        assertEquals(listOf("Wallets", "Banks"), groups.map { it.first })
        assertTrue(groups.first().second.all { it.isWallet })
        assertTrue(groups.last().second.none { it.isWallet })
    }

    @Test
    fun serialize_round_trips() {
        val sources = setOf(MoneySource.KCB, MoneySource.MPESA)
        assertEquals(
            sources,
            parseStoredSources(serializeLedgerSources(sources))
        )
        assertEquals(null, parseStoredSources(null))
        assertEquals(emptySet<MoneySource>(), parseStoredSources(""))
    }
}
