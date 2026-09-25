package com.lipabill.app.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lipabill.app.BuildConfig
import com.lipabill.app.MarketingLinks
import com.lipabill.app.ui.permissions.RestrictedSettingsUnlockSteps
import com.lipabill.app.ui.permissions.SideloadRestrictedSettings
import com.lipabill.app.ui.theme.Space
import com.lipabill.app.viewmodel.SettingsViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: (() -> Unit)? = null,
    onRequestPhoneStatePermission: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val attempts by viewModel.recentAttempts.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
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
            Text("Re-auth timeout", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(Space.tight))
            Text(
                text = "Require unlock again after the app has been in the background " +
                    "for this long. Default is 2 minutes.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Space.block))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "${state.timeoutMinutes} min",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(end = Space.block)
                )
                Slider(
                    value = state.timeoutMinutes.toFloat(),
                    onValueChange = { viewModel.setTimeoutMinutes(it.roundToInt()) },
                    valueRange = 1f..30f,
                    steps = 28,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(Space.section))
            Text("Font size", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(Space.tight))
            Text(
                text = "Montserrat geometric sans across the whole app. Default 12.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Space.block))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "${state.fontSizeSp} sp",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(end = Space.block)
                )
                Slider(
                    value = state.fontSizeSp.toFloat(),
                    onValueChange = { viewModel.setFontSizeSp(it.roundToInt()) },
                    valueRange = 8f..18f,
                    steps = 9,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(Space.gap))
            Text(
                text = "Sample · 1,250.00 · THX7K2LM9P",
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(modifier = Modifier.height(Space.section))
            Text("Balance privacy", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(Space.tight))
            Text(
                text = "When off, balance stays hidden as asterisks until you tap it — then hides again after a few seconds.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Space.block))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Always display balance",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium
                )
                Switch(
                    checked = state.alwaysShowBalance,
                    onCheckedChange = viewModel::setAlwaysShowBalance
                )
            }

            Spacer(modifier = Modifier.height(Space.section))
            Text("Favourites", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(Space.tight))
            Text(
                text = "When on, the Frequent contacts row appears on the home screen.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Space.block))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Show favourites",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium
                )
                Switch(
                    checked = state.favouritesSectionEnabled,
                    onCheckedChange = viewModel::setFavouritesSectionEnabled
                )
            }

            Spacer(modifier = Modifier.height(Space.section))
            Button(
                onClick = { viewModel.lockNow() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.Lock, contentDescription = null)
                Spacer(modifier = Modifier.padding(Space.tight))
                Text("Lock now")
            }

            Spacer(modifier = Modifier.height(Space.section))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(Space.section))

            Text("Repeat payment", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(Space.tight))
            Text(
                text = "When off, Repeat shows copy-details only — no Accessibility automation.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Space.block))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Enable repeat automation",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium
                )
                Switch(
                    checked = state.repeatEnabled,
                    onCheckedChange = viewModel::setRepeatEnabled
                )
            }

            Spacer(modifier = Modifier.height(Space.pageV + Space.block))
            Text("Default SIM", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(Space.tight))
            Text(
                text = "LipaBill dials M-Pesa on your Safaricom SIM automatically. " +
                    "Other SIMs are listed but can’t be used for Send, Pay, or Repeat.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Space.gap))
            if (state.needsPhoneStatePermission) {
                Text(
                    text = "Phone permission is needed to list SIMs.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(Space.tight))
                TextButton(onClick = onRequestPhoneStatePermission) {
                    Text("Allow phone / SIM access")
                }
            } else if (state.simLines.isEmpty()) {
                Text(
                    text = "No active SIMs detected.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (state.simLines.none { it.isSafaricom }) {
                Text(
                    text = "No Safaricom SIM found. You can still browse LipaBill — " +
                        "insert a Safaricom line to Send, Pay, or Repeat.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(Space.gap))
                state.simLines.forEach { line ->
                    Text(
                        text = line.label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = Space.tight)
                    )
                }
            } else {
                state.simLines.forEach { line ->
                    val selected = state.preferredSimSubscriptionId == line.subscriptionId
                    val enabled = line.isSafaricom
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (enabled) {
                                    Modifier.selectable(
                                        selected = selected,
                                        onClick = { viewModel.setPreferredSim(line.subscriptionId) },
                                        role = Role.RadioButton
                                    )
                                } else {
                                    Modifier
                                }
                            )
                            .padding(vertical = Space.gap)
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = if (enabled) {
                                { viewModel.setPreferredSim(line.subscriptionId) }
                            } else {
                                null
                            },
                            enabled = enabled
                        )
                        Spacer(modifier = Modifier.width(Space.gap))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = line.label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (enabled) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                            when {
                                selected -> Text(
                                    text = "Default — used for M-Pesa payments",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                !enabled -> Text(
                                    text = "Not Safaricom — can’t dial M-Pesa",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(Space.pageV + Space.block))
            Text("Accessibility", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(Space.tight))
            Text(
                text = if (state.lipaBillA11yEnabled) {
                    "LipaBill Repeat Payment is enabled."
                } else {
                    "LipaBill Repeat Payment is not enabled."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (state.lipaBillA11yEnabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                }
            )
            val needsRestrictedUnlock =
                SideloadRestrictedSettings.accessibilityUnlockNeeded(LocalContext.current)
            if (needsRestrictedUnlock && !state.lipaBillA11yEnabled) {
                Spacer(modifier = Modifier.height(Space.block))
                RestrictedSettingsUnlockSteps()
                Spacer(modifier = Modifier.height(Space.block))
                Button(
                    onClick = { viewModel.openAccessibilitySettings() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("1 · Open LipaBill toggle")
                }
                Spacer(modifier = Modifier.height(Space.gap))
                Button(
                    onClick = { viewModel.openAppInfoForRestrictedSettings() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("2 · Open App info (unlock)")
                }
                Spacer(modifier = Modifier.height(Space.gap))
                OutlinedButton(
                    onClick = { viewModel.openAccessibilitySettings() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("3 · Open LipaBill toggle again")
                }
            } else {
                Spacer(modifier = Modifier.height(Space.block))
                Button(
                    onClick = { viewModel.openAccessibilitySettings() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open LipaBill toggle")
                }
            }

            Spacer(modifier = Modifier.height(Space.section))
            Text("Recent repeat attempts", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(Space.gap))
            if (attempts.isEmpty()) {
                Text(
                    text = "No repeat attempts yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                attempts.forEach { attempt ->
                    Text(
                        text = "${attempt.outcome} · ${formatKesSafe(attempt.amount)} · " +
                            "${attempt.counterpartyName ?: attempt.counterpartyPhone ?: "—"}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = Space.tight)
                    )
                }
            }

            Spacer(modifier = Modifier.height(Space.section))
            Text(
                text = "You enter your M-Pesa PIN on LipaBill’s keypad (hidden). " +
                    "It is sent once into the prompt and never stored. Payment success is " +
                    "confirmed only when a new M-Pesa SMS appears after sync.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(Space.section))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(Space.section))
            Text("About & legal", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(Space.tight))
            Text(
                text = "LipaBill ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Space.gap))
            TextButton(
                onClick = { openHttps(context, MarketingLinks.PRIVACY) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Privacy policy")
            }
            TextButton(
                onClick = { openHttps(context, MarketingLinks.SUPPORT) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Help & support")
            }
            TextButton(
                onClick = { openMailto(context, MarketingLinks.SUPPORT_EMAIL) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Email support")
            }
        }
    }
}

private fun openHttps(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

private fun openMailto(context: android.content.Context, email: String) {
    runCatching {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
            putExtra(Intent.EXTRA_SUBJECT, "LipaBill support")
        }
        context.startActivity(Intent.createChooser(intent, "Email support"))
    }
}

private fun formatKesSafe(amount: Double?): String =
    if (amount == null) "—" else "%.2f".format(amount)
