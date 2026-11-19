package com.lipabill.app.viewmodel

import com.lipabill.app.ussd.PaymentWallet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PayMethodsForWalletTest {

    @Test
    fun safaricom_includes_pochi() {
        val methods = payMethodsFor(PaymentWallet.SAFARICOM_MPESA)
        assertEquals(
            listOf(PayMethod.TILL, PayMethod.PAYBILL, PayMethod.POCHI),
            methods
        )
    }

    @Test
    fun airtel_and_mtn_hide_pochi() {
        for (wallet in listOf(
            PaymentWallet.AIRTEL_KENYA,
            PaymentWallet.AIRTEL_UGANDA,
            PaymentWallet.MTN_UGANDA
        )) {
            val methods = payMethodsFor(wallet)
            assertEquals(listOf(PayMethod.TILL, PayMethod.PAYBILL), methods)
            assertFalse(PayMethod.POCHI in methods)
        }
    }

    @Test
    fun unknown_wallet_hides_pochi() {
        val methods = payMethodsFor(null)
        assertTrue(PayMethod.TILL in methods)
        assertTrue(PayMethod.PAYBILL in methods)
        assertFalse(PayMethod.POCHI in methods)
    }
}
