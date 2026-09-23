package com.lipabill.app.ui.util

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
 * Use on main shell / home so the keyboard does not resize the whole app.
 */
fun Modifier.imeAndNavBarsPadding(): Modifier = composed {
    windowInsetsPadding(WindowInsets.navigationBars)
}

/**
 * For Send/Pay sheets and similar: lift content above the IME so the CTA sits on
 * the keyboard, not over fields. Pair with [bringIntoViewOnFocus] on text fields
 * and a scrollable body so the focused field scrolls into the gap above the CTA.
 */
fun Modifier.sheetKeyboardPadding(): Modifier = composed {
    navigationBarsPadding().imePadding()
}

/**
 * When this field is focused, scroll/relocate it into the visible area after the
 * IME animation — keeps the caret above the sticky CTA / keyboard.
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
