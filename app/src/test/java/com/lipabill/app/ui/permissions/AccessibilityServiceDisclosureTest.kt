package com.lipabill.app.ui.permissions

import com.lipabill.app.resolveRepoFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityServiceDisclosureTest {

    @Test
    fun standard_body_contains_required_disclosure_phrases() {
        val body = AccessibilityServiceDisclosure.STANDARD_BODY
        AccessibilityServiceDisclosure.REQUIRED_PHRASES.forEach { phrase ->
            assertTrue("Missing phrase: $phrase", body.contains(phrase, ignoreCase = true))
        }
        assertEquals(
            AccessibilityServiceDisclosure.STANDARD_POINTS.joinToString(" "),
            body
        )
    }

    @Test
    fun disclosure_stays_user_facing_without_developer_jargon() {
        val body = AccessibilityServiceDisclosure.STANDARD_BODY
        assertFalse(body.contains("IsAccessibilityTool", ignoreCase = true))
        assertFalse(body.contains("isAccessibilityTool", ignoreCase = true))
        assertTrue(body.contains("not an accessibility tool", ignoreCase = true))
    }

    @Test
    fun disclosure_does_not_claim_permission_is_mandatory() {
        val body = AccessibilityServiceDisclosure.STANDARD_BODY.lowercase()
        assertTrue(body.contains("continue without"))
        assertFalse(body.contains("google requires"))
        assertFalse(body.contains("you must enable"))
        assertFalse(body.contains("this permission is safe"))
    }

    @Test
    fun play_listing_paragraph_names_accessibility_service_api() {
        val listing = AccessibilityServiceDisclosure.PLAY_STORE_LISTING_PARAGRAPH
        assertTrue(listing.contains("AccessibilityService API", ignoreCase = true))
        assertTrue(listing.contains("Send", ignoreCase = true))
        assertTrue(listing.contains("Pay", ignoreCase = true))
        assertTrue(listing.contains("Repeat", ignoreCase = true))
        assertTrue(listing.contains("USSD", ignoreCase = true))
        assertTrue(listing.contains("not shared", ignoreCase = true))
        assertTrue(listing.contains(AccessibilityServiceDisclosure.SERVICE_LABEL))
    }

    @Test
    fun play_store_listing_doc_matches_listing_paragraph() {
        val listingMd = resolveRepoFile("docs/play-store-listing.md")
        assertTrue("Missing docs/play-store-listing.md from ${System.getProperty("user.dir")}", listingMd != null)
        val doc = listingMd!!.readText()
        assertTrue(
            "docs/play-store-listing.md must contain PLAY_STORE_LISTING_PARAGRAPH verbatim",
            doc.contains(AccessibilityServiceDisclosure.PLAY_STORE_LISTING_PARAGRAPH)
        )
        assertTrue(doc.contains("Event tickets", ignoreCase = true))
        assertTrue(doc.contains("SGR", ignoreCase = true))
        assertTrue(!doc.contains("Flight boarding", ignoreCase = true))
    }

    @Test
    fun title_and_consent_labels() {
        val title = AccessibilityServiceDisclosure.TITLE
        assertTrue(title.contains("Accessibility Service", ignoreCase = true))
        assertTrue(title.contains("Send", ignoreCase = true))
        assertTrue(title.contains("Pay", ignoreCase = true))
        assertTrue(title.contains("Repeat", ignoreCase = true))
        assertTrue(AccessibilityServiceDisclosure.AGREE.equals("Agree", ignoreCase = false))
        assertTrue(AccessibilityServiceDisclosure.NOT_NOW.equals("Not now", ignoreCase = false))
        assertTrue(
            AccessibilityServiceDisclosure.STANDARD_BODY.contains(
                "Send, Pay, and Repeat stay off",
                ignoreCase = true
            )
        )
    }
}
