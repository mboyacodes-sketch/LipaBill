package com.lipabill.app.ussd

import com.lipabill.app.data.model.AppRegion
import com.lipabill.app.data.model.MoneySource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LineGatedSourcesTest {

    private fun line(id: Int, carrier: String, mcc: Int?, mnc: Int?) = SimLine(
        subscriptionId = id,
        simSlotIndex = id - 1,
        displayName = carrier,
        carrierName = carrier,
        numberHint = null,
        label = "SIM $id · $carrier",
        isSafaricom = carrier == "Safaricom",
        mcc = mcc,
        mnc = mnc
    )

    private val safaricom = line(1, "Safaricom", 639, 2)
    private val airtelKe = line(2, "Airtel", 639, 3)
    private val chosen = setOf(MoneySource.MPESA, MoneySource.AIRTEL_MONEY, MoneySource.KCB, MoneySource.EQUITY)

    @Test
    fun everyKenyaWalletNeedsItsSim() {
        assertEquals(
            setOf(MoneySource.MPESA, MoneySource.AIRTEL_MONEY, MoneySource.TKASH, MoneySource.EQUITEL),
            LineGatedSources.simBound(AppRegion.KENYA)
        )
    }

    @Test
    fun telkomAndEquitelLinesUnlockTheirWallets() {
        val telkom = line(5, "Telkom KE", 639, 7)
        // Equitel rides on Airtel's network code; its name decides.
        val equitel = line(6, "Equitel", 639, 3)
        val picked = setOf(MoneySource.TKASH, MoneySource.EQUITEL, MoneySource.AIRTEL_MONEY, MoneySource.KCB)
        assertEquals(
            setOf(MoneySource.TKASH, MoneySource.EQUITEL, MoneySource.KCB),
            LineGatedSources.usable(AppRegion.KENYA, picked, listOf(telkom, equitel))
        )
        assertEquals(null, equitel.payWalletFor(AppRegion.KENYA))
    }

    @Test
    fun tanzaniaWalletsFollowTheirNetworks() {
        val vodacom = line(7, "Vodacom", 640, 4)
        val yas = line(8, "YAS", 640, 2)
        assertEquals(
            setOf(MoneySource.MPESA, MoneySource.MIXX),
            LineGatedSources.onPhone(AppRegion.TANZANIA, listOf(vodacom, yas))
        )
    }

    @Test
    fun lockedNamesTheNetwork() {
        val locked = LineGatedSources.locked(AppRegion.KENYA, emptyList(), phoneAccess = true)
        assertEquals("T-Kash needs an active Telkom SIM in this phone.", locked[MoneySource.TKASH])
    }

    @Test
    fun noSimKeepsOnlyBanks() {
        assertEquals(
            setOf(MoneySource.KCB, MoneySource.EQUITY),
            LineGatedSources.usable(AppRegion.KENYA, chosen, emptyList())
        )
    }

    @Test
    fun safaricomOnlyDropsAirtel() {
        assertEquals(
            setOf(MoneySource.MPESA, MoneySource.KCB, MoneySource.EQUITY),
            LineGatedSources.usable(AppRegion.KENYA, chosen, listOf(safaricom))
        )
    }

    @Test
    fun bothSimsKeepEverythingChosen() {
        assertEquals(chosen, LineGatedSources.usable(AppRegion.KENYA, chosen, listOf(safaricom, airtelKe)))
    }

    @Test
    fun foreignSimDoesNotUnlockKenyaWallet() {
        val mtnUg = line(3, "MTN", 641, 10)
        assertEquals(
            setOf(MoneySource.KCB, MoneySource.EQUITY),
            LineGatedSources.usable(AppRegion.KENYA, chosen, listOf(mtnUg))
        )
    }

    @Test
    fun ugandaGatesAirtelAndMtn() {
        val airtelUg = line(4, "Airtel", 641, 6)
        val picked = setOf(MoneySource.MTN_MOMO, MoneySource.AIRTEL_MONEY, MoneySource.STANBIC)
        assertEquals(
            setOf(MoneySource.AIRTEL_MONEY, MoneySource.STANBIC),
            LineGatedSources.usable(AppRegion.UGANDA, picked, listOf(airtelUg))
        )
    }

    @Test
    fun lockedExplainsMissingSimOrPermission() {
        // Every wallet without its line is disabled, not just the ones LipaBill can dial.
        val locked = LineGatedSources.locked(AppRegion.KENYA, listOf(safaricom), phoneAccess = true)
        assertEquals(setOf(MoneySource.AIRTEL_MONEY, MoneySource.TKASH, MoneySource.EQUITEL), locked.keys)
        assertEquals(
            "Airtel Money needs an active Airtel SIM in this phone.",
            locked[MoneySource.AIRTEL_MONEY]
        )

        val noAccess = LineGatedSources.locked(AppRegion.KENYA, emptyList(), phoneAccess = false)
        assertTrue(noAccess.getValue(MoneySource.MPESA).startsWith("Allow phone access"))
    }
}
