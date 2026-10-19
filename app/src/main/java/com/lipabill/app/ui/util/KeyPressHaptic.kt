package com.lipabill.app.ui.util

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/** Light keyboard tick, the same one a system key uses. */
fun View.performKeyPressHaptic() {
    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
}

@Composable
fun rememberKeyPressHaptic(): () -> Unit {
    val view = LocalView.current
    return remember(view) { { view.performKeyPressHaptic() } }
}
