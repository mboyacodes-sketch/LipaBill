package com.lipabill.app.ui.permissions

import com.lipabill.app.resolveRepoFile
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
    fun disclosure_is_money_management_not_otp_or_upi() {
        val body = SmsPermissionDisclosure.STANDARD_BODY.lowercase()
        assertTrue(body.contains("money management"))
        assertTrue(body.contains("does not read otps"))
        assertFalse(body.contains("5 digit"))
        assertFalse(body.contains("account verification"))
        assertFalse(body.contains("default messaging app") && body.contains("is your default"))
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
        assertTrue(
            SmsPermissionDisclosure.STANDARD_BODY.contains(
                "continue without SMS",
                ignoreCase = true
            )
        )
    }

    @Test
    fun console_500_char_instructions_fit_and_name_on_device_storage() {
        val text = SmsPermissionDisclosure.CONSOLE_REVIEWER_INSTRUCTIONS_500
        assertTrue(text.length <= 500)
        assertTrue(text.contains("money management", ignoreCase = true))
        assertTrue(text.contains("encrypted", ignoreCase = true))
        assertTrue(text.contains("not uploaded or shared", ignoreCase = true))
        assertTrue(text.contains("does not send SMS", ignoreCase = true))
        assertTrue(text.contains("OTP", ignoreCase = true))
        val doc = resolveRepoFile("docs/play-store-sms-permission-review.md")
        assertTrue(doc != null)
        assertTrue(doc!!.readText().contains(text))
    }

    @Test
    fun play_listing_paragraph_names_sms_money_management() {
        val listing = SmsPermissionDisclosure.PLAY_STORE_LISTING_PARAGRAPH
        assertTrue(listing.contains("SMS-based money management", ignoreCase = true))
        assertTrue(listing.contains("wallet and bank confirmation", ignoreCase = true))
        assertTrue(listing.contains("M-Pesa", ignoreCase = true))
        assertTrue(listing.contains("available balance", ignoreCase = true))
        assertTrue(listing.contains("does not send SMS", ignoreCase = true))
        assertTrue(listing.contains("does not read OTPs", ignoreCase = true))
        assertTrue(listing.contains("encrypted", ignoreCase = true))
        assertTrue(listing.contains("not uploaded or shared", ignoreCase = true))
        assertFalse(listing.contains("optional", ignoreCase = true))
    }

    @Test
    fun play_store_listing_doc_matches_sms_paragraph() {
        val listingMd = resolveRepoFile("docs/play-store-listing.md")
        assertTrue("Missing docs/play-store-listing.md from ${System.getProperty("user.dir")}", listingMd != null)
        val doc = listingMd!!.readText()
        assertTrue(
            "docs/play-store-listing.md must contain PLAY_STORE_LISTING_PARAGRAPH verbatim",
            doc.contains(SmsPermissionDisclosure.PLAY_STORE_LISTING_PARAGRAPH)
        )
    }
}
