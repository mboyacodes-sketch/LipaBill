package com.lipabill.app.ui.permissions

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoGraph
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lipabill.app.R
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lipabill.app.data.model.AppRegion
import com.lipabill.app.data.model.MoneySource
import com.lipabill.app.data.model.RegionGuess
import com.lipabill.app.data.model.detectAppRegion
import com.lipabill.app.data.model.hint
import com.lipabill.app.data.model.groupedProviderCatalog
import com.lipabill.app.data.model.providerCatalog
import com.lipabill.app.data.model.toggleLedgerSource
import com.lipabill.app.ui.ProviderPicker
import com.lipabill.app.ui.RegionPicker
import com.lipabill.app.ui.components.LipaBillMark
import com.lipabill.app.ui.theme.Accent
import com.lipabill.app.ui.theme.Canvas
import com.lipabill.app.ui.theme.Hairline
import com.lipabill.app.ui.theme.Ink
import com.lipabill.app.ui.theme.Mute
import com.lipabill.app.ui.theme.SoftBlue
import com.lipabill.app.ui.theme.Space
import kotlinx.coroutines.launch

/**
 * Consent wizard. Welcome → Region → Providers → SMS → (Kenya: Phone → Accessibility).
 * Later visits skip Welcome: payment access is still a cluster; Camera,
 * Notifications, and Hibernation open as one page when needed.
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
    onOpenHibernationSettings: () -> Unit = {},
    startPage: FirstRunPage = FirstRunPage.Welcome,
    firstRun: Boolean = true,
    accessibilityNote: String? = null,
    region: AppRegion = AppRegion.DEFAULT,
    onRegionSelected: (AppRegion) -> Unit = {},
    onProvidersSelected: (Set<MoneySource>) -> Unit = {}
) {
    val context = LocalContext.current
    val regionGuess = remember { context.detectAppRegion() }
    var userChoseRegion by remember { mutableStateOf(false) }
    var selectedProviders by remember(region) {
        mutableStateOf(setOf(region.primaryWallet))
    }
    LaunchedEffect(firstRun, regionGuess) {
        if (!firstRun || userChoseRegion) return@LaunchedEffect
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
        scope.launch { pager.animateScrollToPage(index.coerceIn(0, pages.lastIndex)) }
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
    LaunchedEffect(cameraGranted, pager.currentPage) {
        if (cameraGranted && pages[pager.currentPage] == FirstRunPage.Camera) next()
    }
    LaunchedEffect(notificationsGranted, pager.currentPage) {
        if (notificationsGranted && pages[pager.currentPage] == FirstRunPage.Notifications) next()
    }

    val page = pages[pager.currentPage]
    val chrome = disclosureChrome()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Canvas)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(top = chrome.top, bottom = chrome.bottom)
    ) {
        Box(
            modifier = Modifier
                .weight(if (page == FirstRunPage.Welcome) 0.72f else 1f)
                .fillMaxWidth()
        ) {
            if (page == FirstRunPage.Welcome) {
                LipaBillMark(
                    size = 96.dp,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                Illustration(
                    art = wizardArt(page),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = chrome.side, vertical = chrome.art)
                )
            }
            TextButton(
                onClick = {
                    if (page == FirstRunPage.Welcome) {
                        goTo(pages.indexOf(FirstRunPage.Region).coerceAtLeast(0))
                    } else {
                        next()
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = (chrome.side - 16.dp).coerceAtLeast(8.dp))
            ) {
                Text("Skip", color = Mute)
            }
        }
        Column(
            modifier = Modifier
                .weight(if (page == FirstRunPage.Welcome) 1.35f else 1f)
                .fillMaxWidth()
                .padding(horizontal = chrome.side)
        ) {
            HorizontalPager(
                state = pager,
                userScrollEnabled = false,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { index ->
                WizardPageBody(
                    page = pages[index],
                    region = region,
                    regionGuess = regionGuess,
                    onRegionSelected = { picked ->
                        userChoseRegion = true
                        onRegionSelected(picked)
                    },
                    selectedProviders = selectedProviders,
                    onToggleProvider = { source ->
                        selectedProviders = toggleLedgerSource(
                            selectedProviders,
                            source,
                            region.sources
                        )
                    },
                    smsGranted = smsGranted,
                    permanentlyDenied = permanentlyDenied,
                    showRestrictedSettingsHelp = showRestrictedSettingsHelp,
                    phoneReady = phoneGranted && simGranted,
                    accessibilityEnabled = accessibilityEnabled,
                    accessibilityNote = accessibilityNote,
                    cameraGranted = cameraGranted,
                    notificationsGranted = notificationsGranted
                )
            }
            PageDots(
                count = pages.size,
                selected = pager.currentPage,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = Space.block)
            )
            WizardActions(
                page = page,
                region = region,
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
                onPrevious = { goTo(pager.currentPage - 1) },
                onNext = { next() },
                onFinished = onFinished,
                firstRun = firstRun,
                canGoPrevious = pager.currentPage > 0,
                isLastPage = pager.currentPage == pages.lastIndex
            )
        }
    }
}

private fun wizardArt(page: FirstRunPage): Int = when (page) {
    FirstRunPage.Welcome,
    FirstRunPage.Region,
    FirstRunPage.Providers,
    FirstRunPage.Camera -> R.drawable.ill_wizard_welcome
    FirstRunPage.Sms, FirstRunPage.Notifications -> R.drawable.ill_wizard_sms
    FirstRunPage.Phone, FirstRunPage.Hibernation ->
        R.drawable.ill_wizard_phone
    FirstRunPage.Accessibility -> R.drawable.ill_wizard_accessibility
}

@Composable
private fun WizardPageBody(
    page: FirstRunPage,
    region: AppRegion = AppRegion.DEFAULT,
    regionGuess: RegionGuess? = null,
    onRegionSelected: (AppRegion) -> Unit = {},
    selectedProviders: Set<MoneySource> = emptySet(),
    onToggleProvider: (MoneySource) -> Unit = {},
    smsGranted: Boolean,
    permanentlyDenied: Boolean,
    showRestrictedSettingsHelp: Boolean,
    phoneReady: Boolean,
    accessibilityEnabled: Boolean,
    accessibilityNote: String? = null,
    cameraGranted: Boolean = false,
    notificationsGranted: Boolean = false
) {
    val (title, body) = when (page) {
        FirstRunPage.Welcome ->
            FirstRunWizard.WELCOME_TITLE to FirstRunWizard.WELCOME_BODY
        FirstRunPage.Region ->
            FirstRunWizard.REGION_TITLE to FirstRunWizard.REGION_BODY
        FirstRunPage.Providers ->
            FirstRunWizard.PROVIDERS_TITLE to FirstRunWizard.PROVIDERS_BODY
        FirstRunPage.Sms -> FirstRunWizard.SMS_TITLE to if (smsGranted) {
            "SMS is allowed. Where it went can now fill on this phone."
        } else {
            FirstRunWizard.smsBody(showRestrictedSettingsHelp)
        }
        FirstRunPage.Phone -> FirstRunWizard.PHONE_TITLE to if (phoneReady) {
            "Phone and SIM are allowed. Send and Pay can place the call you confirm."
        } else {
            FirstRunWizard.PHONE_BODY
        }
        FirstRunPage.Accessibility ->
            FirstRunWizard.ACCESSIBILITY_TITLE to FirstRunWizard.ACCESSIBILITY_BODY
        FirstRunPage.Camera -> FirstRunWizard.CAMERA_TITLE to if (cameraGranted) {
            "Camera is allowed. Ticket and pay codes can be scanned."
        } else {
            FirstRunWizard.CAMERA_BODY
        }
        FirstRunPage.Notifications -> FirstRunWizard.NOTIFICATIONS_TITLE to if (notificationsGranted) {
            "Alerts are allowed on this phone."
        } else {
            FirstRunWizard.NOTIFICATIONS_BODY
        }
        FirstRunPage.Hibernation ->
            FirstRunWizard.HIBERNATION_TITLE to FirstRunWizard.HIBERNATION_BODY
    }
    val accessibilityPage = page == FirstRunPage.Accessibility
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = disclosureChrome().copy),
        horizontalAlignment = if (accessibilityPage) Alignment.Start else Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = Ink,
            textAlign = if (accessibilityPage) TextAlign.Start else TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Spacer(modifier = Modifier.height(Space.block))
        if (accessibilityPage) {
            AccessibilityDisclosurePoints(
                bodyPrefix = accessibilityNote,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        } else {
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = Mute,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
        if (page == FirstRunPage.Welcome) {
            Spacer(modifier = Modifier.height(Space.section))
            WelcomeBeats()
        }
        if (page == FirstRunPage.Region) {
            Spacer(modifier = Modifier.height(Space.section))
            RegionPicker(
                selected = region,
                onSelect = onRegionSelected
            )
            if (regionGuess?.region == region) {
                Spacer(modifier = Modifier.height(Space.block))
                Text(
                    text = regionGuess.hint(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Mute,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }
        if (page == FirstRunPage.Providers) {
            Spacer(modifier = Modifier.height(Space.section))
            ProviderPicker(
                catalog = providerCatalog(region),
                groups = groupedProviderCatalog(region),
                selected = selectedProviders,
                onToggle = onToggleProvider
            )
        }
        if (page == FirstRunPage.Sms && showRestrictedSettingsHelp && !smsGranted) {
            Spacer(modifier = Modifier.height(Space.section))
            RestrictedSettingsSteps()
        }
        if (page == FirstRunPage.Sms && permanentlyDenied && !showRestrictedSettingsHelp && !smsGranted) {
            Spacer(modifier = Modifier.height(Space.section))
            Text(
                text = "Permission was denied. Enable SMS in App info → Permissions.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }
        if (page == FirstRunPage.Accessibility && accessibilityEnabled) {
            Spacer(modifier = Modifier.height(Space.block))
            Text(
                text = "“${AccessibilityServiceDisclosure.SERVICE_LABEL}” is on.",
                style = MaterialTheme.typography.bodyMedium,
                color = Accent,
                textAlign = TextAlign.Center
            )
        }
        Spacer(modifier = Modifier.height(Space.section))
    }
}

private data class WelcomeBeat(
    val n: String,
    val title: String,
    val body: String,
    val icon: ImageVector
)

@Composable
private fun WelcomeBeats() {
    val beats = listOf(
        WelcomeBeat(
            n = "01",
            title = "See the story",
            body = "Where it went, from the texts you already get.",
            icon = Icons.Outlined.AutoGraph
        ),
        WelcomeBeat(
            n = "02",
            title = "Then move",
            body = "Send and pay in Kenya. Show a pass anywhere.",
            icon = Icons.Outlined.NorthEast
        ),
        WelcomeBeat(
            n = "03",
            title = "What it became",
            body = "The trip, the night out, a QR so someone can pay you.",
            icon = Icons.Outlined.ConfirmationNumber
        )
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Space.block)
    ) {
        beats.forEachIndexed { index, beat ->
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Hairline)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(SoftBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = beat.icon,
                        contentDescription = null,
                        tint = Accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${beat.n}  ${beat.title}",
                        style = MaterialTheme.typography.titleSmall,
                        color = Ink
                    )
                    Text(
                        text = beat.body,
                        style = MaterialTheme.typography.bodySmall,
                        color = Mute
                    )
                }
            }
        }
    }
}

@Composable
private fun Illustration(art: Int, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(art),
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Fit
    )
}

@Composable
private fun PageDots(count: Int, selected: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(count) { i ->
            val on = i == selected
            val color by animateColorAsState(if (on) Accent else Hairline, label = "dot")
            Box(
                modifier = Modifier
                    .height(8.dp)
                    .width(if (on) 18.dp else 8.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(color)
            )
        }
    }
}

@Composable
private fun WizardActions(
    page: FirstRunPage,
    region: AppRegion = AppRegion.DEFAULT,
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
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onFinished: () -> Unit,
    firstRun: Boolean = true,
    canGoPrevious: Boolean = true,
    isLastPage: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
    ) {
        when (page) {
            FirstRunPage.Welcome -> {
                Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
                    Text("Next")
                }
            }
            FirstRunPage.Region -> {
                Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
                    Text("Continue with ${region.label}")
                }
            }
            FirstRunPage.Providers -> {
                Button(
                    onClick = onNext,
                    enabled = providersPicked,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Keep these on the picture")
                }
            }
            FirstRunPage.Sms -> {
                if (smsGranted) {
                    Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
                        Text("Next")
                    }
                } else if (showRestrictedSettingsHelp) {
                    Button(onClick = onOpenSmsSettings, modifier = Modifier.fillMaxWidth()) {
                        Text("Open App info")
                    }
                    Spacer(modifier = Modifier.height(Space.gap))
                    OutlinedButton(onClick = onRequestSms, modifier = Modifier.fillMaxWidth()) {
                        Text("I've unlocked — Allow SMS")
                    }
                } else if (permanentlyDenied) {
                    Button(onClick = onOpenSmsSettings, modifier = Modifier.fillMaxWidth()) {
                        Text("Open system Settings")
                    }
                } else {
                    Button(onClick = onRequestSms, modifier = Modifier.fillMaxWidth()) {
                        Text("Allow SMS access")
                    }
                }
                if (!smsGranted) {
                    Spacer(modifier = Modifier.height(Space.gap))
                    OutlinedButton(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
                        Text("Continue without SMS")
                    }
                }
            }
            FirstRunPage.Phone -> {
                if (phoneReady) {
                    Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
                        Text("Next")
                    }
                } else {
                    Button(onClick = onRequestPhone, modifier = Modifier.fillMaxWidth()) {
                        Text("Allow phone")
                    }
                    Spacer(modifier = Modifier.height(Space.gap))
                    OutlinedButton(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
                        Text("Not now")
                    }
                }
            }
            FirstRunPage.Accessibility -> {
                if (!accessibilityEnabled) {
                    Button(onClick = onAgreeAccessibility, modifier = Modifier.fillMaxWidth()) {
                        Text(AccessibilityServiceDisclosure.AGREE)
                    }
                    Spacer(modifier = Modifier.height(Space.gap))
                    OutlinedButton(
                        onClick = if (isLastPage) onFinished else onNext,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(AccessibilityServiceDisclosure.CONTINUE_WITHOUT)
                    }
                } else {
                    Button(
                        onClick = if (isLastPage) onFinished else onNext,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (isLastPage) {
                                if (firstRun) "Get started" else "Done"
                            } else {
                                "Next"
                            }
                        )
                    }
                }
            }
            FirstRunPage.Camera -> OptionalAccessActions(
                granted = cameraGranted,
                allowLabel = PermissionLesson.Camera.allowLabel,
                isLastPage = isLastPage,
                firstRun = firstRun,
                onAllow = onRequestCamera,
                onSkip = onNext,
                onNext = onNext,
                onFinished = onFinished
            )
            FirstRunPage.Notifications -> OptionalAccessActions(
                granted = notificationsGranted,
                allowLabel = PermissionLesson.Notifications.allowLabel,
                isLastPage = isLastPage,
                firstRun = firstRun,
                onAllow = onRequestNotifications,
                onSkip = onSkipNotifications,
                onNext = onNext,
                onFinished = onFinished
            )
            FirstRunPage.Hibernation -> {
                Button(
                    onClick = {
                        onOpenHibernationSettings()
                        onSkipHibernation()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(PermissionLesson.Hibernation.allowLabel)
                }
                Spacer(modifier = Modifier.height(Space.gap))
                OutlinedButton(
                    onClick = onSkipHibernation,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Not now")
                }
            }
        }
        if (page != FirstRunPage.Welcome) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Space.block, start = 4.dp, end = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (canGoPrevious) {
                    Text(
                        text = "Previous",
                        style = MaterialTheme.typography.labelLarge,
                        color = Mute,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onPrevious)
                            .padding(horizontal = Space.block, vertical = Space.gap)
                    )
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }
                if (!isLastPage && page != FirstRunPage.Hibernation) {
                    Text(
                        text = "Next",
                        style = MaterialTheme.typography.labelLarge,
                        color = Accent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onNext)
                            .padding(horizontal = Space.block, vertical = Space.gap)
                    )
                }
            }
        }
    }
}

@Composable
private fun OptionalAccessActions(
    granted: Boolean,
    allowLabel: String,
    isLastPage: Boolean,
    firstRun: Boolean,
    onAllow: () -> Unit,
    onSkip: () -> Unit,
    onNext: () -> Unit,
    onFinished: () -> Unit
) {
    if (granted) {
        Button(
            onClick = if (isLastPage) onFinished else onNext,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (isLastPage) {
                    if (firstRun) "Get started" else "Done"
                } else {
                    "Next"
                }
            )
        }
    } else {
        Button(onClick = onAllow, modifier = Modifier.fillMaxWidth()) {
            Text(allowLabel)
        }
        Spacer(modifier = Modifier.height(Space.gap))
        OutlinedButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
            Text("Not now")
        }
    }
}

@Composable
private fun AccessibilityDisclosurePoints(
    bodyPrefix: String? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (!bodyPrefix.isNullOrBlank()) {
            Text(
                text = bodyPrefix.trim(),
                style = MaterialTheme.typography.bodyMedium,
                color = Ink,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(Space.block))
        }
        AccessibilityServiceDisclosure.STANDARD_POINTS.forEach { point ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
            ) {
                Text(
                    text = "•",
                    color = Accent,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(16.dp)
                )
                Text(
                    text = point,
                    style = MaterialTheme.typography.bodyMedium,
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
