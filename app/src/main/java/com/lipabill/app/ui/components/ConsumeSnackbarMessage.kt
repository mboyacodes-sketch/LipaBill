package com.lipabill.app.ui.components

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/** Shows [message] once, then calls [onConsumed] so the source can clear it. */
@Composable
fun ConsumeSnackbarMessage(
    message: String?,
    host: SnackbarHostState,
    onConsumed: () -> Unit
) {
    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        host.showSnackbar(text)
        onConsumed()
    }
}
