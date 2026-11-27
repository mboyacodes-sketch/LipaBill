package com.lipabill.app.ui.paylink

import com.lipabill.app.ui.AccentWideButton
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lipabill.app.LipaBillApp
import com.lipabill.app.R
import com.lipabill.app.data.DemoLedger
import com.lipabill.app.pay.PayLink
import com.lipabill.app.social.SocialLink
import com.lipabill.app.ussd.PaymentWallet
import com.lipabill.app.ussd.SimLineHelper
import com.lipabill.app.ui.privacy.LocalRecordingPrivacy
import com.lipabill.app.ui.components.ConsumeSnackbarMessage
import com.lipabill.app.ui.components.SpeedDialColumn
import com.lipabill.app.ui.components.SpeedDialItem
import com.lipabill.app.ui.sheet.AmountKeypad
import com.lipabill.app.ui.sheet.EditableSheetField
import com.lipabill.app.ui.sheet.InAppKeyboard
import com.lipabill.app.ui.sheet.rememberSheetEditor
import com.lipabill.app.ui.theme.Accent
import com.lipabill.app.ui.theme.Canvas
import com.lipabill.app.ui.theme.CardWhite
import com.lipabill.app.ui.theme.Debit
import com.lipabill.app.ui.theme.Expense
import com.lipabill.app.ui.theme.HomeType
import com.lipabill.app.ui.theme.Ink
import com.lipabill.app.ui.theme.Mute
import com.lipabill.app.ui.theme.OnAccent
import com.lipabill.app.ui.theme.SoftBlue
import com.lipabill.app.ui.theme.Space
import com.lipabill.app.ui.util.InterceptSystemIme
import com.lipabill.app.ui.util.copyPlainText
import com.lipabill.app.ui.util.hideKeyboardOnOutsideTap
import com.lipabill.app.ui.util.sanitizeDigits
import com.lipabill.app.ui.util.sanitizePayAccount
import com.lipabill.app.ui.util.sanitizePayPhone
import com.lipabill.app.ui.util.sanitizeWholeShillings
import com.lipabill.app.ui.util.sharePlainText
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

private enum class RequestQrMode {
    Payments,
    Socials
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayProfileScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as LipaBillApp
    val prefs = app.securePreferences
    val snackbar = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val activeWallet by app.activeDialWallet.collectAsStateWithLifecycle()
    val requestSource = activeWallet?.ledgerSource ?: prefs.appRegion.primaryWallet
    val walletLabel = activeWallet?.badge ?: requestSource.label
    LaunchedEffect(Unit) {
        prefs.ensureRequestPayDestPerWallet(prefs.appRegion)
    }
    var requestMode by remember {
        mutableStateOf(
            if (prefs.requestQrMode.equals("socials", ignoreCase = true)) {
                RequestQrMode.Socials
            } else {
                RequestQrMode.Payments
            }
        )
    }
    var socialPlatformKey by remember { mutableStateOf(prefs.socialPlatformKey) }
    var collectKind by remember(requestSource) {
        mutableStateOf(prefs.payCollectKind(requestSource).toPayKind())
    }
    var simPhone by remember(requestSource) {
        mutableStateOf(context.walletSimPhone(activeWallet, prefs.preferredSimSubscriptionId))
    }
    var savedPhone by remember(requestSource) {
        mutableStateOf(prefs.payPhone(requestSource).orEmpty())
    }
    var payPhoneHidden by remember(requestSource) {
        mutableStateOf(prefs.payPhoneHidden(requestSource))
    }
    var savedTill by remember(requestSource) {
        mutableStateOf(prefs.payTill(requestSource).orEmpty())
    }
    var savedBill by remember(requestSource) {
        mutableStateOf(prefs.payBillBusiness(requestSource).orEmpty())
    }
    var savedAccount by remember(requestSource) {
        mutableStateOf(prefs.payBillAccount(requestSource).orEmpty())
    }
    // Empty state first; Add / missing kind opens the form.
    var editing by remember { mutableStateOf(false) }
    var collectKindBeforeEdit by remember { mutableStateOf<PayLink.Kind?>(null) }
    var addDialExpanded by remember { mutableStateOf(false) }
    var pendingSocialAdd by remember { mutableStateOf<SocialLink.Platform?>(null) }
    var socialEditing by remember { mutableStateOf(false) }
    var socialRevision by remember { mutableIntStateOf(0) }
    var draftPrimary by remember {
        mutableStateOf(
            when (collectKind) {
                PayLink.Kind.PHONE -> savedPhone
                PayLink.Kind.TILL -> savedTill
                PayLink.Kind.PAYBILL -> savedBill
            }
        )
    }
    var draftAccount by remember { mutableStateOf(savedAccount) }
    val primaryEditor = rememberSheetEditor(draftPrimary)
    val accountEditor = rememberSheetEditor(draftAccount)
    var amountFocused by remember { mutableStateOf(false) }
    var primaryFocused by remember { mutableStateOf(false) }
    var accountFocused by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val focusManager = LocalFocusManager.current

    DisposableEffect(lifecycleOwner, activeWallet) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                simPhone = context.walletSimPhone(activeWallet, prefs.preferredSimSubscriptionId)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Prefer a number the user saved; otherwise the payment SIM.
    val phone = when {
        payPhoneHidden -> ""
        savedPhone.isNotBlank() -> savedPhone
        else -> simPhone.orEmpty()
    }
    // Screen recording: sharp dummy destinations instead of blurring real ones.
    val recordingPrivacy = LocalRecordingPrivacy.current
    val viewPhone = if (recordingPrivacy) DemoLedger.REQUEST_PHONE else phone
    val viewTill = if (recordingPrivacy) DemoLedger.REQUEST_TILL else savedTill
    val viewBill = if (recordingPrivacy) DemoLedger.REQUEST_PAYBILL else savedBill
    val viewAccount = if (recordingPrivacy) DemoLedger.REQUEST_ACCOUNT else savedAccount
    var amountText by remember { mutableStateOf("") }
    var fieldError by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    /** Keep Compose text editors in lockstep with draft state (avoids till leaking into paybill). */
    fun setPaymentDrafts(primary: String, account: String = "") {
        draftPrimary = primary
        draftAccount = account
        primaryEditor.overwrite(primary)
        accountEditor.overwrite(account)
        fieldError = null
    }

    fun draftsForKind(kind: PayLink.Kind, blankIfUnset: Boolean): Pair<String, String> {
        val has = hasDestination(
            kind, simPhone, savedPhone, savedTill, savedBill, savedAccount, payPhoneHidden
        )
        if (blankIfUnset && !has) return "" to ""
        return when (kind) {
            PayLink.Kind.PHONE -> (savedPhone.ifBlank { simPhone.orEmpty() }) to ""
            PayLink.Kind.TILL -> savedTill to ""
            PayLink.Kind.PAYBILL -> savedBill to savedAccount
        }
    }

    LaunchedEffect(requestSource) {
        // Switching the home wallet must not leak another MNO's Request QR.
        collectKind = prefs.payCollectKind(requestSource).toPayKind()
        simPhone = context.walletSimPhone(activeWallet, prefs.preferredSimSubscriptionId)
        savedPhone = prefs.payPhone(requestSource).orEmpty()
        payPhoneHidden = prefs.payPhoneHidden(requestSource)
        savedTill = prefs.payTill(requestSource).orEmpty()
        savedBill = prefs.payBillBusiness(requestSource).orEmpty()
        savedAccount = prefs.payBillAccount(requestSource).orEmpty()
        editing = false
        collectKindBeforeEdit = null
        fieldError = null
        val (primary, account) = draftsForKind(collectKind, blankIfUnset = false)
        setPaymentDrafts(primary, account)
    }

    ConsumeSnackbarMessage(notice, snackbar) { notice = null }
    LaunchedEffect(amountFocused, primaryFocused, accountFocused) {
        if (amountFocused || primaryFocused || accountFocused) {
            delay(64)
            scroll.animateScrollTo(scroll.maxValue)
        }
    }

    var payQrFixed by remember { mutableStateOf(prefs.payQrFixed) }
    var dayTick by remember { mutableIntStateOf(PayLink.dayIndex()) }
    LaunchedEffect(payQrFixed) {
        if (payQrFixed) return@LaunchedEffect
        while (true) {
            dayTick = PayLink.dayIndex()
            delay(60_000)
        }
    }
    val amount = amountText.toLongOrNull()?.takeIf { it > 0L }
    // Demo recording swaps destination digits in the QR only — it must not invent
    // slides for kinds the user never saved (Edit would open on empty fields).
    val ready = !editing &&
        hasDestination(
            collectKind, simPhone, savedPhone, savedTill, savedBill, savedAccount, payPhoneHidden
        )
    /** Pay link for one destination kind, with today's amount and rotation setting. */
    fun encodePayLink(kind: PayLink.Kind) = PayLink.encode(
        kind = kind,
        identifier = when (kind) {
            PayLink.Kind.PHONE -> viewPhone
            PayLink.Kind.TILL -> viewTill
            PayLink.Kind.PAYBILL -> viewBill
        },
        account = viewAccount.takeIf { kind == PayLink.Kind.PAYBILL },
        amountShillings = amount,
        rotateDaily = !payQrFixed
    )
    val link = remember(
        collectKind, viewPhone, viewTill, viewBill, viewAccount, amount, payQrFixed, dayTick, ready
    ) {
        if (!ready) return@remember null
        encodePayLink(collectKind)
    }
    val mark = remember {
        ContextCompat.getDrawable(context, R.mipmap.ic_launcher)
            ?.toBitmap(width = 192, height = 192)
            ?.asImageBitmap()
    }

    /** Social request panel; the same wiring in the wide and narrow layouts. */
    @Composable
    fun Socials(modifier: Modifier = Modifier) {
        SocialRequestPanel(
            prefs = prefs,
            mark = mark,
            onNotice = { notice = it },
            modifier = modifier,
            selectedPlatformKey = socialPlatformKey,
            onPlatformKeyChange = { key ->
                socialPlatformKey = key
                prefs.socialPlatformKey = key
            },
            pendingAddPlatform = pendingSocialAdd,
            onPendingAddConsumed = { pendingSocialAdd = null },
            onEditingChange = { socialEditing = it },
            onHandlesChanged = { socialRevision += 1 }
        )
    }
    val paymentSlides = remember(
        viewPhone, viewTill, viewBill, viewAccount,
        simPhone, savedPhone, payPhoneHidden, savedTill, savedBill, savedAccount,
        amount, payQrFixed, dayTick
    ) {
        PayLink.Kind.entries.mapNotNull { kind ->
            if (!hasDestination(
                    kind, simPhone, savedPhone, savedTill, savedBill, savedAccount, payPhoneHidden
                )
            ) {
                return@mapNotNull null
            }
            val encoded = encodePayLink(kind) ?: return@mapNotNull null
            RequestQrSlide.Payment(kind, encoded)
        }
    }

    fun applyPaymentSlide(slide: RequestQrSlide.Payment) {
        collectKind = slide.kind
        prefs.setPayCollectKind(requestSource, slide.kind.storageKey())
        editing = false
        collectKindBeforeEdit = null
        val (primary, account) = draftsForKind(slide.kind, blankIfUnset = false)
        setPaymentDrafts(primary, account)
    }

    fun cancelPaymentEdit() {
        editing = false
        primaryFocused = false
        accountFocused = false
        amountFocused = false
        fieldError = null
        focusManager.clearFocus(force = true)
        val restore = collectKindBeforeEdit
        collectKindBeforeEdit = null
        val slides = paymentSlides
        when {
            restore != null && slides.any { it.kind == restore } -> {
                applyPaymentSlide(slides.first { it.kind == restore })
            }
            slides.isNotEmpty() -> applyPaymentSlide(slides.first())
            else -> {
                // Stay on empty state with a sensible default kind.
                collectKind = PayLink.Kind.PHONE
                prefs.setPayCollectKind(requestSource, "phone")
                setPaymentDrafts("", "")
            }
        }
    }

    fun startAddPayment(kind: PayLink.Kind? = null) {
        val missing = kind ?: PayLink.Kind.entries.firstOrNull { candidate ->
            !hasDestination(
                candidate, simPhone, savedPhone, savedTill, savedBill, savedAccount, payPhoneHidden
            )
        } ?: PayLink.Kind.PHONE
        if (!editing) collectKindBeforeEdit = collectKind
        collectKind = missing
        // Don't write prefs until save — avoids a blank screen if the user backs out.
        // Always blank for a kind that isn't saved yet so till digits don't stick on paybill.
        val (primary, account) = draftsForKind(missing, blankIfUnset = true)
        setPaymentDrafts(primary, account)
        amountFocused = false
        primaryFocused = false
        accountFocused = false
        addDialExpanded = false
        editing = true
    }

    fun deletePaymentDestination(kind: PayLink.Kind) {
        prefs.clearPayDestination(requestSource, kind)
        when (kind) {
            PayLink.Kind.PHONE -> {
                savedPhone = ""
                payPhoneHidden = true
            }
            PayLink.Kind.TILL -> savedTill = ""
            PayLink.Kind.PAYBILL -> {
                savedBill = ""
                savedAccount = ""
            }
        }
        amountFocused = false
        primaryFocused = false
        accountFocused = false
        editing = false
        focusManager.clearFocus(force = true)
        val remaining = PayLink.Kind.entries.filter { candidate ->
            candidate != kind && hasDestination(
                candidate, simPhone, savedPhone, savedTill, savedBill, savedAccount, payPhoneHidden
            )
        }
        if (remaining.isNotEmpty()) {
            val next = remaining.first()
            collectKind = next
            prefs.setPayCollectKind(requestSource, next.storageKey())
            val (primary, account) = draftsForKind(next, blankIfUnset = false)
            setPaymentDrafts(primary, account)
        } else {
            collectKind = PayLink.Kind.PHONE
            prefs.setPayCollectKind(requestSource, "phone")
            setPaymentDrafts("", "")
        }
    }

    val unsetPaymentKinds = remember(
        simPhone, savedPhone, payPhoneHidden, savedTill, savedBill, savedAccount
    ) {
        PayLink.Kind.entries.filter { kind ->
            !hasDestination(
                kind, simPhone, savedPhone, savedTill, savedBill, savedAccount, payPhoneHidden
            )
        }
    }
    val unsetSocialPlatforms = remember(socialRevision, socialPlatformKey) {
        SocialLink.Platform.entries.filter { platform ->
            prefs.socialHandle(platform.storageKey).isNullOrBlank()
        }
    }

    // Recover if we left add mid-flow with a kind that has no destination saved.
    LaunchedEffect(editing, paymentSlides, collectKind) {
        if (editing) return@LaunchedEffect
        if (paymentSlides.isEmpty()) return@LaunchedEffect
        if (paymentSlides.none { it.kind == collectKind }) {
            applyPaymentSlide(paymentSlides.first())
        }
    }

    BackHandler(enabled = editing) {
        cancelPaymentEdit()
    }

    fun saveDestination() {
        when (collectKind) {
            PayLink.Kind.PHONE -> {
                val saved = PayLink.canonicalPhone(draftPrimary.ifBlank { primaryEditor.text })
                if (saved == null) {
                    fieldError = "Enter a Safaricom or Airtel number."
                    return
                }
                prefs.setPayPhone(requestSource, saved)
                prefs.setPayPhoneHidden(requestSource, false)
                savedPhone = saved
                payPhoneHidden = false
                setPaymentDrafts(saved, "")
            }
            PayLink.Kind.TILL -> {
                val till = PayLink.canonicalMerchantId(draftPrimary.ifBlank { primaryEditor.text })
                if (till == null) {
                    fieldError = "Enter a till number (5+ digits)."
                    return
                }
                prefs.setPayTill(requestSource, till)
                savedTill = till
                setPaymentDrafts(till, "")
            }
            PayLink.Kind.PAYBILL -> {
                val business = PayLink.canonicalMerchantId(
                    draftPrimary.ifBlank { primaryEditor.text }
                )
                val account = PayLink.canonicalAccount(
                    draftAccount.ifBlank { accountEditor.text }
                )
                if (business == null) {
                    fieldError = "Enter a paybill number (5+ digits)."
                    return
                }
                if (account == null) {
                    fieldError = "Enter the account number."
                    return
                }
                prefs.setPayBillBusiness(requestSource, business)
                prefs.setPayBillAccount(requestSource, account)
                savedBill = business
                savedAccount = account
                setPaymentDrafts(business, account)
            }
        }
        prefs.setPayCollectKind(requestSource, collectKind.storageKey())
        collectKindBeforeEdit = null
        editing = false
        primaryFocused = false
        accountFocused = false
        focusManager.clearFocus(force = true)
    }

    Scaffold(
        containerColor = Canvas,
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            when (requestMode) {
                RequestQrMode.Payments -> {
                    if (editing) return@Scaffold
                    if (unsetPaymentKinds.isEmpty() && paymentSlides.isNotEmpty()) return@Scaffold
                    SpeedDialColumn(
                        expanded = addDialExpanded,
                        onToggle = { addDialExpanded = !addDialExpanded },
                        actions = buildList {
                            if (unsetPaymentKinds.isEmpty()) {
                                add(
                                    SpeedDialItem(
                                        label = "Phone",
                                        icon = Icons.Outlined.PhoneAndroid
                                    ) { startAddPayment(PayLink.Kind.PHONE) }
                                )
                            } else {
                                unsetPaymentKinds.forEach { kind ->
                                    add(
                                        SpeedDialItem(
                                            label = when (kind) {
                                                PayLink.Kind.PHONE -> "Phone"
                                                PayLink.Kind.TILL -> "Till"
                                                PayLink.Kind.PAYBILL -> "Paybill"
                                            },
                                            icon = when (kind) {
                                                PayLink.Kind.PHONE -> Icons.Outlined.PhoneAndroid
                                                PayLink.Kind.TILL -> Icons.Outlined.Storefront
                                                PayLink.Kind.PAYBILL -> Icons.Outlined.AccountBalance
                                            }
                                        ) { startAddPayment(kind) }
                                    )
                                }
                            }
                        }
                    )
                }
                RequestQrMode.Socials -> {
                    if (socialEditing) return@Scaffold
                    // Only unset networks — when everything is linked, edit via the row sheet.
                    if (unsetSocialPlatforms.isEmpty()) return@Scaffold
                    SpeedDialColumn(
                        expanded = addDialExpanded,
                        onToggle = { addDialExpanded = !addDialExpanded },
                        actions = unsetSocialPlatforms.map { platform ->
                            SpeedDialItem(
                                label = platform.label,
                                glyph = {
                                    SocialBrandMark(platform = platform, size = 24.dp)
                                }
                            ) {
                                addDialExpanded = false
                                pendingSocialAdd = platform
                            }
                        }
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Canvas)
        ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = 70.dp, y = (-30).dp)
                        .clip(CircleShape)
                        .background(SoftBlue)
                )
                val socialsEmpty = requestMode == RequestQrMode.Socials &&
                    unsetSocialPlatforms.size == SocialLink.Platform.entries.size
                val paymentsQrReady = requestMode == RequestQrMode.Payments &&
                    !editing &&
                    link != null
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .hideKeyboardOnOutsideTap {
                            amountFocused = false
                            primaryFocused = false
                            accountFocused = false
                        }
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(CardWhite)
                                .clickable(onClick = onBack),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "Back",
                                tint = Ink
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Request",
                                style = HomeType.greeting,
                                color = Ink,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "$walletLabel · codes people can scan",
                                style = HomeType.caption,
                                color = Mute,
                                textAlign = TextAlign.Center
                            )
                        }
                        Spacer(modifier = Modifier.size(40.dp))
                    }
                    Spacer(modifier = Modifier.height(Space.block))
                    RequestModeRow(
                        selected = requestMode,
                        onSelect = { mode ->
                            if (mode == requestMode) return@RequestModeRow
                            requestMode = mode
                            prefs.requestQrMode = if (mode == RequestQrMode.Socials) {
                                "socials"
                            } else {
                                "payments"
                            }
                            addDialExpanded = false
                            amountFocused = false
                            primaryFocused = false
                            accountFocused = false
                            focusManager.clearFocus(force = true)
                        }
                    )
                    Spacer(modifier = Modifier.height(Space.section))
                    if (socialsEmpty) {
                        Socials(
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        )
                    } else if (paymentsQrReady) {
                        val payLink = link!!
                        val title = when (collectKind) {
                            PayLink.Kind.PHONE -> "Phone"
                            PayLink.Kind.TILL -> "Till"
                            PayLink.Kind.PAYBILL -> "Paybill"
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = title,
                                        style = HomeType.section.copy(
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = Ink,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = destinationCaption(collectKind, payQrFixed, payLink),
                                        style = HomeType.body,
                                        color = Mute,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                    if (paymentSlides.size > 1) {
                                        Text(
                                            text = "Swipe to see your other payment details",
                                            style = HomeType.caption,
                                            color = Mute,
                                            modifier = Modifier.padding(top = 8.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(20.dp))
                                    if (paymentSlides.size > 1) {
                                        RequestQrPeekCarousel(
                                            slides = paymentSlides,
                                            mark = mark,
                                            selectedId = "pay_${collectKind.name}",
                                            onSelect = { slide ->
                                                val payment = slide as? RequestQrSlide.Payment
                                                    ?: return@RequestQrPeekCarousel
                                                applyPaymentSlide(payment)
                                            },
                                            onCopy = { slide ->
                                                notice = if (context.copyPlainText(
                                                        "LipaBill pay link",
                                                        slide.shareUrl
                                                    )
                                                ) {
                                                    "Link copied"
                                                } else {
                                                    "Couldn’t copy right now"
                                                }
                                            }
                                        )
                                    } else {
                                        RequestQrSlideCard(
                                            slide = RequestQrSlide.Payment(collectKind, payLink),
                                            mark = mark,
                                            onCopy = {
                                                notice = if (context.copyPlainText(
                                                        "LipaBill pay link",
                                                        payLink.shareUrl
                                                    )
                                                ) {
                                                    "Link copied"
                                                } else {
                                                    "Couldn’t copy right now"
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "Tap the code to copy the link",
                                style = HomeType.caption,
                                color = Mute
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Amount", style = HomeType.body, color = Mute)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("KES", style = HomeType.caption, color = Mute)
                                    InterceptSystemIme {
                                        BasicTextField(
                                            value = amountText,
                                            onValueChange = { next ->
                                                amountText = sanitizeWholeShillings(next)
                                            },
                                            modifier = Modifier
                                                .padding(start = 8.dp)
                                                .widthIn(min = 48.dp, max = 110.dp)
                                                .onFocusChanged { amountFocused = it.isFocused },
                                            textStyle = HomeType.section.copy(
                                                color = Ink,
                                                textAlign = TextAlign.End
                                            ),
                                            singleLine = true,
                                            cursorBrush = SolidColor(Accent),
                                            decorationBox = { inner ->
                                                Box(contentAlignment = Alignment.CenterEnd) {
                                                    if (amountText.isEmpty()) {
                                                        Text(
                                                            "Any",
                                                            style = HomeType.section,
                                                            color = Mute,
                                                            textAlign = TextAlign.End
                                                        )
                                                    }
                                                    inner()
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(CardWhite)
                                    .clickable {
                                        val next = !payQrFixed
                                        payQrFixed = next
                                        prefs.payQrFixed = next
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Keep the same code",
                                        style = HomeType.caption.copy(
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = Ink
                                    )
                                    Text(
                                        text = if (payQrFixed) {
                                            "Stays the same every day."
                                        } else {
                                            "Refreshes each day."
                                        },
                                        style = HomeType.caption,
                                        color = Mute
                                    )
                                }
                                Switch(
                                    checked = payQrFixed,
                                    onCheckedChange = { next ->
                                        payQrFixed = next
                                        prefs.payQrFixed = next
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedTrackColor = Accent,
                                        checkedThumbColor = OnAccent
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(20.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                PaymentAction(
                                    label = "Edit",
                                    icon = Icons.Outlined.Edit,
                                    tint = Accent,
                                    onClick = {
                                        collectKindBeforeEdit = collectKind
                                        val (primary, account) = draftsForKind(
                                            collectKind,
                                            blankIfUnset = false
                                        )
                                        setPaymentDrafts(primary, account)
                                        editing = true
                                        amountFocused = false
                                    }
                                )
                                PaymentAction(
                                    label = "Delete",
                                    icon = Icons.Outlined.Delete,
                                    tint = Expense,
                                    onClick = { deletePaymentDestination(collectKind) }
                                )
                                PaymentAction(
                                    label = "Share",
                                    icon = Icons.Outlined.IosShare,
                                    tint = Accent,
                                    onClick = {
                                        if (!context.sharePlainText(
                                                "Share link",
                                                payLink.shareMessage
                                            )
                                        ) {
                                            notice = "Couldn’t share right now"
                                        }
                                    }
                                )
                            }
                        }
                    } else {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(scroll),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                    if (requestMode == RequestQrMode.Socials) {
                        Socials()
                    } else {
                        when {
                            editing -> {
                                val kindTitle = when (collectKind) {
                                    PayLink.Kind.PHONE -> "Phone"
                                    PayLink.Kind.TILL -> "Till"
                                    PayLink.Kind.PAYBILL -> "Paybill"
                                }
                                Text(
                                    text = when {
                                        paymentSlides.any { it.kind == collectKind } ->
                                            "Edit $kindTitle"
                                        else -> "Add $kindTitle"
                                    },
                                    style = HomeType.section.copy(
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = Ink,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = when (collectKind) {
                                        PayLink.Kind.PHONE ->
                                            "Enter the mobile money number that should receive payments."
                                        PayLink.Kind.TILL ->
                                            "Enter the Buy Goods till that should receive payments."
                                        PayLink.Kind.PAYBILL ->
                                            "Enter the paybill and account that should receive payments."
                                    },
                                    style = HomeType.body,
                                    color = Mute,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                                Spacer(modifier = Modifier.height(Space.block))
                                InterceptSystemIme {
                                    EditableSheetField(
                                        editor = primaryEditor,
                                        onText = { next ->
                                            draftPrimary = next
                                            fieldError = null
                                        },
                                        placeholder = when (collectKind) {
                                            PayLink.Kind.PHONE -> "Mobile money number"
                                            PayLink.Kind.TILL -> "Till number"
                                            PayLink.Kind.PAYBILL -> "Paybill number"
                                        },
                                        sanitize = when (collectKind) {
                                            PayLink.Kind.PHONE -> ::sanitizePayPhone
                                            else -> ::sanitizeDigits
                                        },
                                        onFocused = { primaryFocused = it },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                if (collectKind == PayLink.Kind.PAYBILL) {
                                    Spacer(modifier = Modifier.height(Space.gap))
                                    InterceptSystemIme {
                                        EditableSheetField(
                                            editor = accountEditor,
                                            onText = { next ->
                                                draftAccount = next
                                                fieldError = null
                                            },
                                            placeholder = "Account number",
                                            sanitize = ::sanitizePayAccount,
                                            onFocused = { accountFocused = it },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                                Text(
                                    text = fieldError ?: when (collectKind) {
                                        PayLink.Kind.PHONE -> "From your payment SIM, or type it in. Stays on this phone."
                                        PayLink.Kind.TILL -> "Buy Goods till that should receive the money."
                                        PayLink.Kind.PAYBILL -> "Paybill and account that should receive the money."
                                    },
                                    style = HomeType.caption,
                                    color = if (fieldError != null) Debit else Mute,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 4.dp, top = 8.dp)
                                )
                                Spacer(modifier = Modifier.height(Space.block))
                                AccentWideButton(onClick = ::saveDestination) {
                                    Text("Save", style = HomeType.rowTitle)
                                }
                                if (paymentSlides.isNotEmpty()) {
                                    Text(
                                        text = "Cancel",
                                        style = HomeType.rowTitle,
                                        color = Mute,
                                        modifier = Modifier
                                            .padding(top = 8.dp)
                                            .clickable(onClick = ::cancelPaymentEdit)
                                            .padding(vertical = 10.dp)
                                    )
                                }
                            }
                            paymentSlides.isEmpty() -> {
                                RequestEmptyState(
                                    icon = {
                                        Icon(
                                            Icons.Outlined.Payments,
                                            contentDescription = null,
                                            tint = Accent,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    },
                                    title = "Get paid with a QR",
                                    body = "Add a $walletLabel phone, till, or paybill. Each wallet has its own code.",
                                    cta = "Add payment details",
                                    onCta = ::startAddPayment
                                )
                            }
                            else -> {
                                // Safety net: recover instead of rendering a blank page.
                                LaunchedEffect(Unit) {
                                    if (paymentSlides.isNotEmpty()) {
                                        applyPaymentSlide(paymentSlides.first())
                                    } else {
                                        cancelPaymentEdit()
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(Space.section))
                    }
                    }
                }
            }
            if (requestMode == RequestQrMode.Payments && (primaryFocused || accountFocused)) {
                InAppKeyboard(
                    // Account codes are often alphanumeric; business/till/phone stay on digits.
                    startOnDigits = !accountFocused,
                    onChar = { ch ->
                        if (accountFocused) {
                            draftAccount = accountEditor.insert(ch, ::sanitizePayAccount)
                        } else {
                            val sanitize = when (collectKind) {
                                PayLink.Kind.PHONE -> ::sanitizePayPhone
                                else -> ::sanitizeDigits
                            }
                            draftPrimary = primaryEditor.insert(ch, sanitize)
                        }
                        fieldError = null
                    },
                    onBackspace = {
                        if (accountFocused) {
                            draftAccount = accountEditor.backspace(::sanitizePayAccount)
                        } else {
                            val sanitize = when (collectKind) {
                                PayLink.Kind.PHONE -> ::sanitizePayPhone
                                else -> ::sanitizeDigits
                            }
                            draftPrimary = primaryEditor.backspace(sanitize)
                        }
                    },
                    onDone = {
                        primaryFocused = false
                        accountFocused = false
                        focusManager.clearFocus(force = true)
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            } else if (requestMode == RequestQrMode.Payments && amountFocused) {
                AmountKeypad(
                    onKey = { key ->
                        if (key.length == 1 && key[0].isDigit()) {
                            amountText = sanitizeWholeShillings(amountText + key)
                        }
                    },
                    onBackspace = { amountText = amountText.dropLast(1) },
                    onClear = { amountText = "" },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
        if (addDialExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { addDialExpanded = false }
            )
        }
        }
    }

}

@Composable
private fun PaymentAction(
    label: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
        Text(text = label, style = HomeType.rowTitle, color = tint)
    }
}

@Composable
private fun RequestModeRow(
    selected: RequestQrMode,
    onSelect: (RequestQrMode) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RequestQrMode.entries.forEach { mode ->
            RequestChip(
                label = when (mode) {
                    RequestQrMode.Payments -> "Payments"
                    RequestQrMode.Socials -> "Socials"
                },
                selected = mode == selected,
                onClick = { onSelect(mode) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private fun hasDestination(
    kind: PayLink.Kind,
    simPhone: String?,
    phone: String,
    till: String,
    bill: String,
    account: String,
    phoneHidden: Boolean = false
): Boolean = when (kind) {
    PayLink.Kind.PHONE ->
        !phoneHidden && (!simPhone.isNullOrBlank() || phone.isNotBlank())
    PayLink.Kind.TILL -> till.isNotBlank()
    PayLink.Kind.PAYBILL -> bill.isNotBlank() && account.isNotBlank()
}

private fun destinationCaption(
    kind: PayLink.Kind,
    fixed: Boolean,
    link: PayLink.Target
): String {
    val where = when (kind) {
        PayLink.Kind.PHONE -> link.identifier
        PayLink.Kind.TILL -> link.identifier
        PayLink.Kind.PAYBILL -> "${link.identifier} · Acc ${link.account}"
    }
    return if (fixed) {
        where
    } else {
        "$where · refreshes daily"
    }
}

private fun String.toPayKind(): PayLink.Kind = when (lowercase()) {
    "till" -> PayLink.Kind.TILL
    "paybill" -> PayLink.Kind.PAYBILL
    else -> PayLink.Kind.PHONE
}

private fun PayLink.Kind.storageKey(): String = when (this) {
    PayLink.Kind.PHONE -> "phone"
    PayLink.Kind.TILL -> "till"
    PayLink.Kind.PAYBILL -> "paybill"
}

/** SIM number for the Request wallet in focus (not every active MNO). */
private fun Context.walletSimPhone(
    wallet: PaymentWallet?,
    preferredSubscriptionId: Int
): String? {
    val region = wallet?.region
        ?: (applicationContext as? LipaBillApp)?.securePreferences?.appRegion
        ?: com.lipabill.app.data.model.ActiveRegion.current
    val lines = SimLineHelper.listActiveLines(this, region)
    val line = when {
        wallet != null -> lines.firstOrNull { it.payWalletFor(region) == wallet }
        else -> SimLineHelper.payLine(region, lines, preferredSubscriptionId)
    } ?: return null
    return PayLink.canonicalPhone(line.numberHint)
}

