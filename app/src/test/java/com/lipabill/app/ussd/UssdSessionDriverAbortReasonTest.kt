package com.lipabill.app.ussd

import org.junit.Assert.assertEquals
import org.junit.Test

class UssdSessionDriverAbortReasonTest {

    @Test
    fun maps_known_failure_codes() {
        assertEquals(
            RepeatTransactionCoordinator.OTHER_ACCESSIBILITY_SERVICE,
            UssdSessionDriver.closedAbortReason(
                RepeatTransactionCoordinator.OTHER_ACCESSIBILITY_SERVICE
            )
        )
        assertEquals(
            RepeatTransactionCoordinator.OTHER_ACCESSIBILITY_SERVICE,
            UssdSessionDriver.closedAbortReason("other_accessibility_blocked")
        )
        assertEquals("service_interrupted", UssdSessionDriver.closedAbortReason("service_interrupted"))
        assertEquals("inject_failed", UssdSessionDriver.closedAbortReason("inject_failed"))
        assertEquals("inject_failed", UssdSessionDriver.closedAbortReason("Could not type into field"))
        assertEquals("call_permission", UssdSessionDriver.closedAbortReason("call_permission"))
        assertEquals("call_permission", UssdSessionDriver.closedAbortReason("CALL_PHONE denied"))
        assertEquals("dial_failed", UssdSessionDriver.closedAbortReason("dial_failed"))
        assertEquals("session_timeout", UssdSessionDriver.closedAbortReason("session_timeout"))
        assertEquals("session_timeout", UssdSessionDriver.closedAbortReason("stall_timeout"))
        assertEquals("feature_disabled", UssdSessionDriver.closedAbortReason("feature_disabled"))
        assertEquals("driver_error", UssdSessionDriver.closedAbortReason("driver_error"))
        assertEquals("driver_error", UssdSessionDriver.closedAbortReason("accessibility_error"))
        assertEquals("menu_mismatch", UssdSessionDriver.closedAbortReason("menu_mismatch"))
        assertEquals("aborted", UssdSessionDriver.closedAbortReason(null))
        assertEquals("aborted", UssdSessionDriver.closedAbortReason(""))
    }

    @Test
    fun truncates_unknown_messages() {
        val long = "x".repeat(64)
        assertEquals(32, UssdSessionDriver.closedAbortReason(long).length)
        assertEquals("confirm_no", UssdSessionDriver.closedAbortReason("confirm_no"))
    }

    @Test
    fun stall_timeout_constant_is_finite() {
        assertEquals(45_000L, UssdSessionDriver.SESSION_STALL_MS)
    }
}
