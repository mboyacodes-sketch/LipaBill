package com.lipabill.app.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.InterceptPlatformTextInput
import kotlinx.coroutines.awaitCancellation

/**
 * Blocks the system soft keyboard while [content] is composed.
 * Use around sheet text fields that are driven by [com.lipabill.app.ui.sheet.InAppKeyboard].
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun InterceptSystemIme(content: @Composable () -> Unit) {
    InterceptPlatformTextInput(
        interceptor = { _, _ ->
            // Never start the platform IME session.
            awaitCancellation()
        },
        content = content
    )
}
