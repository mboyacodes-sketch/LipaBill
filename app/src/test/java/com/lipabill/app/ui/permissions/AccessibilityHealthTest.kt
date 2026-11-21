package com.lipabill.app.ui.permissions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityHealthTest {

    private fun health(
        preferred: Boolean = true,
        secureOn: Boolean = false,
        bound: Boolean = false,
        batteryIgnoring: Boolean = false
    ) = AccessibilityHealth(
        preferred = preferred,
        secureOn = secureOn,
        bound = bound,
        batteryIgnoring = batteryIgnoring,
        unusedAppStatus = "unknown",
        manufacturer = "samsung",
        model = "SM-A125F",
        sdk = 31
    )

    @Test
    fun bound_is_payment_ready_with_no_coach() {
        val h = health(secureOn = true, bound = true)
        assertTrue(h.paymentReady)
        assertEquals(A11yCoachKind.None, h.coachKind)
    }

    @Test
    fun secure_without_bind_routes_to_unbound_coach() {
        val h = health(secureOn = true, bound = false)
        assertFalse(h.paymentReady)
        assertEquals(A11yCoachKind.Unbound, h.coachKind)
        assertEquals(FirstRunPage.OemSurvival, AccessibilityHealthProbe.consentPageFor(h.coachKind))
    }

    @Test
    fun preferred_without_secure_routes_to_grant_coach() {
        val h = health(preferred = true, secureOn = false, bound = false)
        assertEquals(A11yCoachKind.GrantCleared, h.coachKind)
        assertEquals(
            FirstRunPage.Accessibility,
            AccessibilityHealthProbe.consentPageFor(h.coachKind)
        )
    }

    @Test
    fun resume_notes_explain_each_failure_mode() {
        assertTrue(
            AccessibilityHealthProbe.resumeNoteFor(A11yCoachKind.GrantCleared)!!
                .contains("Accessibility", ignoreCase = true)
        )
        assertTrue(
            AccessibilityHealthProbe.resumeNoteFor(A11yCoachKind.Unbound)!!
                .contains("sleeping", ignoreCase = true)
        )
    }
}
