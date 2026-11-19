package com.lipabill.app.ussd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityServiceMatchTest {

    private val pkg = "com.lipabill.app"

    @Test
    fun parse_splits_colon_list_and_drops_blanks() {
        assertEquals(
            listOf(
                "com.lipabill.app/.ussd.UssdAccessibilityService",
                "com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService"
            ),
            parseEnabledAccessibilityServices(
                "com.lipabill.app/.ussd.UssdAccessibilityService:" +
                    "com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService"
            )
        )
        assertEquals(emptyList<String>(), parseEnabledAccessibilityServices(""))
        assertEquals(emptyList<String>(), parseEnabledAccessibilityServices(null))
        assertEquals(
            listOf("com.lipabill.app/.ussd.UssdAccessibilityService"),
            parseEnabledAccessibilityServices("  com.lipabill.app/.ussd.UssdAccessibilityService : ")
        )
    }

    @Test
    fun match_accepts_short_and_flat_component_forms() {
        assertTrue(
            matchesLipaBillServiceToken(
                "com.lipabill.app/.ussd.UssdAccessibilityService",
                pkg
            )
        )
        assertTrue(
            matchesLipaBillServiceToken(
                "com.lipabill.app/com.lipabill.app.ussd.UssdAccessibilityService",
                pkg
            )
        )
        assertTrue(
            matchesLipaBillServiceToken(
                "com.lipabill.app/com.lipabill.app.ussd.UssdAccessibilityService".uppercase(),
                pkg
            )
        )
    }

    @Test
    fun match_rejects_other_services_and_empty() {
        assertFalse(matchesLipaBillServiceToken("", pkg))
        assertFalse(
            matchesLipaBillServiceToken(
                "com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService",
                pkg
            )
        )
        assertFalse(
            matchesLipaBillServiceToken(
                "com.other.app/.ussd.UssdAccessibilityService",
                pkg
            )
        )
    }

    @Test
    fun foreign_detection_shape_only_lipabill_is_not_foreign() {
        val tokens = parseEnabledAccessibilityServices(
            "com.lipabill.app/.ussd.UssdAccessibilityService"
        )
        assertFalse(tokens.any { !matchesLipaBillServiceToken(it, pkg) })
    }

    @Test
    fun foreign_detection_shape_talkback_with_lipabill_is_foreign() {
        val tokens = parseEnabledAccessibilityServices(
            "com.lipabill.app/.ussd.UssdAccessibilityService:" +
                "com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService"
        )
        assertTrue(tokens.any { !matchesLipaBillServiceToken(it, pkg) })
    }
}
