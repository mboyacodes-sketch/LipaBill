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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.lipabill.app.ui.permissions.AccessibilityDisclosure
import com.lipabill.app.ui.permissions.RestrictedSettingsUnlockSteps
import com.lipabill.app.ui.permissions.SideloadRestrictedSettings
import com.lipabill.app.ui.theme.Space

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccessibilityOnboardingScreen(
    onOpenSettings: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onContinue: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val showRestrictedUnlock = SideloadRestrictedSettings.accessibilityUnlockNeeded(context)

    Scaffold(
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
                    "Firebase App Distribution installs are treated as sideloads. " +
                        "Android blocks Accessibility until you allow restricted settings, " +
                        "then turn on LipaBill Repeat Payment."
                } else {
                    "You turn Accessibility on yourself in system Settings. " +
                        "LipaBill opens the LipaBill Repeat Payment toggle screen — " +
                        "flip the switch only if you agree with the disclosure below."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (showRestrictedUnlock) {
                Spacer(modifier = Modifier.height(Space.section))
                RestrictedSettingsUnlockSteps()
                Spacer(modifier = Modifier.height(Space.section))
                Text("What Accessibility will do", style = MaterialTheme.typography.titleMedium)
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
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("1 · Open LipaBill toggle")
                }
                Spacer(modifier = Modifier.height(Space.gap))
                Button(
                    onClick = onOpenAppInfo,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("2 · Open App info (unlock)")
                }
                Spacer(modifier = Modifier.height(Space.gap))
                OutlinedButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("3 · Open LipaBill toggle again")
                }
            } else {
                Spacer(modifier = Modifier.height(Space.pageV + Space.block))
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
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open LipaBill toggle")
                }
            }

            Spacer(modifier = Modifier.height(Space.gap))
            OutlinedButton(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("I've enabled it — continue")
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
