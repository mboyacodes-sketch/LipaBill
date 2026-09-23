package com.lipabill.app.ui.permissions

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsPermissionDisclosureTest {

    @Test
    fun standard_body_contains_required_disclosure_phrases() {
        val body = SmsPermissionDisclosure.STANDARD_BODY
        SmsPermissionDisclosure.REQUIRED_PHRASES.forEach { phrase ->
            assertTrue("Missing phrase: $phrase", body.contains(phrase, ignoreCase = true))
        }
    }

    @Test
    fun disclosure_does_not_claim_permission_is_mandatory() {
        val body = SmsPermissionDisclosure.STANDARD_BODY.lowercase()
        assertTrue(body.contains("continue without sms"))
        assertFalse(body.contains("google requires"))
        assertFalse(body.contains("this permission is safe"))
        assertFalse(body.contains("you must grant"))
    }

    @Test
    fun restricted_body_includes_standard_facts() {
        val body = SmsPermissionDisclosure.body(showRestrictedSettingsHelp = true)
        SmsPermissionDisclosure.REQUIRED_PHRASES.forEach { phrase ->
            assertTrue(body.contains(phrase, ignoreCase = true))
        }
        assertTrue(body.contains("restricted settings", ignoreCase = true))
    }

    @Test
    fun continue_without_is_optional_path() {
        // Mirror SmsPermissionScreen: disclosure + continue-without CTA wording.
        assertTrue(
            SmsPermissionDisclosure.STANDARD_BODY.contains(
                "continue without SMS",
                ignoreCase = true
            )
        )
    }
}
