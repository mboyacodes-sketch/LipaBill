package com.lipabill.app.ui.permissions

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OemSurvivalHelperTest {

    @Test
    fun coach_body_mentions_battery_or_sleep() {
        val body = OemSurvivalHelper.coachBody()
        assertTrue(
            body.contains("battery", ignoreCase = true) ||
                body.contains("sleep", ignoreCase = true)
        )
    }

    @Test
    fun oem_family_key_is_closed_token() {
        val key = OemSurvivalHelper.oemFamilyKey()
        assertTrue(key in setOf("samsung", "transsion", "xiaomi", "other"))
    }

    @Test
    fun unbound_health_is_not_payment_ready() {
        val h = AccessibilityHealth(
            preferred = true,
            secureOn = true,
            bound = false,
            batteryIgnoring = false,
            unusedAppStatus = "enabled",
            manufacturer = "samsung",
            model = "SM-A125F",
            sdk = 31
        )
        assertFalse(h.paymentReady)
        assertTrue(h.coachKind == A11yCoachKind.Unbound)
    }
}
