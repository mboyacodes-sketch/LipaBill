package com.lipabill.app.device

import android.content.pm.PackageManager
import android.content.res.Configuration
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HandheldDeviceGateTest {

    @Test
    fun phone_and_tablet_are_allowed() {
        assertTrue(handheld())
    }

    @Test
    fun television_watch_car_xr_and_pc_are_blocked() {
        assertFalse(handheld(features = setOf(PackageManager.FEATURE_LEANBACK)))
        assertFalse(handheld(features = setOf("android.hardware.type.television")))
        assertFalse(handheld(features = setOf(PackageManager.FEATURE_WATCH)))
        assertFalse(handheld(features = setOf(PackageManager.FEATURE_AUTOMOTIVE)))
        assertFalse(handheld(features = setOf("android.hardware.type.pc")))
        assertFalse(handheld(features = setOf(PackageManager.FEATURE_EMBEDDED)))
        assertFalse(handheld(features = setOf("android.software.xr.api.spatial")))
        assertFalse(handheld(features = setOf("android.hardware.type.xr")))
        assertFalse(handheld(features = setOf("android.hardware.vr.headtracking")))
        assertFalse(handheld(uiMode = Configuration.UI_MODE_TYPE_TELEVISION))
        assertFalse(handheld(uiMode = Configuration.UI_MODE_TYPE_WATCH))
        assertFalse(handheld(uiMode = Configuration.UI_MODE_TYPE_CAR))
        assertFalse(handheld(uiMode = Configuration.UI_MODE_TYPE_APPLIANCE))
        assertFalse(handheld(uiMode = Configuration.UI_MODE_TYPE_VR_HEADSET))
        assertFalse(handheld(hasTouchscreen = false))
    }

    private fun handheld(
        hasTouchscreen: Boolean = true,
        uiMode: Int = Configuration.UI_MODE_TYPE_NORMAL,
        features: Set<String> = emptySet()
    ): Boolean = HandheldDeviceGate.allows(hasTouchscreen, uiMode, features)
}
