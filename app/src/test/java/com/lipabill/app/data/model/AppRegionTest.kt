package com.lipabill.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppRegionTest {

    @Test
    fun kenya_keeps_mpesa_send_pay_and_fuliza() {
        val ke = AppRegion.KENYA
        assertTrue(ke.canSendPay)
        assertTrue(ke.hasFuliza)
        assertEquals(MoneySource.MPESA, ke.primaryWallet)
        assertTrue(MoneySource.MPESA in ke.sources)
        assertTrue(MoneySource.KCB in ke.sources)
        assertFalse(MoneySource.MTN_MOMO in ke.sources)
        assertFalse(MoneySource.MIXX in ke.sources)
    }

    @Test
    fun uganda_is_ledger_only_with_mtn() {
        val ug = AppRegion.UGANDA
        assertFalse(ug.canSendPay)
        assertFalse(ug.hasFuliza)
        assertEquals(MoneySource.MTN_MOMO, ug.primaryWallet)
        assertTrue(MoneySource.MTN_MOMO in ug.sources)
        assertTrue(MoneySource.AIRTEL_MONEY in ug.sources)
        assertFalse(MoneySource.MPESA in ug.sources)
        assertFalse(MoneySource.TKASH in ug.sources)
    }

    @Test
    fun tanzania_reads_vodacom_mpesa_and_mixx() {
        val tz = AppRegion.TANZANIA
        assertTrue(MoneySource.MPESA in tz.sources)
        assertTrue(MoneySource.MIXX in tz.sources)
        assertTrue(MoneySource.HALOPESA in tz.sources)
        assertTrue(MoneySource.CRDB in tz.sources)
        assertFalse(tz.canSendPay)
        assertFalse(tz.hasFuliza)
    }

    @Test
    fun sender_is_scoped_to_the_selected_country() {
        assertEquals(
            MoneySource.TKASH,
            MoneySource.fromSender("T-Kash", AppRegion.KENYA)
        )
        assertNull(MoneySource.fromSender("T-Kash", AppRegion.UGANDA))
        assertEquals(
            MoneySource.MTN_MOMO,
            MoneySource.fromSender("MTN", AppRegion.UGANDA)
        )
        assertNull(MoneySource.fromSender("MTN", AppRegion.KENYA))
        assertEquals(
            MoneySource.MIXX,
            MoneySource.fromSender("TigoPesa", AppRegion.TANZANIA)
        )
        assertNull(MoneySource.fromSender("TigoPesa", AppRegion.KENYA))
    }

    @Test
    fun currency_tokens_differ_by_country() {
        assertTrue(AppRegion.KENYA.matchesCurrency("Credited with Ksh 100.00"))
        assertTrue(AppRegion.KENYA.matchesCurrency("You have received Ksh500.00 from Jane"))
        assertFalse(AppRegion.KENYA.matchesCurrency("Credited with UGX 100"))
        assertTrue(AppRegion.UGANDA.matchesCurrency("You received UGX 5,000"))
        assertTrue(AppRegion.TANZANIA.matchesCurrency("Sent TSh 2,000"))
        assertTrue(AppRegion.RWANDA.matchesCurrency("Paid RWF 1500"))
    }

    @Test
    fun from_iso_defaults_to_kenya() {
        assertEquals(AppRegion.KENYA, AppRegion.fromIso(null))
        assertEquals(AppRegion.KENYA, AppRegion.fromIso("xx"))
        assertEquals(AppRegion.TANZANIA, AppRegion.fromIso("tz"))
        assertNull(AppRegion.fromIsoOrNull(null))
        assertNull(AppRegion.fromIsoOrNull(""))
        assertNull(AppRegion.fromIsoOrNull("us"))
        assertEquals(AppRegion.UGANDA, AppRegion.fromIsoOrNull("ug"))
    }

    @Test
    fun flag_emoji_follows_iso() {
        assertEquals("🇰🇪", AppRegion.KENYA.flagEmoji)
        assertEquals("🇹🇿", AppRegion.TANZANIA.flagEmoji)
        assertEquals("🇺🇬", AppRegion.UGANDA.flagEmoji)
        assertEquals("🇷🇼", AppRegion.RWANDA.flagEmoji)
        assertEquals("", isoToFlagEmoji("K"))
        assertEquals("", isoToFlagEmoji("12"))
    }
}
