package com.lipabill.app.ussd

import com.lipabill.app.resolveRepoFile
import com.lipabill.app.ui.permissions.AccessibilityServiceDisclosure
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PlaySettingsWalkSourceTest {

    @Test
    fun play_source_set_has_no_settings_toggle_navigator() {
        val playUssd = resolveRepoFile("app/src/play/java/com/lipabill/app/ussd")
        assertTrue("Missing play ussd source dir", playUssd != null && playUssd.isDirectory)
        assertFalse(
            "Play AAB must not ship SettingsToggleNavigator",
            File(playUssd, "SettingsToggleNavigator.kt").exists()
        )
        val walk = File(playUssd, "SideloadSettingsWalk.kt").readText()
        assertTrue(walk.contains("fun start(label: String): Boolean = false"))
        assertFalse(walk.contains("performAction"))
        assertFalse(walk.contains("ACTION_CLICK"))
        assertFalse(walk.contains("SettingsToggleNavigator"))
    }

    @Test
    fun main_accessibility_code_does_not_name_settings_toggle_navigator() {
        val mainUssd = resolveRepoFile("app/src/main/java/com/lipabill/app/ussd")
        assertTrue(mainUssd != null && mainUssd.isDirectory)
        val helper = File(mainUssd, "AccessibilityHelper.kt").readText()
        val service = File(mainUssd, "UssdAccessibilityService.kt").readText()
        assertFalse(helper.contains("SettingsToggleNavigator"))
        assertFalse(service.contains("SettingsToggleNavigator"))
        assertTrue(helper.contains("SideloadSettingsWalk"))
        assertTrue(service.contains("SideloadSettingsWalk"))
    }

    @Test
    fun coach_copy_uses_mpesa_payments_label_not_repeat_payment() {
        val label = AccessibilityServiceDisclosure.SERVICE_LABEL
        val samsung = AccessibilityHelper.coachMessage(turningOn = false, serviceLabel = label)
        val turningOn = AccessibilityHelper.coachMessage(turningOn = true, serviceLabel = label)
        assertTrue(samsung.contains(label))
        assertTrue(turningOn.contains(label))
        assertFalse(samsung.contains("Repeat Payment"))
        assertFalse(turningOn.contains("Repeat Payment"))
    }
}
