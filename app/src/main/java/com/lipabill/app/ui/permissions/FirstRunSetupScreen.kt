package com.lipabill.app.ui.permissions

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lipabill.app.data.model.AppRegion
import com.lipabill.app.data.model.MoneySource
import com.lipabill.app.data.model.defaultLedgerSources
import com.lipabill.app.data.model.detectAppRegion
import com.lipabill.app.data.model.groupedProviderCatalog
import com.lipabill.app.data.model.lockedLedgerSources
import com.lipabill.app.data.model.providerCatalog
import com.lipabill.app.data.model.simWalletNumberHints
import com.lipabill.app.data.model.toggleLedgerSource
import com.lipabill.app.ui.ProviderPicker
import com.lipabill.app.ui.privacy.RecordingCapture
import com.lipabill.app.ui.theme.Accent
import com.lipabill.app.ui.theme.Canvas
import com.lipabill.app.ui.theme.Hairline
import com.lipabill.app.ui.theme.Ink
import com.lipabill.app.ui.theme.Mute
import com.lipabill.app.ui.theme.OnAccent
import com.lipabill.app.ui.theme.SoftBlue
import com.lipabill.app.ui.theme.Space
import com.lipabill.app.ussd.LineGatedSources
import com.lipabill.app.ussd.SimLineHelper
import kotlinx.coroutines.launch

/** Pager turn for first-run / consent — about half the default spring settle. */
private const val PAGE_TURN_MS = 180

/**
 * Consent wizard. Welcome (three-slide intro) → Phone (Send/Pay) →
 * Providers → SMS → (Accessibility). Region is taken from the SIM / phone
 * number — no country picker. Phone before Providers so wallet chips match
 * SIMs. Later visits skip Welcome; Camera / Notifications / Hibernation open
 * as one page when needed.
 */
@Composable
fun FirstRunSetupScreen(
    onFinished: () -> Unit,
    smsGranted: Boolean,
    permanentlyDenied: Boolean,
    showRestrictedSettingsHelp: Boolean,
    onRequestSms: () -> Unit,
    onOpenSmsSettings: () -> Unit,
    phoneGranted: Boolean,
    simGranted: Boolean,
    onRequestPhone: () -> Unit,
    accessibilityEnabled: Boolean,
    onAgreeAccessibility: () -> Unit,
    cameraGranted: Boolean = false,
    notificationsGranted: Boolean = false,
    onRequestCamera: () -> Unit = {},
    onRequestNotifications: () -> Unit = {},
    onSkipNotifications: () -> Unit = {},
    onSkipHibernation: () -> Unit = {},
    /** Called when unused-app restrictions are already Disabled (auto-advance). */
    onHibernationCleared: () -> Unit = {},
    onOpenHibernationSettings: () -> Unit = {},
    onSkipOemSurvival: () -> Unit = {},
    onOpenOemSurvivalSettings: () -> Unit = {},
    startPage: FirstRunPage = FirstRunPage.Welcome,
    firstRun: Boolean = true,
    accessibilityNote: String? = null,
    region: AppRegion = AppRegion.DEFAULT,
    onRegionSelected: (AppRegion) -> Unit = {},
    onProvidersSelected: (Set<MoneySource>) -> Unit = {},
    /** Debug / sideload: jump straight to Home and remember the Settings toggle. */
    onDebugSkipToApp: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val regionGuess = remember { context.detectAppRegion() }
    // Primary + on-phone MNOs + common banks when SIM access is known.
    var selectedProviders by remember(region) {
        mutableStateOf(defaultLedgerSources(region))
    }
    val simLines = remember(region, simGranted) {
        if (simGranted) SimLineHelper.listActiveLines(context, region) else emptyList()
    }
    val lockedProviders = remember(region, simGranted, simLines) {
        lockedLedgerSources(region, simLines, phoneAccess = simGranted)
    }
    val providerNumberHints = remember(region, simLines) {
        simWalletNumberHints(region, simLines)
    }
    LaunchedEffect(region, simGranted) {
        val onPhone = if (simGranted) {
            LineGatedSources.onPhone(region, SimLineHelper.listActiveLines(context, region))
        } else {
            null
        }
        selectedProviders = defaultLedgerSources(region, onPhoneWallets = onPhone)
    }
    // Country comes from SIM / network / locale — no wizard picker.
    LaunchedEffect(firstRun, regionGuess) {
        if (!firstRun) return@LaunchedEffect
        val guessed = regionGuess?.region ?: return@LaunchedEffect
        if (guessed != region) onRegionSelected(guessed)
    }
    val pages = if (firstRun) {
        FirstRunWizard.firstRunPages(region)
    } else {
        FirstRunWizard.laterPages(startPage)
    }
    val startIndex = pages.indexOf(startPage).let { if (it >= 0) it else 0 }
    val pager = rememberPagerState(initialPage = startIndex, pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    fun goTo(index: Int) {
        // ~half the default pager spring settle — snappier first-run / Maestro taps.
        scope.launch {
            pager.animateScrollToPage(
                page = index.coerceIn(0, pages.lastIndex),
                animationSpec = tween(durationMillis = PAGE_TURN_MS)
            )
        }
    }
    fun commitProviders() {
        if (firstRun) onProvidersSelected(selectedProviders)
    }
    fun next() {
        if (pages[pager.currentPage] == FirstRunPage.Providers) commitProviders()
        if (pager.currentPage < pages.lastIndex) goTo(pager.currentPage + 1)
        else onFinished()
    }
    if (!firstRun) {
        BackHandler(onBack = onFinished)
    }
    LaunchedEffect(smsGranted, pager.currentPage) {
        if (smsGranted && pages[pager.currentPage] == FirstRunPage.Sms) next()
    }
    LaunchedEffect(phoneGranted, simGranted, pager.currentPage) {
        if (phoneGranted && simGranted && pages[pager.currentPage] == FirstRunPage.Phone) {
            next()
        }
    }
    LaunchedEffect(accessibilityEnabled, pager.currentPage) {
        // Same auto-advance as SMS/Phone — return from Settings with the toggle on
        // should leave the Agree loop without another tap.
        if (accessibilityEnabled && pages[pager.currentPage] == FirstRunPage.Accessibility) {
            next()
        }
    }
    LaunchedEffect(cameraGranted, pager.currentPage) {
        if (cameraGranted && pages[pager.currentPage] == FirstRunPage.Camera) next()
    }
    LaunchedEffect(notificationsGranted, pager.currentPage) {
        if (notificationsGranted && pages[pager.currentPage] == FirstRunPage.Notifications) next()
    }

    // After opening battery / hibernation settings, advance only when the user returns and is clear.
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumeTick by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(resumeTick, pager.currentPage) {
        if (resumeTick == 0) return@LaunchedEffect
        when (pages.getOrNull(pager.currentPage)) {
            FirstRunPage.Hibernation -> {
                val status = unusedAppRestrictionOf(readUnusedAppRestrictions(context))
                if (status == UnusedAppRestriction.Disabled) {
                    onHibernationCleared()
                    next()
                }
            }
            FirstRunPage.OemSurvival -> {
                if (AccessibilityHealthProbe.isIgnoringBatteryOptimizations(context)) {
                    onSkipOemSurvival()
                    next()
                }
            }
            else -> Unit
        }
    }

    val page = pages[pager.currentPage]
    val intro = rememberPagerState(pageCount = { FirstRunWizard.INTRO_SLIDES.size })
    val firstSetupIndex = pages.indexOfFirst { it != FirstRunPage.Welcome }.coerceAtLeast(0)
    fun back() {
        if (pager.currentPage > 0) goTo(pager.currentPage - 1) else if (!firstRun) onFinished()
    }
    if (firstRun) {
        BackHandler(enabled = pager.currentPage > 0 || intro.currentPage > 0) {
            if (page == FirstRunPage.Welcome) {
                scope.launch { intro.animateScrollToPage(intro.currentPage - 1) }
            } else {
                back()
            }
        }
    }
    val chrome = disclosureChrome()
    val onIntro = page == FirstRunPage.Welcome
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Canvas)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(top = chrome.top, bottom = chrome.bottom)
    ) {
        // Intro brings its own Skip, dots and CTA; setup pages share this chrome.
        if (!onIntro) {
            WizardTopBar(
                progress = FirstRunWizard.stepProgress(pages, page),
                showBack = pager.currentPage > 0 || !firstRun,
                onBack = ::back,
                side = chrome.side
            )
        }
        if (!onIntro && firstRun && RecordingCapture.available && onDebugSkipToApp != null) {
            TextButton(
                onClick = onDebugSkipToApp,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(end = (chrome.side - 8.dp).coerceAtLeast(4.dp))
            ) {
                Text("Skip to app", color = Mute)
            }
        }
        HorizontalPager(
            state = pager,
            userScrollEnabled = false,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { index ->
            if (pages[index] == FirstRunPage.Welcome) {
                FirstRunIntro(
                    pager = intro,
                    region = region,
                    onContinue = {
                        if (intro.currentPage < intro.pageCount - 1) {
                            scope.launch {
                                intro.animateScrollToPage(
                                    intro.currentPage + 1,
                                    animationSpec = tween(durationMillis = PAGE_TURN_MS)
                                )
                            }
                        } else {
                            goTo(firstSetupIndex)
                        }
                    },
                    onSkip = { onDebugSkipToApp?.invoke() ?: goTo(firstSetupIndex) },
                    skipLabel = if (onDebugSkipToApp != null) "Skip to app" else "Skip"
                )
            } else {
                WizardPageBody(
                    page = pages[index],
                    region = region,
                    selectedProviders = selectedProviders,
                    lockedProviders = lockedProviders,
                    providerNumberHints = providerNumberHints,
                    onToggleProvider = { source ->
                        if (source !in lockedProviders) {
                            selectedProviders = toggleLedgerSource(
                                selectedProviders,
                                source,
                                region.sources
                            )
                        }
                    },
                    smsGranted = smsGranted,
                    permanentlyDenied = permanentlyDenied,
                    showRestrictedSettingsHelp = showRestrictedSettingsHelp,
                    phoneReady = phoneGranted && simGranted,
                    accessibilityEnabled = accessibilityEnabled,
                    accessibilityNote = accessibilityNote,
                    cameraGranted = cameraGranted,
                    notificationsGranted = notificationsGranted,
                    side = chrome.side
                )
            }
        }
        if (!onIntro) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = chrome.side)
                    .padding(top = Space.block)
            ) {
                WizardActions(
                    page = page,
                    providersPicked = selectedProviders.isNotEmpty(),
                    smsGranted = smsGranted,
                    permanentlyDenied = permanentlyDenied,
                    showRestrictedSettingsHelp = showRestrictedSettingsHelp,
                    phoneReady = phoneGranted && simGranted,
                    accessibilityEnabled = accessibilityEnabled,
                    onRequestSms = onRequestSms,
                    onOpenSmsSettings = onOpenSmsSettings,
                    onRequestPhone = onRequestPhone,
                    onAgreeAccessibility = onAgreeAccessibility,
                    cameraGranted = cameraGranted,
                    notificationsGranted = notificationsGranted,
                    onRequestCamera = onRequestCamera,
                    onRequestNotifications = onRequestNotifications,
                    onSkipNotifications = {
                        onSkipNotifications()
                        next()
                    },
                    onSkipHibernation = {
                        onSkipHibernation()
                        next()
                    },
                    onOpenHibernationSettings = onOpenHibernationSettings,
                    onSkipOemSurvival = {
                        onSkipOemSurvival()
                        next()
                    },
                    onOpenOemSurvivalSettings = onOpenOemSurvivalSettings,
                    onNext = { next() },
                    onFinished = onFinished,
                    firstRun = firstRun,
                    isLastPage = pager.currentPage == pages.lastIndex
                )
                StepFooter(
                    step = FirstRunWizard.stepLabel(pages, page),
                    onSkip = when (page) {
                        FirstRunPage.Notifications -> ({
                            onSkipNotifications()
                            next()
                        })
                        FirstRunPage.Hibernation -> ({
                            onSkipHibernation()
                            next()
                        })
                        FirstRunPage.OemSurvival -> ({
                            onSkipOemSurvival()
                            next()
                        })
                        else -> ({ next() })
                    }
                )
            }
        }
    }
}

/** Back arrow over a thin progress track, as on every setup page. */
@Composable
private fun WizardTopBar(
    progress: Float,
    showBack: Boolean,
    onBack: () -> Unit,
    side: Dp
) {
    val fill by animateFloatAsState(progress.coerceIn(0f, 1f), tween(PAGE_TURN_MS * 2), label = "progress")
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .padding(horizontal = (side - 12.dp).coerceAtLeast(4.dp))
                .height(48.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (showBack) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Ink
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .padding(horizontal = side)
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape)
                .background(Hairline)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fill)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(Accent)
            )
        }
    }
}

@Composable
private fun questionTitle() = MaterialTheme.typography.displayLarge.let {
    it.copy(
        fontSize = it.fontSize * 0.9f,
        lineHeight = it.fontSize * 0.9f * 1.18f,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun WizardPageBody(
    page: FirstRunPage,
    region: AppRegion = AppRegion.DEFAULT,
    selectedProviders: Set<MoneySource> = emptySet(),
    lockedProviders: Map<MoneySource, String> = emptyMap(),
    providerNumberHints: Map<MoneySource, String> = emptyMap(),
    onToggleProvider: (MoneySource) -> Unit = {},
    smsGranted: Boolean,
    permanentlyDenied: Boolean,
    showRestrictedSettingsHelp: Boolean,
    phoneReady: Boolean,
    accessibilityEnabled: Boolean,
    accessibilityNote: String? = null,
    cameraGranted: Boolean = false,
    notificationsGranted: Boolean = false,
    side: Dp = 24.dp
) {
    val (title, body) = when (page) {
        FirstRunPage.Welcome ->
            FirstRunWizard.WELCOME_TITLE to FirstRunWizard.WELCOME_BODY
        FirstRunPage.Providers ->
            FirstRunWizard.PROVIDERS_TITLE to FirstRunWizard.PROVIDERS_BODY
        FirstRunPage.Sms -> FirstRunWizard.SMS_TITLE to FirstRunWizard.smsBody(showRestrictedSettingsHelp)
        FirstRunPage.Phone -> FirstRunWizard.PHONE_TITLE to FirstRunWizard.PHONE_BODY
        FirstRunPage.Accessibility ->
            FirstRunWizard.ACCESSIBILITY_TITLE to FirstRunWizard.ACCESSIBILITY_BODY
        FirstRunPage.Camera -> FirstRunWizard.CAMERA_TITLE to FirstRunWizard.CAMERA_BODY
        FirstRunPage.Notifications ->
            FirstRunWizard.NOTIFICATIONS_TITLE to FirstRunWizard.NOTIFICATIONS_BODY
        FirstRunPage.Hibernation ->
            FirstRunWizard.HIBERNATION_TITLE to FirstRunWizard.HIBERNATION_BODY
        FirstRunPage.OemSurvival ->
            FirstRunWizard.OEM_SURVIVAL_TITLE to FirstRunWizard.OEM_SURVIVAL_BODY
    }
    // Granted / state lines sit in the info note under the content.
    val note: String? = when (page) {
        FirstRunPage.Providers ->
            lockedProviders.values.distinct().joinToString("\n").ifBlank { null }
        FirstRunPage.Sms -> if (smsGranted) {
            "SMS access is on. Your money history can now load on this phone."
        } else {
            null
        }
        FirstRunPage.Phone -> if (phoneReady) {
            "Phone access is on. Send and Pay can dial the payments you confirm."
        } else {
            null
        }
        FirstRunPage.Accessibility -> if (accessibilityEnabled) {
            "“${AccessibilityServiceDisclosure.SERVICE_LABEL}” is on."
        } else {
            null
        }
        FirstRunPage.Camera -> if (cameraGranted) {
            "Camera is allowed. Ticket and pay codes can be scanned."
        } else {
            null
        }
        FirstRunPage.Notifications -> if (notificationsGranted) {
            "Alerts are allowed on this phone."
        } else {
            null
        }
        else -> null
    }
    // Providers stay one viewport; other pages may scroll for long copy.
    val fitOnePage = page == FirstRunPage.Providers
    val topGap = if (fitOnePage) Space.block else Space.section
    val titleGap = if (fitOnePage) Space.block else Space.block * 2
    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (fitOnePage) Modifier
                else Modifier.verticalScroll(rememberScrollState())
            )
            .padding(horizontal = side)
            .padding(top = disclosureChrome().copy + topGap)
    ) {
        Text(
            text = FirstRunWizard.eyebrow(page).uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp),
            color = Mute
        )
        Spacer(modifier = Modifier.height(titleGap))
        Text(text = title, style = questionTitle(), color = Ink)
        Spacer(modifier = Modifier.height(titleGap))
        if (page == FirstRunPage.Accessibility) {
            AccessibilityDisclosurePoints(bodyPrefix = accessibilityNote)
        } else {
            Text(
                text = body,
                style = if (fitOnePage) {
                    MaterialTheme.typography.bodyMedium
                } else {
                    MaterialTheme.typography.bodyLarge
                },
                color = Mute
            )
        }
        if (page == FirstRunPage.Providers) {
            Spacer(modifier = Modifier.height(Space.section))
            ProviderPicker(
                catalog = providerCatalog(region),
                groups = groupedProviderCatalog(region),
                selected = selectedProviders,
                onToggle = onToggleProvider,
                locked = lockedProviders,
                numberHints = providerNumberHints,
                modifier = Modifier
                    .weight(1f, fill = true)
                    .fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(Space.block))
            Text(
                text = "Pick up to 5, or skip for now.",
                style = MaterialTheme.typography.labelMedium,
                color = Mute
            )
        }
        if (page == FirstRunPage.Sms && showRestrictedSettingsHelp && !smsGranted) {
            Spacer(modifier = Modifier.height(Space.section * 2))
            RestrictedSettingsSteps()
        }
        if (page == FirstRunPage.Sms && permanentlyDenied && !showRestrictedSettingsHelp && !smsGranted) {
            Spacer(modifier = Modifier.height(Space.section * 2))
            InfoNote(
                text = "Permission was denied. Enable SMS in App info → Permissions.",
                color = MaterialTheme.colorScheme.error
            )
        }
        if (note != null) {
            Spacer(modifier = Modifier.height(if (fitOnePage) Space.block else Space.section * 2))
            InfoNote(text = note)
        }
        if (!fitOnePage) {
            Spacer(modifier = Modifier.height(Space.section))
        }
    }
}

/** Grey note with an info glyph — status, hints, and why we ask. */
@Composable
private fun InfoNote(text: String, color: Color = Mute) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SoftBlue.copy(alpha = 0.55f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            Icons.Outlined.Info,
            contentDescription = null,
            tint = color,
            modifier = Modifier
                .padding(top = 1.dp)
                .size(16.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

/** Brand pill CTA — Accent on OnAccent, matching Request / Habits actions. */
@Composable
internal fun WizardPrimaryButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Accent,
            contentColor = OnAccent,
            disabledContainerColor = Accent.copy(alpha = 0.35f),
            disabledContentColor = OnAccent
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun WizardSecondaryButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = CircleShape,
        border = BorderStroke(1.dp, Hairline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

/** "02 / 05 · Skip for now" under the CTA. */
@Composable
private fun StepFooter(step: String, onSkip: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Space.block),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "$step  ·", style = MaterialTheme.typography.labelMedium, color = Mute)
        Text(
            text = "Skip for now",
            style = MaterialTheme.typography.labelMedium,
            color = Ink,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onSkip)
                .padding(horizontal = 6.dp, vertical = Space.gap * 2)
        )
    }
}

@Composable
private fun WizardActions(
    page: FirstRunPage,
    providersPicked: Boolean = true,
    smsGranted: Boolean,
    permanentlyDenied: Boolean,
    showRestrictedSettingsHelp: Boolean,
    phoneReady: Boolean,
    accessibilityEnabled: Boolean,
    onRequestSms: () -> Unit,
    onOpenSmsSettings: () -> Unit,
    onRequestPhone: () -> Unit,
    onAgreeAccessibility: () -> Unit,
    cameraGranted: Boolean,
    notificationsGranted: Boolean,
    onRequestCamera: () -> Unit,
    onRequestNotifications: () -> Unit,
    onSkipNotifications: () -> Unit,
    onSkipHibernation: () -> Unit,
    onOpenHibernationSettings: () -> Unit,
    onSkipOemSurvival: () -> Unit = {},
    onOpenOemSurvivalSettings: () -> Unit = {},
    onNext: () -> Unit,
    onFinished: () -> Unit,
    firstRun: Boolean = true,
    isLastPage: Boolean = false
) {
    val doneLabel = if (isLastPage) {
        if (firstRun) "Get started" else "Done"
    } else {
        "Next"
    }
    val gap = Space.block * 2
    Column(modifier = Modifier.fillMaxWidth()) {
        when (page) {
            FirstRunPage.Welcome -> WizardPrimaryButton("Next", onNext)
            FirstRunPage.Providers ->
                WizardPrimaryButton("Use these", onNext, enabled = providersPicked)
            FirstRunPage.Sms -> {
                if (smsGranted) {
                    WizardPrimaryButton("Next", onNext)
                } else if (showRestrictedSettingsHelp) {
                    WizardPrimaryButton("Open App info", onOpenSmsSettings)
                    Spacer(modifier = Modifier.height(gap))
                    WizardSecondaryButton("I've unlocked — Allow SMS", onRequestSms)
                } else if (permanentlyDenied) {
                    WizardPrimaryButton("Open system Settings", onOpenSmsSettings)
                } else {
                    WizardPrimaryButton("Allow SMS access", onRequestSms)
                }
                if (!smsGranted) {
                    Spacer(modifier = Modifier.height(gap))
                    WizardSecondaryButton("Continue without SMS", onNext)
                }
            }
            FirstRunPage.Phone -> {
                if (phoneReady) {
                    WizardPrimaryButton("Next", onNext)
                } else {
                    WizardPrimaryButton("Allow phone", onRequestPhone)
                    Spacer(modifier = Modifier.height(gap))
                    WizardSecondaryButton("Not now", onNext)
                }
            }
            FirstRunPage.Accessibility -> {
                val leave = if (isLastPage) onFinished else onNext
                if (!accessibilityEnabled) {
                    WizardPrimaryButton(AccessibilityServiceDisclosure.AGREE, onAgreeAccessibility)
                    Spacer(modifier = Modifier.height(gap))
                    WizardSecondaryButton(AccessibilityServiceDisclosure.CONTINUE_WITHOUT, leave)
                } else {
                    WizardPrimaryButton(doneLabel, leave)
                }
            }
            FirstRunPage.Camera -> OptionalAccessActions(
                granted = cameraGranted,
                allowLabel = PermissionLesson.Camera.allowLabel,
                doneLabel = doneLabel,
                onAllow = onRequestCamera,
                onSkip = onNext,
                onDone = if (isLastPage) onFinished else onNext
            )
            FirstRunPage.Notifications -> OptionalAccessActions(
                granted = notificationsGranted,
                allowLabel = PermissionLesson.Notifications.allowLabel,
                doneLabel = doneLabel,
                onAllow = onRequestNotifications,
                onSkip = onSkipNotifications,
                onDone = if (isLastPage) onFinished else onNext
            )
            FirstRunPage.Hibernation -> {
                // Open settings only — stay on this page until return (auto-advance) or Not now.
                WizardPrimaryButton(PermissionLesson.Hibernation.allowLabel, onOpenHibernationSettings)
                Spacer(modifier = Modifier.height(gap))
                WizardSecondaryButton("Not now", onSkipHibernation)
            }
            FirstRunPage.OemSurvival -> {
                WizardPrimaryButton(PermissionLesson.OemSurvival.allowLabel, onOpenOemSurvivalSettings)
                Spacer(modifier = Modifier.height(gap))
                WizardSecondaryButton("Not now", onSkipOemSurvival)
            }
        }
    }
}

@Composable
private fun OptionalAccessActions(
    granted: Boolean,
    allowLabel: String,
    doneLabel: String,
    onAllow: () -> Unit,
    onSkip: () -> Unit,
    onDone: () -> Unit
) {
    if (granted) {
        WizardPrimaryButton(doneLabel, onDone)
    } else {
        WizardPrimaryButton(allowLabel, onAllow)
        Spacer(modifier = Modifier.height(Space.block * 2))
        WizardSecondaryButton("Not now", onSkip)
    }
}

@Composable
private fun AccessibilityDisclosurePoints(bodyPrefix: String? = null) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (!bodyPrefix.isNullOrBlank()) {
            Text(
                text = bodyPrefix.trim(),
                style = MaterialTheme.typography.bodyLarge,
                color = Ink,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(Space.block * 2))
        }
        AccessibilityServiceDisclosure.STANDARD_POINTS.forEach { point ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 7.dp)
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(Ink)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = point,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Mute
                )
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
        verticalArrangement = Arrangement.spacedBy(Space.block * 2)
    ) {
        steps.forEachIndexed { index, step ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(SoftBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Ink
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = step,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Ink,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
