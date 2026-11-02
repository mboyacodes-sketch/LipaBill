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
