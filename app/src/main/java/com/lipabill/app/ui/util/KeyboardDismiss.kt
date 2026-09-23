package com.lipabill.app.ui.util

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController

/**
 * Soft keyboard appears when a text/number field is focused (system default).
 * Put this on screen / sheet roots so tapping empty space clears focus and hides it.
 * Child text fields still receive taps first.
 *
 * Do not combine with IME padding / imeNestedScroll — that resizes or scrolls the
 * whole page when the keyboard opens and fights the layout.
 */
fun Modifier.hideKeyboardOnOutsideTap(): Modifier = composed {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    pointerInput(Unit) {
        detectTapGestures(onTap = {
            focusManager.clearFocus(force = true)
            keyboard?.hide()
        })
    }
}

/**
 * Bottom inset for the system navigation bar only — not the IME.
 * Keyboard overlays content; tap outside (via [hideKeyboardOnOutsideTap]) dismisses it.
 */
fun Modifier.imeAndNavBarsPadding(): Modifier = composed {
    windowInsetsPadding(WindowInsets.navigationBars)
}

/** Nav-bar inset only (same as [imeAndNavBarsPadding]; name kept for call sites). */
fun Modifier.keyboardAvoidingPadding(): Modifier = composed {
    navigationBarsPadding()
}

fun hideKeyboard(
    focusManager: FocusManager,
    keyboard: SoftwareKeyboardController?
) {
    focusManager.clearFocus(force = true)
    keyboard?.hide()
}

@Composable
fun rememberHideKeyboard(): () -> Unit {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    return remember(focusManager, keyboard) {
        { hideKeyboard(focusManager, keyboard) }
    }
}

@Composable
fun rememberKeyboardDismissActions(): KeyboardActions {
    val hide = rememberHideKeyboard()
    return remember(hide) {
        KeyboardActions(
            onDone = { hide() },
            onSearch = { hide() },
            onGo = { hide() },
            onSend = { hide() }
        )
    }
}
