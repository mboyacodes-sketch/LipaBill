package com.lipabill.app.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lipabill.app.BuildConfig
import com.lipabill.app.MarketingLinks
import com.lipabill.app.ui.permissions.SideloadRestrictedSettings
import com.lipabill.app.ui.permissions.rememberAccessibilityToggleCoach
import com.lipabill.app.ui.theme.HomeType
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
    val snackbar = remember { SnackbarHostState() }
    var prevA11yEnabled by remember { mutableStateOf<Boolean?>(null) }
    val a11yCoach = rememberAccessibilityToggleCoach(
        currentlyEnabled = state.lipaBillA11yEnabled,
        onOpenSettings = { viewModel.onAccessibilityToggleConfirmed(state.lipaBillA11yEnabled) }
    )
    val showRestrictedUnlock =
        SideloadRestrictedSettings.shouldShowUnlockButton(context)

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(state.lipaBillA11yEnabled) {
        val prev = prevA11yEnabled
        if (prev != null && prev != state.lipaBillA11yEnabled) {
            snackbar.showSnackbar(
                if (state.lipaBillA11yEnabled) {
                    "LipaBill Accessibility is on"
                } else {
                    "LipaBill Accessibility is off"
                }
            )
        }
        prevA11yEnabled = state.lipaBillA11yEnabled
    }

    a11yCoach.Dialog()

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
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
                .padding(horizontal = Space.page, vertical = Space.pageV)
        ) {
            SettingsSection("Security") {
                SettingsSliderRow(
                    title = "Re-auth timeout",
                    valueLabel = "${state.timeoutMinutes} min",
                    value = state.timeoutMinutes.toFloat(),
                    valueRange = 1f..30f,
                    steps = 28,
                    onValueChange = { viewModel.setTimeoutMinutes(it.roundToInt()) }
                )
                OutlinedButton(
                    onClick = { viewModel.lockNow() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.Lock, contentDescription = null)
                    Spacer(modifier = Modifier.width(Space.gap))
                    Text("Lock now")
                }
            }

            SettingsSection("Display") {
                SettingsSliderRow(
                    title = "Font size",
                    valueLabel = "${state.fontSizeSp} sp",
                    value = state.fontSizeSp.toFloat(),
                    valueRange = 8f..18f,
                    steps = 9,
                    onValueChange = { viewModel.setFontSizeSp(it.roundToInt()) },
                    caption = "Sample · 1,250.00 · THX7K2LM9P"
                )
                SettingsSwitchRow(
                    title = "Always show balance",
                    subtitle = "Off hides it until you tap",
                    checked = state.alwaysShowBalance,
                    onCheckedChange = viewModel::setAlwaysShowBalance
                )
                SettingsSwitchRow(
                    title = "Show favourites",
                    subtitle = "Frequent contacts on home",
                    checked = state.favouritesSectionEnabled,
                    onCheckedChange = viewModel::setFavouritesSectionEnabled
                )
            }

            SettingsSection("Payments") {
                SettingsSwitchRow(
                    title = "Repeat automation",
                    subtitle = "Off = copy details only",
                    checked = state.repeatEnabled,
                    onCheckedChange = viewModel::setRepeatEnabled
                )

                Spacer(modifier = Modifier.height(Space.block))
                Text("Accessibility", style = HomeType.rowTitle)
                Spacer(modifier = Modifier.height(Space.tight))
                Text(
                    text = if (state.lipaBillA11yEnabled) {
                        "On · Send, Pay, Repeat"
                    } else {
                        "Off · needed for Send, Pay, Repeat"
                    },
                    style = HomeType.body,
                    color = if (state.lipaBillA11yEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
                if (showRestrictedUnlock) {
                    Spacer(modifier = Modifier.height(Space.tight))
                    Text(
                        text = "Allow restricted settings in App info first.",
                        style = HomeType.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(Space.gap))
                Button(
                    onClick = { a11yCoach.requestToggle() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (state.lipaBillA11yEnabled) "Turn off" else "Turn on")
                }
                if (showRestrictedUnlock) {
                    Spacer(modifier = Modifier.height(Space.gap))
                    OutlinedButton(
                        onClick = { viewModel.openAppInfoForRestrictedSettings() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Allow restricted settings")
                    }
                }

                Spacer(modifier = Modifier.height(Space.block))
                Text("Default SIM", style = HomeType.rowTitle)
                Spacer(modifier = Modifier.height(Space.tight))
                Text(
                    text = "Safaricom only for M-Pesa payments.",
                    style = HomeType.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(Space.gap))
                when {
                    state.needsPhoneStatePermission -> {
                        Text(
                            text = "Phone permission needed to list SIMs.",
                            style = HomeType.body,
                            color = MaterialTheme.colorScheme.error
                        )
                        TextButton(onClick = onRequestPhoneStatePermission) {
                            Text("Allow phone / SIM access")
                        }
                    }
                    state.simLines.isEmpty() -> {
                        Text(
                            text = "No active SIMs detected.",
                            style = HomeType.body,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    state.simLines.none { it.isSafaricom } -> {
                        Text(
                            text = "No Safaricom SIM — insert one to pay.",
                            style = HomeType.body,
                            color = MaterialTheme.colorScheme.error
                        )
                        state.simLines.forEach { line ->
                            Text(
                                text = line.label,
                                style = HomeType.body,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = Space.tight)
                            )
                        }
                    }
                    else -> {
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
                                                onClick = {
                                                    viewModel.setPreferredSim(line.subscriptionId)
                                                },
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
                                        style = HomeType.body,
                                        color = if (enabled) {
                                            MaterialTheme.colorScheme.onSurface
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                    when {
                                        selected -> Text(
                                            text = "Default for M-Pesa",
                                            style = HomeType.caption,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        !enabled -> Text(
                                            text = "Not Safaricom",
                                            style = HomeType.caption,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (attempts.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(Space.block))
                    Text("Recent repeats", style = HomeType.rowTitle)
                    Spacer(modifier = Modifier.height(Space.tight))
                    attempts.take(5).forEach { attempt ->
                        Text(
                            text = "${attempt.outcome} · ${formatKesSafe(attempt.amount)} · " +
                                (attempt.counterpartyName
                                    ?: attempt.counterpartyPhone
                                    ?: "—"),
                            style = HomeType.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = Space.tight)
                        )
                    }
                }
            }

            SettingsSection("About", showDividerBelow = false) {
                Text(
                    text = "LipaBill ${BuildConfig.VERSION_NAME}",
                    style = HomeType.body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(Space.tight))
                Text(
                    text = "M-Pesa PIN is typed on LipaBill’s keypad, sent once, never stored.",
                    style = HomeType.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(Space.gap))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Space.tight)
                ) {
                    TextButton(onClick = { openHttps(context, MarketingLinks.PRIVACY) }) {
                        Text("Privacy")
                    }
                    TextButton(onClick = { openHttps(context, MarketingLinks.SUPPORT) }) {
                        Text("Support")
                    }
                    TextButton(onClick = { openMailto(context, MarketingLinks.SUPPORT_EMAIL) }) {
                        Text("Email")
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    showDividerBelow: Boolean = true,
    content: @Composable () -> Unit
) {
    Text(title, style = HomeType.section)
    Spacer(modifier = Modifier.height(Space.block))
    content()
    if (showDividerBelow) {
        Spacer(modifier = Modifier.height(Space.section))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(Space.section))
    } else {
        Spacer(modifier = Modifier.height(Space.section))
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Space.gap)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = HomeType.rowTitle)
            Spacer(modifier = Modifier.height(Space.tight))
            Text(
                text = subtitle,
                style = HomeType.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsSliderRow(
    title: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    caption: String? = null
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = Space.gap)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(title, style = HomeType.rowTitle, modifier = Modifier.weight(1f))
            Text(
                text = valueLabel,
                style = HomeType.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth()
        )
        if (caption != null) {
            Text(
                text = caption,
                style = HomeType.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
