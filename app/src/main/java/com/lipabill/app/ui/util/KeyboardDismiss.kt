package com.lipabill.app.ui.util

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Soft keyboard appears when a text/number field is focused (system default).
 * Put this on screen / sheet roots so tapping empty space clears focus and hides it.
 * Child text fields still receive taps first.
 */
fun Modifier.hideKeyboardOnOutsideTap(onOutsideTap: () -> Unit = {}): Modifier = composed {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val outsideTap = rememberUpdatedState(onOutsideTap)
    pointerInput(Unit) {
        detectTapGestures(onTap = {
            focusManager.clearFocus(force = true)
            keyboard?.hide()
            outsideTap.value()
        })
    }
}

/**
 * Status, navigation, cutout, and IME. Use on content that must not sit under
 * the system bars. Keep this off decorative backgrounds so they can draw edge-to-edge.
 */
fun Modifier.systemBarsAndCutoutPadding(): Modifier = composed {
    windowInsetsPadding(WindowInsets.safeDrawing)
}

/**
 * Bottom sheet content sits under the status bar gap already — only keep
 * horizontal cutout and navigation-bar padding so the keypad stays on screen.
 */
fun Modifier.moneySheetWindowPadding(): Modifier = composed {
    windowInsetsPadding(
        WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
    )
}

/** Status bar and horizontal cutout. Canvas behind can still draw under the nav bar. */
fun Modifier.safeDrawingTopPadding(): Modifier = composed {
    windowInsetsPadding(
        WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
    )
}

@Composable
fun safeDrawingBottomPadding(): Dp =
    WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()

/**
 * When this field is focused, scroll it into the visible area after the IME
 * animation so the caret stays above the sticky action.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.bringIntoViewOnFocus(delayMs: Long = 280L): Modifier = composed {
    val requester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    Modifier
        .bringIntoViewRequester(requester)
        .onFocusEvent { focusState ->
            if (focusState.isFocused) {
                scope.launch {
                    delay(delayMs)
                    requester.bringIntoView()
                }
            }
        }
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
