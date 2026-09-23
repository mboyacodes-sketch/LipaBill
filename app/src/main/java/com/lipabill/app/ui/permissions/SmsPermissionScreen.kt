package com.lipabill.app.ui.permissions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lipabill.app.ui.theme.Space

@Composable
fun SmsPermissionScreen(
    permanentlyDenied: Boolean,
    showRestrictedSettingsHelp: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onContinueWithout: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = maxHeight)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.page, vertical = Space.section),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Sms,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(Space.pageV + Space.block))
            Text(
                text = SmsPermissionDisclosure.TITLE,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(Space.block))
            Text(
                text = SmsPermissionDisclosure.body(showRestrictedSettingsHelp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            if (showRestrictedSettingsHelp) {
                Spacer(modifier = Modifier.height(Space.section))
                RestrictedSettingsSteps()
                Spacer(modifier = Modifier.height(Space.section))
                Button(
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open App info")
                }
                Spacer(modifier = Modifier.height(Space.gap))
                OutlinedButton(
                    onClick = onRequestPermission,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("I've unlocked — Allow SMS")
                }
                Spacer(modifier = Modifier.height(Space.gap))
                Text(
                    text = "SMS is still blocked. Complete Allow restricted settings, " +
                        "then Permissions → SMS → Allow.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            } else if (permanentlyDenied) {
                Spacer(modifier = Modifier.height(Space.section))
                Button(
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open system Settings")
                }
                Spacer(modifier = Modifier.height(Space.gap))
                Text(
                    text = "Permission was denied. Enable SMS in App info → Permissions.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            } else {
                Spacer(modifier = Modifier.height(Space.section))
                Button(
                    onClick = onRequestPermission,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Allow SMS access")
                }
            }

            Spacer(modifier = Modifier.height(Space.block))
            OutlinedButton(
                onClick = onContinueWithout,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Continue without SMS")
            }
        }
    }
}

@Composable
private fun RestrictedSettingsSteps() {
    val steps = listOf(
        "Tap Open App info below",
        "Tap ⋮ (or More) → Allow restricted settings",
        "Permissions → SMS → Allow",
        "Return here — or tap I've unlocked — Allow SMS"
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Space.gap)
    ) {
        steps.forEachIndexed { index, step ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "${index + 1}.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(Space.gap))
                Text(
                    text = step,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
