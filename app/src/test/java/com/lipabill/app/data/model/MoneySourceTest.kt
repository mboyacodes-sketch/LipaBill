package com.lipabill.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneySourceTest {

    @Test
    fun mpesa_senders_normalize_to_mpesa() {
        assertEquals(MoneySource.MPESA, MoneySource.fromSender("MPESA"))
        assertEquals(MoneySource.MPESA, MoneySource.fromSender("M-PESA"))
        assertEquals(MoneySource.MPESA, MoneySource.fromSender(" mpesa "))
        assertEquals(MoneySource.MPESA, MoneySource.fromSender("M_PESA"))
    }

    @Test
    fun marketing_mpesa_prefixes_are_not_the_wallet() {
        assertNull(MoneySource.fromSender("INFO-MPESA"))
        assertNull(MoneySource.fromSender("MPESA Promo"))
        assertNull(MoneySource.fromSender("MyMPESA"))
        assertNull(MoneySource.fromSender("Safaricom"))
    }

    @Test
    fun other_kenyan_rails_map_from_common_headers() {
        assertEquals(MoneySource.AIRTEL_MONEY, MoneySource.fromSender("AirtelMoney"))
        assertEquals(MoneySource.AIRTEL_MONEY, MoneySource.fromSender("airtelmoney"))
        assertNull(MoneySource.fromSender("AIRTEL", AppRegion.KENYA))
        assertNull(MoneySource.fromSender("AirtelAlert", AppRegion.KENYA))
        assertEquals(
            MoneySource.AIRTEL_MONEY,
            MoneySource.fromSender("AIRTEL", AppRegion.UGANDA)
        )
        assertEquals(MoneySource.TKASH, MoneySource.fromSender("T-Kash"))
        assertEquals(MoneySource.KCB, MoneySource.fromSender("KCB"))
        assertNull(MoneySource.fromSender("KCBInfo"))
        assertNull(MoneySource.fromSender("KCBALERTS"))
        assertEquals(MoneySource.EQUITY, MoneySource.fromSender("Equity Bank"))
        assertEquals(MoneySource.COOP, MoneySource.fromSender("CO-OPBANK"))
        assertEquals(MoneySource.I_AND_M, MoneySource.fromSender("I&M"))
        assertEquals(MoneySource.I_AND_M, MoneySource.fromSender("IANDMBANK"))
        assertEquals(MoneySource.ABSA, MoneySource.fromSender("AbsaBank"))
    }

    @Test
    fun inbox_likes_cover_mpesa_and_major_banks() {
        val likes = AppRegion.KENYA.inboxAddressLikes
        assertTrue(likes.contains("%MPESA%"))
        assertTrue(likes.contains("%M-PESA%"))
        assertTrue(likes.contains("%KCB%"))
        assertTrue(likes.contains("%EQUITY%"))
        assertTrue(likes.contains("%AIRTEL%"))
        assertTrue(likes.contains("%CO-OP%"))
        assertFalse(likes.contains("%MTN%"))
        assertFalse(likes.contains("%TIGO%"))
    }
}
