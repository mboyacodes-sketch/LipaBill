package com.lipabill.app.ussd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentAccessGatesTest {

    private fun ready(
        featureEnabled: Boolean = true,
        accessibilityEnabled: Boolean = true,
        hasCallPhone: Boolean = true,
        hasPhoneState: Boolean = true,
        simCount: Int = 1,
        hasSafaricom: Boolean = true
    ) = PaymentAccessGates.statusFrom(
        featureEnabled = featureEnabled,
        accessibilityEnabled = accessibilityEnabled,
        hasCallPhone = hasCallPhone,
        hasPhoneState = hasPhoneState,
        simCount = simCount,
        hasSafaricom = hasSafaricom
    )

    @Test
    fun all_gates_open_is_ready() {
        val s = ready()
        assertTrue(s.ready)
        assertNull(s.blockReason)
    }

    @Test
    fun feature_off_blocks_first() {
        val s = ready(featureEnabled = false, accessibilityEnabled = false)
        assertFalse(s.ready)
        assertEquals("Turn on payment automation in Profile", s.blockReason)
    }

    @Test
    fun accessibility_off_blocks() {
        val s = ready(accessibilityEnabled = false)
        assertFalse(s.ready)
        assertTrue(s.blockReason!!.contains("Accessibility", ignoreCase = true))
    }

    @Test
    fun call_phone_missing_blocks() {
        val s = ready(hasCallPhone = false)
        assertFalse(s.ready)
        assertTrue(s.blockReason!!.contains("Phone permission", ignoreCase = true))
    }

    @Test
    fun phone_state_missing_blocks() {
        val s = ready(hasPhoneState = false)
        assertFalse(s.ready)
        assertTrue(s.blockReason!!.contains("Phone state", ignoreCase = true))
    }

    @Test
    fun no_sim_blocks() {
        val s = ready(simCount = 0, hasSafaricom = false)
        assertFalse(s.ready)
        assertTrue(s.blockReason!!.contains("No SIM", ignoreCase = true))
    }

    @Test
    fun non_safaricom_sim_blocks() {
        val s = ready(simCount = 1, hasSafaricom = false)
        assertFalse(s.ready)
        assertTrue(s.blockReason!!.contains("Safaricom", ignoreCase = true))
    }
}
