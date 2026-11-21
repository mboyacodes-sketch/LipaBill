package com.lipabill.app.ui.theme

import android.os.Build
import android.view.Window
import android.view.WindowManager
import androidx.core.view.WindowCompat

/**
 * Edge-to-edge without the APIs Play Console flags as deprecated on Android 15+:
 * [Window.setStatusBarColor], [Window.setNavigationBarColor], and
 * [WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES].
 *
 * Transparent bar colors come from themes.xml. Icon contrast is set here.
 * Prefer this over [androidx.activity.enableEdgeToEdge] / [WindowCompat.enableEdgeToEdge],
 * which still reference those deprecated Window APIs in their bytecode.
 */
object LipaEdgeToEdge {

    fun enable(window: Window, lightIcons: Boolean) {
        // Force decor creation before attribute writes so later init cannot overwrite them.
        window.decorView
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        // ALWAYS exists from API 30. Never write SHORT_EDGES — Play scans the constant in dex.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val attrs = window.attributes
            if (attrs.layoutInDisplayCutoutMode !=
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            ) {
                attrs.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                window.attributes = attrs
            }
        }
        applySystemBarIcons(window, lightIcons)
    }

    fun applySystemBarIcons(window: Window, lightIcons: Boolean) {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = lightIcons
            isAppearanceLightNavigationBars = lightIcons
        }
    }
}
