package com.lipabill.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lipabill.app.BuildConfig
import com.lipabill.app.MarketingLinks
import com.lipabill.app.ui.components.AccentGlyphBadge
import com.lipabill.app.ui.util.openHttpsUrl
import com.lipabill.app.ui.util.openMailto
import com.lipabill.app.data.model.groupedProviderCatalog
import com.lipabill.app.R
import com.lipabill.app.data.local.entity.RepeatAttemptEntity
import com.lipabill.app.ui.LipaChoiceChip
import com.lipabill.app.ui.ProviderPicker
import com.lipabill.app.ui.RegionPicker
import com.lipabill.app.ui.permissions.rememberAccessibilityToggleCoach
import com.lipabill.app.ui.privacy.RecordingCapture
import com.lipabill.app.ui.privacy.recordingPrivacyCover
import com.lipabill.app.ui.theme.Accent
import com.lipabill.app.ui.theme.Canvas
import com.lipabill.app.ui.theme.CardWhite
import com.lipabill.app.ui.theme.Expense
import com.lipabill.app.ui.theme.Hairline
import com.lipabill.app.ui.theme.HomeType
import com.lipabill.app.ui.theme.Ink
import com.lipabill.app.ui.theme.Mute
import com.lipabill.app.ui.theme.OnAccent
import com.lipabill.app.ui.theme.Space
import com.lipabill.app.ui.util.formatActivityTime
import com.lipabill.app.ui.util.formatKes
import com.lipabill.app.ussd.RepeatOutcome
import com.lipabill.app.ussd.SimLine
import com.lipabill.app.viewmodel.SettingsViewModel
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: (() -> Unit)? = null,
    onRequestPhoneStatePermission: () -> Unit = {},
    onRequestAccessibilityConsent: () -> Unit = {},
    onOptInFuliza: () -> Unit = {},
    onCheckForUpdate: () -> Unit = {},
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
                    "Send, Pay, and Repeat are on"
                } else {
                    "Send, Pay, and Repeat stay off until you turn this back on"
                }
            )
        }
        prevA11yEnabled = state.lipaBillA11yEnabled
    }

    a11yCoach.Dialog()

    Scaffold(
        modifier = modifier,
        containerColor = Canvas,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Settings", style = HomeType.greeting, color = Ink) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Ink
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Canvas,
                    titleContentColor = Ink,
                    navigationIconContentColor = Ink
                )
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
            SettingsSection("Picture", Icons.Outlined.AccountBalanceWallet) {
                SettingsChoiceBlock(
                    title = "Country",
                    hint = if (state.region.canSendPay) {
                        "Ledger for ${state.region.label}. Send and Pay stay on M-Pesa."
                    } else {
                        "Ledger for ${state.region.label}. Send and Pay are Kenya M-Pesa for now."
                    }
                ) {
                    RegionPicker(
                        selected = state.region,
                        onSelect = viewModel::setRegion,
                        modifier = Modifier.padding(bottom = Space.card)
                    )
                }
                SettingsDivider()
                SettingsChoiceBlock(
                    title = "Ledger",
                    hint = "Up to 5 wallets and banks on this picture. Changing this refreshes the ledger."
                ) {
                    ProviderPicker(
                        catalog = state.providerChoices,
                        groups = groupedProviderCatalog(state.region),
                        selected = state.enabledSources,
                        onToggle = viewModel::toggleProvider,
                        modifier = Modifier.padding(bottom = Space.card)
                    )
                }
            }

            if (state.region.canSendPay) {
                SettingsSection("Payments", Icons.Outlined.Payments) {
                    SimChoices(
                        needsPermission = state.needsPhoneStatePermission,
                        lines = state.simLines,
                        selectedId = state.preferredSimSubscriptionId,
                        onAllow = onRequestPhoneStatePermission,
                        onSelect = viewModel::setPreferredSim
                    )
                    SettingsDivider()
                    SettingsNavRow(
                        title = "Fill payment screens",
                        subtitle = if (state.lipaBillA11yEnabled) {
                            "On. Send, Pay, and Repeat are available."
                        } else {
                            "Off. Send, Pay, and Repeat stay off until this is on."
                        },
                        action = if (state.lipaBillA11yEnabled) "Turn off" else "Turn on",
                        actionEmphasis = !state.lipaBillA11yEnabled,
                        onClick = {
                            if (state.lipaBillA11yEnabled) {
                                a11yCoach.requestToggle()
                            } else {
                                onRequestAccessibilityConsent()
                            }
                        }
                    )
                    SettingsDivider()
                    SettingsSwitchRow(
                        title = "Automatic repeat",
                        subtitle = "Fills a past payment. Off copies the details.",
                        checked = state.repeatEnabled,
                        onCheckedChange = viewModel::setRepeatEnabled
                    )
                    SettingsDivider()
                    SettingsNavRow(
                        title = "Opt in to Fuliza",
                        subtitle = "Opens Safaricom’s Fuliza screens on *334#.",
                        action = "Start",
                        onClick = onOptInFuliza
                    )
                    if (attempts.isNotEmpty()) {
                        SettingsDivider()
                        Text(
                            text = "Last payments",
                            style = HomeType.label,
                            color = Mute,
                            modifier = Modifier.padding(top = Space.card, bottom = Space.gap)
                        )
                        attempts.take(3).forEach { attempt ->
                            RecentAttemptRow(attempt)
                        }
                        Spacer(modifier = Modifier.height(Space.card))
                    }
                }
            }

            SettingsSection("Home", Icons.Outlined.Home) {
                SettingsSwitchRow(
                    title = "Dark mode",
                    subtitle = "Navy screens at night. Passes stay paper white.",
                    checked = state.darkMode,
                    onCheckedChange = viewModel::setDarkMode
                )
                SettingsDivider()
                SettingsChoiceBlock(title = "Text size") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = Space.card),
                        horizontalArrangement = Arrangement.spacedBy(Space.gap)
                    ) {
                        val selected = nearestTextSizeChoice(state.fontSizeSp)
                        TEXT_SIZE_CHOICES.forEach { choice ->
                            LipaChoiceChip(
                                label = choice.label,
                                selected = choice.sp == selected.sp,
                                onClick = { viewModel.setFontSizeSp(choice.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            SettingsSection("Security", Icons.Outlined.Shield) {
                SettingsChoiceBlock(
                    title = "Lock after",
                    hint = "Unlock again after you leave the app."
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Space.gap),
                        verticalArrangement = Arrangement.spacedBy(Space.gap),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = Space.card)
                    ) {
                        lockMinuteChoices(state.timeoutMinutes).forEach { minutes ->
                            LipaChoiceChip(
                                label = minutesLabel(minutes),
                                selected = minutes == state.timeoutMinutes,
                                onClick = { viewModel.setTimeoutMinutes(minutes) }
                            )
                        }
                    }
                }
                SettingsDivider()
                SettingsActionRow(
                    title = "Lock now",
                    onClick = { viewModel.lockNow() }
                )
            }

            SettingsSection("About", Icons.Outlined.Info) {
                SettingsNavRow(
                    title = stringResource(R.string.settings_check_update),
                    subtitle = "LipaBill ${BuildConfig.VERSION_NAME}",
                    showChevron = true,
                    onClick = onCheckForUpdate
                )
                SettingsDivider()
                SettingsNavRow(
                    title = "Privacy",
                    subtitle = "SMS, Accessibility, and what’s on this phone.",
                    showChevron = true,
                    onClick = { openHttps(context, MarketingLinks.PRIVACY) }
                )
                SettingsDivider()
                SettingsNavRow(
                    title = "Support",
                    subtitle = "Guides on lipabill.app",
                    showChevron = true,
                    onClick = { openHttps(context, MarketingLinks.SUPPORT) }
                )
                SettingsDivider()
                SettingsNavRow(
                    title = "Email",
                    subtitle = MarketingLinks.SUPPORT_EMAIL,
                    showChevron = true,
                    onClick = { openSupportEmail(context, MarketingLinks.SUPPORT_EMAIL) }
                )
            }

            if (RecordingCapture.available) {
                SettingsSection("Developer", Icons.Outlined.Code) {
                    SettingsSwitchRow(
                        title = "Screen recording",
                        subtitle = "Off by default. On clears capture protection and blurs names and amounts for scrcpy.",
                        checked = state.recordingPrivacy,
                        onCheckedChange = viewModel::setRecordingPrivacy
                    )
                }
            }

            if (state.region.canSendPay) {
                Text(
                    text = "Your PIN is typed on LipaBill’s keypad and never stored.",
                    style = HomeType.caption,
                    color = Mute,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(modifier = Modifier.height(Space.section))
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = Space.section)
            .clip(RoundedCornerShape(16.dp))
            .background(CardWhite)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(
                start = Space.card,
                end = Space.card,
                top = Space.card,
                bottom = Space.block
            )
        ) {
            AccentGlyphBadge(icon, size = 36.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Text(title, style = HomeType.section, color = Ink)
        }
        HorizontalDivider(color = Hairline, thickness = 1.dp)
        Column(
            modifier = Modifier.padding(horizontal = Space.card),
            content = content
        )
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(color = Hairline, thickness = 1.dp)
}

@Composable
private fun SettingsChoiceBlock(
    title: String,
    hint: String? = null,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Space.card)
    ) {
        Text(title, style = HomeType.rowTitle, color = Ink)
        if (hint != null) {
            Spacer(modifier = Modifier.height(Space.tight))
            Text(hint, style = HomeType.caption, color = Mute)
        }
        Spacer(modifier = Modifier.height(Space.gap))
        content()
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
            .clickable(role = Role.Switch) { onCheckedChange(!checked) }
            .padding(vertical = Space.card)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = HomeType.rowTitle, color = Ink)
            Spacer(modifier = Modifier.height(Space.tight))
            Text(subtitle, style = HomeType.caption, color = Mute)
        }
        Spacer(modifier = Modifier.width(Space.card))
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = OnAccent,
                checkedTrackColor = Accent,
                uncheckedThumbColor = OnAccent,
                uncheckedTrackColor = Mute.copy(alpha = 0.35f)
            )
        )
    }
}

@Composable
private fun SettingsActionRow(
    title: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Space.card)
    ) {
        Text(title, style = HomeType.rowTitle, color = Ink)
    }
}

@Composable
private fun SettingsNavRow(
    title: String,
    onClick: () -> Unit,
    subtitle: String? = null,
    action: String = "",
    actionEmphasis: Boolean = false,
    showChevron: Boolean = false
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Space.card)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = HomeType.rowTitle, color = Ink)
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(Space.tight))
                Text(subtitle, style = HomeType.caption, color = Mute)
            }
        }
        if (action.isNotEmpty()) {
            Spacer(modifier = Modifier.width(Space.card))
            Text(
                text = action,
                style = HomeType.body,
                color = if (actionEmphasis) Accent else Mute
            )
        }
        if (showChevron) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Mute
            )
        }
    }
}

@Composable
private fun SimChoices(
    needsPermission: Boolean,
    lines: List<SimLine>,
    selectedId: Int?,
    onAllow: () -> Unit,
    onSelect: (Int) -> Unit
) {
    when {
        needsPermission -> {
            SettingsNavRow(
                title = "Payment SIM",
                subtitle = "Allow SIM access to choose the line.",
                action = "Allow",
                actionEmphasis = true,
                onClick = onAllow
            )
        }
        lines.isEmpty() -> {
            Column(modifier = Modifier.padding(vertical = Space.card)) {
                Text("Payment SIM", style = HomeType.rowTitle, color = Ink)
                Spacer(modifier = Modifier.height(Space.tight))
                Text("No SIM in this phone.", style = HomeType.caption, color = Mute)
            }
        }
        lines.none { it.isSafaricom } -> {
            Column(modifier = Modifier.padding(vertical = Space.card)) {
                Text("Payment SIM", style = HomeType.rowTitle, color = Ink)
                Spacer(modifier = Modifier.height(Space.tight))
                Text(
                    "Insert a Safaricom SIM to pay.",
                    style = HomeType.caption,
                    color = Expense
                )
                lines.forEach { line ->
                    Text(
                        text = line.label,
                        style = HomeType.caption,
                        color = Mute,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(top = Space.tight)
                            .recordingPrivacyCover()
                    )
                }
            }
        }
        else -> {
            val safaricom = lines.filter { it.isSafaricom }
            val others = lines.filterNot { it.isSafaricom }
            if (safaricom.size == 1) {
                val line = safaricom.first()
                Column(modifier = Modifier.padding(vertical = Space.card)) {
                    Text("Payment SIM", style = HomeType.rowTitle, color = Ink)
                    Spacer(modifier = Modifier.height(Space.tight))
                    Text(
                        text = line.label,
                        style = HomeType.caption,
                        color = Mute,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.recordingPrivacyCover()
                    )
                    if (others.isNotEmpty()) {
                        Text(
                            text = others.joinToString { it.label },
                            style = HomeType.caption,
                            color = Mute,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .padding(top = Space.tight)
                                .recordingPrivacyCover()
                        )
                    }
                }
            } else {
                Text(
                    text = "Payment SIM",
                    style = HomeType.rowTitle,
                    color = Ink,
                    modifier = Modifier.padding(top = Space.card)
                )
                Text(
                    text = "Used for Send, Pay, and Repeat.",
                    style = HomeType.caption,
                    color = Mute,
                    modifier = Modifier.padding(top = Space.tight, bottom = Space.gap)
                )
                safaricom.forEach { line ->
                    val selected = selectedId == line.subscriptionId
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selected,
                                onClick = { onSelect(line.subscriptionId) },
                                role = Role.RadioButton
                            )
                            .padding(vertical = Space.gap)
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Accent,
                                unselectedColor = Hairline
                            )
                        )
                        Spacer(modifier = Modifier.width(Space.gap))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = line.label,
                                style = HomeType.body,
                                color = Ink,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.recordingPrivacyCover()
                            )
                            if (selected) {
                                Text(
                                    text = "Used for Send and Pay",
                                    style = HomeType.caption,
                                    color = Accent
                                )
                            }
                        }
                    }
                }
                if (others.isNotEmpty()) {
                    Text(
                        text = others.joinToString { it.label },
                        style = HomeType.caption,
                        color = Mute,
                        modifier = Modifier
                            .padding(bottom = Space.card)
                            .recordingPrivacyCover()
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentAttemptRow(attempt: RepeatAttemptEntity) {
    val who = attempt.counterpartyName ?: attempt.counterpartyPhone ?: "Unknown"
    val amount = attempt.amount?.let { formatKes(it) }
    val detail = listOfNotNull(amount, who, formatActivityTime(attempt.createdAtMillis))
        .joinToString(" · ")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Space.gap)
    ) {
        Text(outcomeLabel(attempt.outcome), style = HomeType.body, color = Ink)
        Text(
            text = detail,
            style = HomeType.caption,
            color = Mute,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.recordingPrivacyCover()
        )
    }
}

internal data class TextSizeChoice(val sp: Int, val label: String)

internal val TEXT_SIZE_CHOICES = listOf(
    TextSizeChoice(10, "Small"),
    TextSizeChoice(12, "Default"),
    TextSizeChoice(14, "Large"),
    TextSizeChoice(18, "XL")
)

internal fun lockMinuteChoices(current: Int): List<Int> {
    val preset = listOf(1, 2, 5, 10, 15, 30)
    return if (current in preset) preset else (preset + current).sorted().distinct()
}

internal fun nearestTextSizeChoice(sp: Int): TextSizeChoice =
    TEXT_SIZE_CHOICES.minBy { abs(it.sp - sp) }

internal fun minutesLabel(minutes: Int): String =
    if (minutes == 1) "1 min" else "$minutes min"

private fun outcomeLabel(outcome: RepeatOutcome): String = when (outcome) {
    RepeatOutcome.COMPLETED_TO_PIN -> "Reached the PIN screen"
    RepeatOutcome.USER_CANCELLED -> "Cancelled"
    RepeatOutcome.AUTH_FAILED -> "Unlock failed"
    RepeatOutcome.ABORTED_MISMATCH -> "Payment screen didn’t match"
    RepeatOutcome.ABORTED_ERROR -> "Couldn’t finish"
    RepeatOutcome.MANUAL_COPY -> "Details copied"
}

private fun openHttps(context: android.content.Context, url: String) {
    context.openHttpsUrl(url)
}

private fun openSupportEmail(context: android.content.Context, email: String) {
    context.openMailto(email, subject = "LipaBill support")
}
