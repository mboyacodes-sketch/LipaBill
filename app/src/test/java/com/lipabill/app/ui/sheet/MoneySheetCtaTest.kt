package com.lipabill.app.ui.sheet

import com.lipabill.app.ui.permissions.PaymentAccessNeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneySheetCtaTest {

    @Test
    fun amount_step_ready_uses_confirm_label() {
        val cta = moneySheetPrimaryCta(
            onAmountStep = true,
            canConfirm = true,
            dialStarted = false,
            detailsValid = true,
            paymentBlock = null,
            confirmLabel = "Send Now"
        )
        assertEquals("Send Now", cta.label)
        assertTrue(cta.enabled)
    }

    @Test
    fun payment_block_wins_over_confirm_on_amount_step() {
        val cta = moneySheetPrimaryCta(
            onAmountStep = true,
            canConfirm = true,
            dialStarted = false,
            detailsValid = true,
            paymentBlock = PaymentAccessNeed.Accessibility,
            confirmLabel = "Send Now"
        )
        assertEquals(PaymentAccessNeed.Accessibility.actionLabel, cta.label)
        assertTrue(cta.enabled)
    }

    @Test
    fun details_step_with_block_asks_for_access() {
        val cta = moneySheetPrimaryCta(
            onAmountStep = false,
            canConfirm = false,
            dialStarted = false,
            detailsValid = false,
            paymentBlock = PaymentAccessNeed.Phone,
            confirmLabel = "Send Now"
        )
        assertEquals(PaymentAccessNeed.Phone.actionLabel, cta.label)
        assertTrue(cta.enabled)
    }

    @Test
    fun payment_block_runs_allow_instead_of_confirm() {
        var allowed: PaymentAccessNeed? = null
        var confirmed = false
        runMoneySheetPrimaryCta(
            onAmountStep = true,
            canConfirm = true,
            paymentBlock = PaymentAccessNeed.Accessibility,
            onConfirm = { confirmed = true },
            onAllow = { allowed = it },
            onContinue = {}
        )
        assertEquals(PaymentAccessNeed.Accessibility, allowed)
        assertFalse(confirmed)
    }

    @Test
    fun dial_started_disables_confirm() {
        val cta = moneySheetPrimaryCta(
            onAmountStep = true,
            canConfirm = true,
            dialStarted = true,
            detailsValid = true,
            paymentBlock = null,
            confirmLabel = "Pay Now"
        )
        assertEquals("Pay Now", cta.label)
        assertFalse(cta.enabled)
    }
}
