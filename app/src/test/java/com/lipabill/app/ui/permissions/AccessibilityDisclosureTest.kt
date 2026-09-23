package com.lipabill.app.ui.permissions

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityDisclosureTest {

    @Test
    fun disclosure_covers_required_topics() {
        val combined =
            AccessibilityDisclosure.WHAT_IT_DOES + "\n" + AccessibilityDisclosure.WHAT_IT_DOES_NOT
        AccessibilityDisclosure.REQUIRED_PHRASES.forEach { phrase ->
            assertTrue("Missing: $phrase", combined.contains(phrase, ignoreCase = true))
        }
    }

    @Test
    fun does_not_claim_accessibility_tool_or_hide_settings() {
        val does = AccessibilityDisclosure.WHAT_IT_DOES.lowercase()
        assertTrue(does.contains("settings"))
        assertFalse(does.contains("accessibility tool"))
        assertFalse(does.contains("never reads ui"))
    }

    @Test
    fun states_not_general_automation() {
        assertTrue(
            AccessibilityDisclosure.WHAT_IT_DOES_NOT.contains(
                "general device automation",
                ignoreCase = true
            )
        )
    }
}
