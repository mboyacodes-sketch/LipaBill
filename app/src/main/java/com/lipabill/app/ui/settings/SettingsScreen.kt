package com.lipabill.app.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.lipabill.app.R
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lipabill.app.BuildConfig
import com.lipabill.app.MarketingLinks
import com.lipabill.app.data.local.entity.RepeatAttemptEntity
import com.lipabill.app.ui.permissions.rememberAccessibilityToggleCoach
import com.lipabill.app.ui.privacy.recordingPrivacyCover
import com.lipabill.app.ui.theme.Accent
import com.lipabill.app.ui.theme.Canvas
import com.lipabill.app.ui.theme.CardWhite
import com.lipabill.app.ui.theme.OnAccent
import com.lipabill.app.ui.theme.Expense
import com.lipabill.app.ui.theme.Hairline
import com.lipabill.app.ui.theme.HomeType
import com.lipabill.app.ui.theme.Ink
import com.lipabill.app.ui.theme.Mute
import com.lipabill.app.ui.theme.Space
import com.lipabill.app.ui.util.formatActivityTime
import com.lipabill.app.ui.util.formatKes
import com.lipabill.app.ussd.RepeatOutcome
import com.lipabill.app.ussd.SimLine
import com.lipabill.app.viewmodel.SettingsViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: (() -> Unit)? = null,
    onRequestPhoneStatePermission: () -> Unit = {},
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
    var lockAfter by remember(state.timeoutMinutes) {
        mutableFloatStateOf(state.timeoutMinutes.toFloat())
    }

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
                    "LipaBill can fill M-Pesa screens"
                } else {
                    "LipaBill will no longer fill M-Pesa screens"
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
            SettingsSection(stringResource(R.string.settings_habits)) {
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_challenges_title),
                    subtitle = stringResource(R.string.settings_challenges_body),
                    checked = state.challengesEnabled,
                    onCheckedChange = viewModel::setChallengesEnabled
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_checkin_title),
                    subtitle = stringResource(R.string.settings_checkin_body),
                    checked = state.weeklyCheckInEnabled,
                    onCheckedChange = viewModel::setWeeklyCheckInEnabled
                )
            }

            SettingsSection("Appearance") {
                SettingsSwitchRow(
                    title = "Dark mode",
                    subtitle = "Navy surfaces at night. Passes stay paper white.",
                    checked = state.darkMode,
                    onCheckedChange = viewModel::setDarkMode
                )
            }

            SettingsSection("Lock") {
                SettingsValueRow(
                    title = "Lock after",
                    value = minutesLabel(lockAfter.roundToInt())
                )
                Text(
                    text = "You’ll unlock again after you leave the app.",
                    style = HomeType.caption,
                    color = Mute
                )
                Slider(
                    value = lockAfter,
                    onValueChange = { lockAfter = it },
                    onValueChangeFinished = {
                        viewModel.setTimeoutMinutes(lockAfter.roundToInt())
                    },
                    valueRange = 1f..30f,
                    steps = 28,
                    modifier = Modifier.fillMaxWidth(),
                    colors = settingsSliderColors()
                )
                SettingsDivider()
                SettingsActionRow(
                    title = "Lock now",
                    icon = {
                        Icon(
                            Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = Ink
                        )
                    },
                    onClick = { viewModel.lockNow() }
                )
            }

            SettingsSection("Home") {
                SettingsSwitchRow(
                    title = "Show balance",
                    subtitle = "Stays on screen. Off hides it until you tap.",
                    checked = state.alwaysShowBalance,
                    onCheckedChange = viewModel::setAlwaysShowBalance
                )
                SettingsDivider()
                SettingsSwitchRow(
                    title = "Favourites",
                    subtitle = "Frequent contacts on the home screen.",
                    checked = state.favouritesSectionEnabled,
                    onCheckedChange = viewModel::setFavouritesSectionEnabled
                )
                if (BuildConfig.DEBUG) {
                    SettingsDivider()
                    SettingsSwitchRow(
                        title = "Blur for recording",
                        subtitle = "Debug only. Blurs names, numbers, references, and the PIN pad.",
                        checked = state.recordingPrivacy,
                        onCheckedChange = viewModel::setRecordingPrivacy
                    )
                }
                SettingsDivider()
                SettingsValueRow(
                    title = "Text size",
                    value = textSizeLabel(state.fontSizeSp)
                )
                Slider(
                    value = state.fontSizeSp.toFloat(),
                    onValueChange = { viewModel.setFontSizeSp(it.roundToInt()) },
                    valueRange = 8f..18f,
                    steps = 9,
                    modifier = Modifier.fillMaxWidth(),
                    colors = settingsSliderColors()
                )
            }

            SettingsSection("M-Pesa") {
                Text(
                    text = "SIM",
                    style = HomeType.rowTitle,
                    color = Ink,
                    modifier = Modifier.padding(top = Space.card)
                )
                Text(
                    text = "The line used for M-Pesa.",
                    style = HomeType.caption,
                    color = Mute,
                    modifier = Modifier.padding(top = Space.tight, bottom = Space.gap)
                )
                SimChoices(
                    needsPermission = state.needsPhoneStatePermission,
                    lines = state.simLines,
                    selectedId = state.preferredSimSubscriptionId,
                    onAllow = onRequestPhoneStatePermission,
                    onSelect = viewModel::setPreferredSim
                )
                SettingsDivider()
                SettingsNavRow(
                    title = "Fill M-Pesa screens",
                    subtitle = if (state.lipaBillA11yEnabled) {
                        "On. LipaBill types into the payment menus."
                    } else {
                        "Off. Turn this on to type into payment menus."
                    },
                    action = if (state.lipaBillA11yEnabled) "Turn off" else "Turn on",
                    actionEmphasis = !state.lipaBillA11yEnabled,
                    onClick = { a11yCoach.requestToggle() }
                )
                SettingsDivider()
                SettingsSwitchRow(
                    title = "Automatic repeat",
                    subtitle = "Fills the M-Pesa screens. Off copies the details instead.",
                    checked = state.repeatEnabled,
                    onCheckedChange = viewModel::setRepeatEnabled
                )
            }

            if (attempts.isNotEmpty()) {
                SettingsSection("Recent") {
                    attempts.take(3).forEachIndexed { index, attempt ->
                        if (index > 0) SettingsDivider()
                        RecentAttemptRow(attempt)
                    }
                }
            }

            SettingsSection("About") {
                SettingsNavRow(
                    title = stringResource(R.string.settings_check_update),
                    subtitle = stringResource(R.string.settings_check_update_body),
                    showChevron = true,
                    onClick = onCheckForUpdate
                )
                SettingsDivider()
                SettingsNavRow(
                    title = "Privacy",
                    action = "",
                    showChevron = true,
                    onClick = { openHttps(context, MarketingLinks.PRIVACY) }
                )
                SettingsDivider()
                SettingsNavRow(
                    title = "Support",
                    action = "",
                    showChevron = true,
                    onClick = { openHttps(context, MarketingLinks.SUPPORT) }
                )
                SettingsDivider()
                SettingsNavRow(
                    title = "Email support",
                    action = "",
                    showChevron = true,
                    onClick = { openMailto(context, MarketingLinks.SUPPORT_EMAIL) }
                )
            }

            Text(
                text = "LipaBill ${BuildConfig.VERSION_NAME}",
                style = HomeType.caption,
                color = Mute,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(Space.tight))
            Text(
                text = "Your M-Pesa PIN is typed on LipaBill’s keypad, sent once, and never stored.",
                style = HomeType.caption,
                color = Mute
            )
            Spacer(modifier = Modifier.height(Space.section))
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Text(
        text = title,
        style = HomeType.label,
        color = Mute,
        modifier = Modifier.padding(start = Space.gap)
    )
    Spacer(modifier = Modifier.height(Space.gap))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardWhite)
            .padding(horizontal = Space.card)
    ) {
        content()
    }
    Spacer(modifier = Modifier.height(Space.section))
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(color = Hairline, thickness = 1.dp)
}

@Composable
private fun settingsSliderColors() = SliderDefaults.colors(
    thumbColor = Accent,
    activeTrackColor = Accent,
    inactiveTrackColor = Hairline
)

@Composable
private fun SettingsValueRow(
    title: String,
    value: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Space.card)
    ) {
        Text(title, style = HomeType.rowTitle, color = Ink, modifier = Modifier.weight(1f))
        Text(value, style = HomeType.body, color = Mute)
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
    onClick: () -> Unit,
    icon: @Composable (() -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Space.card)
    ) {
        if (icon != null) {
            icon()
            Spacer(modifier = Modifier.width(Space.card))
        }
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
                title = "Allow SIM access",
                subtitle = "Needed to choose the M-Pesa line.",
                action = "Allow",
                actionEmphasis = true,
                onClick = onAllow
            )
        }
        lines.isEmpty() -> {
            Text(
                text = "No SIM found in this phone.",
                style = HomeType.body,
                color = Mute,
                modifier = Modifier.padding(bottom = Space.card)
            )
        }
        lines.none { it.isSafaricom } -> {
            Text(
                text = "Insert a Safaricom SIM to pay.",
                style = HomeType.body,
                color = Expense,
                modifier = Modifier.padding(bottom = Space.gap)
            )
            lines.forEach { line ->
                Text(
                    text = line.label,
                    style = HomeType.caption,
                    color = Mute,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(bottom = Space.card)
                        .recordingPrivacyCover()
                )
            }
        }
        else -> {
            val safaricom = lines.filter { it.isSafaricom }
            val others = lines.filterNot { it.isSafaricom }
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
                                text = "Used for M-Pesa",
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

@Composable
private fun RecentAttemptRow(attempt: RepeatAttemptEntity) {
    val who = attempt.counterpartyName ?: attempt.counterpartyPhone ?: "Unknown"
    val amount = attempt.amount?.let { formatKes(it) }
    val detail = listOfNotNull(amount, who, formatActivityTime(attempt.createdAtMillis))
        .joinToString(" · ")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Space.card)
    ) {
        Text(outcomeLabel(attempt.outcome), style = HomeType.rowTitle, color = Ink)
        Spacer(modifier = Modifier.height(Space.tight))
        Text(
            text = detail,
            style = HomeType.caption,
            color = Mute,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.recordingPrivacyCover()
        )
    }
}

private fun minutesLabel(minutes: Int): String =
    if (minutes == 1) "1 minute" else "$minutes minutes"

private fun textSizeLabel(sp: Int): String = when {
    sp <= 10 -> "Small"
    sp <= 13 -> "Default"
    sp <= 16 -> "Large"
    else -> "Extra large"
}

private fun outcomeLabel(outcome: RepeatOutcome): String = when (outcome) {
    RepeatOutcome.COMPLETED_TO_PIN -> "Reached the PIN screen"
    RepeatOutcome.USER_CANCELLED -> "Cancelled"
    RepeatOutcome.AUTH_FAILED -> "Unlock failed"
    RepeatOutcome.ABORTED_MISMATCH -> "Payment screen didn’t match"
    RepeatOutcome.ABORTED_ERROR -> "Couldn’t finish"
    RepeatOutcome.MANUAL_COPY -> "Details copied"
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
