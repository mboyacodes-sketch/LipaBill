package com.lipabill.app.viewmodel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletFundingTest {

    @Test
    fun zero_mpesa_balance_can_still_start_a_payment() {
        assertTrue(WalletAccount.MPESA.canFundPayment(0.0, null))
        assertTrue(WalletAccount.FULIZA.canFundPayment(0.0, 0.0))
    }

    @Test
    fun fuliza_history_can_start_a_payment_before_an_mpesa_balance_arrives() {
        assertTrue(WalletAccount.FULIZA.canFundPayment(null, 80.0))
    }

    @Test
    fun no_balance_at_all_cannot_start_a_payment() {
        assertFalse(WalletAccount.MPESA.canFundPayment(null, null))
        assertFalse(WalletAccount.FULIZA.canFundPayment(null, null))
    }
}
