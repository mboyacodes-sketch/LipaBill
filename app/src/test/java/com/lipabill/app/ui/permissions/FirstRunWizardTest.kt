package com.lipabill.app.ui.permissions

import com.lipabill.app.data.model.AppRegion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstRunWizardTest {

    @Test
    fun first_run_is_welcome_region_sms_phone_accessibility() {
        assertEquals(
            listOf(
                FirstRunPage.Welcome,
                FirstRunPage.Region,
                FirstRunPage.Providers,
                FirstRunPage.Sms,
                FirstRunPage.Phone,
                FirstRunPage.Accessibility
            ),
            FirstRunWizard.PAGES
        )
        assertTrue(FirstRunWizard.PAGES.contains(FirstRunPage.Region))
        assertEquals(
            listOf(
                FirstRunPage.Welcome,
                FirstRunPage.Region,
                FirstRunPage.Providers,
                FirstRunPage.Sms
            ),
            FirstRunWizard.firstRunPages(AppRegion.UGANDA)
        )
        assertFalse(FirstRunWizard.PAGES.contains(FirstRunPage.Camera))
        assertFalse(FirstRunWizard.PAGES.contains(FirstRunPage.Notifications))
        assertFalse(FirstRunWizard.PAGES.contains(FirstRunPage.Hibernation))
    }

    @Test
    fun later_visits_skip_welcome() {
        assertEquals(
            listOf(
                FirstRunPage.Sms,
                FirstRunPage.Phone,
                FirstRunPage.Accessibility,
                FirstRunPage.Camera,
                FirstRunPage.Notifications,
                FirstRunPage.Hibernation
            ),
            FirstRunWizard.LATER_PAGES
        )
        assertFalse(FirstRunWizard.LATER_PAGES.contains(FirstRunPage.Region))
        assertFalse(FirstRunWizard.LATER_PAGES.contains(FirstRunPage.Providers))
    }

    @Test
    fun later_payment_stays_a_cluster_optional_asks_are_one_page() {
        assertEquals(
            listOf(FirstRunPage.Sms, FirstRunPage.Phone, FirstRunPage.Accessibility),
            FirstRunWizard.laterPages(FirstRunPage.Phone)
        )
        assertEquals(listOf(FirstRunPage.Camera), FirstRunWizard.laterPages(FirstRunPage.Camera))
        assertEquals(
            listOf(FirstRunPage.Notifications),
            FirstRunWizard.laterPages(FirstRunPage.Notifications)
        )
        assertEquals(
            listOf(FirstRunPage.Hibernation),
            FirstRunWizard.laterPages(FirstRunPage.Hibernation)
        )
    }

    @Test
    fun send_pay_opens_phone_page_before_accessibility() {
        assertEquals(
            FirstRunPage.Phone,
            consentPageFor(listOf(PaymentAccessNeed.Phone, PaymentAccessNeed.Accessibility))
        )
        assertEquals(
            FirstRunPage.Accessibility,
            consentPageFor(listOf(PaymentAccessNeed.Accessibility))
        )
    }

    @Test
    fun region_page_lets_the_user_confirm_the_country() {
        assertEquals("Where is this phone?", FirstRunWizard.REGION_TITLE)
        assertTrue(FirstRunWizard.REGION_BODY.contains("SIM", ignoreCase = true))
        assertTrue(FirstRunWizard.REGION_BODY.contains("Settings"))
        assertEquals("🇰🇪", AppRegion.KENYA.flagEmoji)
        assertEquals("🇺🇬", AppRegion.UGANDA.flagEmoji)
    }

    @Test
    fun providers_page_is_wallets_then_banks() {
        assertEquals("Who should be in the picture?", FirstRunWizard.PROVIDERS_TITLE)
        assertTrue(FirstRunWizard.PROVIDERS_BODY.contains("Wallets first"))
        assertTrue(FirstRunWizard.PROVIDERS_BODY.contains("Settings"))
    }

    @Test
    fun welcome_is_short_and_not_a_permission_disclosure() {
        assertTrue(FirstRunWizard.WELCOME_BODY.length < 160)
        assertTrue(FirstRunWizard.WELCOME_TITLE.contains("story", ignoreCase = true))
        assertTrue(FirstRunWizard.WELCOME_BODY.contains("send", ignoreCase = true))
        assertFalse(FirstRunWizard.WELCOME_BODY.contains("AccessibilityService"))
        assertFalse(FirstRunWizard.WELCOME_BODY.contains("READ_SMS"))
    }

    @Test
    fun sms_page_uses_money_management_disclosure_not_otp() {
        val body = FirstRunWizard.smsBody(false)
        assertEquals(SmsPermissionDisclosure.TITLE, FirstRunWizard.SMS_TITLE)
        assertTrue(body.contains("money management", ignoreCase = true))
        assertTrue(body.contains("does not read OTPs", ignoreCase = true))
        assertFalse(body.contains("AccessibilityService API"))
    }

    @Test
    fun accessibility_page_is_standalone_disclosure() {
        assertEquals(AccessibilityServiceDisclosure.TITLE, FirstRunWizard.ACCESSIBILITY_TITLE)
        assertEquals(
            AccessibilityServiceDisclosure.STANDARD_BODY,
            FirstRunWizard.ACCESSIBILITY_BODY
        )
        AccessibilityServiceDisclosure.REQUIRED_PHRASES.forEach { phrase ->
            assertTrue(FirstRunWizard.ACCESSIBILITY_BODY.contains(phrase, ignoreCase = true))
        }
        assertFalse(
            FirstRunWizard.ACCESSIBILITY_BODY.contains(SmsPermissionDisclosure.STANDARD_BODY)
        )
        assertFalse(FirstRunWizard.ACCESSIBILITY_BODY.contains("READ_CONTACTS"))
        assertFalse(FirstRunWizard.PHONE_BODY.contains("AccessibilityService API"))
    }

    @Test
    fun settings_turn_on_reuses_wizard_accessibility_disclosure() {
        assertEquals(
            AccessibilityServiceDisclosure.STANDARD_BODY,
            FirstRunWizard.ACCESSIBILITY_BODY
        )
        assertEquals("Agree", AccessibilityServiceDisclosure.AGREE)
        assertEquals("Not now", AccessibilityServiceDisclosure.NOT_NOW)
        assertEquals("Continue without", AccessibilityServiceDisclosure.CONTINUE_WITHOUT)
    }

    @Test
    fun phone_page_does_not_bundle_sms_or_accessibility() {
        assertFalse(FirstRunWizard.PHONE_BODY.contains("SMS", ignoreCase = false))
        assertFalse(FirstRunWizard.PHONE_BODY.contains("AccessibilityService"))
        assertTrue(FirstRunWizard.PHONE_BODY.contains("M-Pesa", ignoreCase = true))
    }

    @Test
    fun notifications_page_explains_confirmation_alerts() {
        assertEquals("Hear when money moves", FirstRunWizard.NOTIFICATIONS_TITLE)
        assertTrue(FirstRunWizard.NOTIFICATIONS_BODY.contains("confirmation", ignoreCase = true))
        assertTrue(FirstRunWizard.NOTIFICATIONS_BODY.contains("without hunting SMS", ignoreCase = true))
        assertTrue(FirstRunWizard.NOTIFICATIONS_BODY.contains("without alerts", ignoreCase = true))
        assertEquals("Allow alerts", PermissionLesson.Notifications.allowLabel)
    }

    @Test
    fun camera_page_covers_ticket_and_pay_codes() {
        assertTrue(FirstRunWizard.CAMERA_BODY.contains("ticket", ignoreCase = true))
        assertTrue(FirstRunWizard.CAMERA_BODY.contains("pay QR", ignoreCase = true))
        assertEquals(RouteConsent.CAMERA, FirstRunPage.Camera.toConsentRoute())
        assertEquals(RouteConsent.NOTIFICATIONS, FirstRunPage.Notifications.toConsentRoute())
        assertEquals(RouteConsent.HIBERNATION, FirstRunPage.Hibernation.toConsentRoute())
    }
}
