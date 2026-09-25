package com.lipabill.app.ui.repeat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lipabill.app.ui.permissions.AccessibilityDisclosure
import com.lipabill.app.ui.permissions.RestrictedSettingsUnlockSteps
import com.lipabill.app.ui.permissions.SideloadRestrictedSettings
import com.lipabill.app.ui.permissions.rememberAccessibilityToggleCoach
import com.lipabill.app.ui.theme.Space
import com.lipabill.app.ussd.AccessibilityHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccessibilityOnboardingScreen(
    onOpenSettings: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onContinue: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var a11yEnabled by remember {
        mutableStateOf(AccessibilityHelper.isLipaBillServiceEnabled(context))
    }
    var showRestrictedUnlock by remember {
        mutableStateOf(SideloadRestrictedSettings.shouldShowUnlockButton(context))
    }
    var prevA11y by remember { mutableStateOf<Boolean?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val coach = rememberAccessibilityToggleCoach(
        currentlyEnabled = a11yEnabled,
        onOpenSettings = onOpenSettings
    )

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                a11yEnabled = AccessibilityHelper.isLipaBillServiceEnabled(context)
                showRestrictedUnlock =
                    SideloadRestrictedSettings.shouldShowUnlockButton(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(a11yEnabled) {
        val prev = prevA11y
        if (prev != null && prev != a11yEnabled) {
            snackbar.showSnackbar(
                if (a11yEnabled) {
                    "LipaBill Accessibility is on"
                } else {
                    "LipaBill Accessibility is off"
                }
            )
        }
        prevA11y = a11yEnabled
    }

    coach.Dialog()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Enable Repeat Payment") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Space.page)
        ) {
            Icon(
                imageVector = Icons.Outlined.AccessibilityNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = Space.block)
            )
            Text(
                text = AccessibilityDisclosure.TITLE,
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(Space.block))
            Text(
                text = if (showRestrictedUnlock) {
                    "Sideload installs may need Allow restricted settings first, " +
                        "then turn on LipaBill Repeat Payment."
                } else {
                    "You flip one switch in system Settings. " +
                        "LipaBill opens the right screen — turn it on only if you agree below."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(Space.block))
            Text(
                text = if (a11yEnabled) {
                    "On · LipaBill Repeat Payment"
                } else {
                    "Off · LipaBill Repeat Payment"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (a11yEnabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                }
            )

            if (showRestrictedUnlock) {
                Spacer(modifier = Modifier.height(Space.section))
                RestrictedSettingsUnlockSteps()
            }

            Spacer(modifier = Modifier.height(Space.section))
            Text("What it will do", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(Space.gap))
            Text(
                text = AccessibilityDisclosure.WHAT_IT_DOES,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(Space.block))
            Text("What it will not do", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(Space.gap))
            Text(
                text = AccessibilityDisclosure.WHAT_IT_DOES_NOT,
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(modifier = Modifier.height(Space.section))
            Button(
                onClick = { coach.requestToggle() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (a11yEnabled) "Turn off" else "Turn on")
            }
            if (showRestrictedUnlock) {
                Spacer(modifier = Modifier.height(Space.gap))
                OutlinedButton(
                    onClick = onOpenAppInfo,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Allow restricted settings")
                }
            }

            Spacer(modifier = Modifier.height(Space.gap))
            OutlinedButton(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (a11yEnabled) "Continue" else "I've enabled it — continue"
                )
            }
            Spacer(modifier = Modifier.height(Space.gap))
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel")
            }
        }
    }
}
