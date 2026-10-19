package com.lipabill.app.ui.paylink

import android.Manifest
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lipabill.app.LipaBillApp
import com.lipabill.app.R
import com.lipabill.app.pay.PayLink
import com.lipabill.app.ussd.SimLineHelper
import com.lipabill.app.ui.permissions.PermissionGuideDialog
import com.lipabill.app.ui.permissions.PermissionLesson
import com.lipabill.app.ui.permissions.permissionGranted
import com.lipabill.app.ui.privacy.recordingPrivacyCover
import com.lipabill.app.ui.sheet.AmountKeypad
import com.lipabill.app.ui.sheet.EditableSheetField
import com.lipabill.app.ui.sheet.InAppKeyboard
import com.lipabill.app.ui.sheet.rememberSheetEditor
import com.lipabill.app.ui.theme.Accent
import com.lipabill.app.ui.theme.ActionPay
import com.lipabill.app.ui.theme.BrandNavy
import com.lipabill.app.ui.theme.Canvas
import com.lipabill.app.ui.theme.CardWhite
import com.lipabill.app.ui.theme.Debit
import com.lipabill.app.ui.theme.HomeType
import com.lipabill.app.ui.theme.Ink
import com.lipabill.app.ui.theme.LabelBlue
import com.lipabill.app.ui.theme.Mute
import com.lipabill.app.ui.theme.OnAccent
import com.lipabill.app.ui.theme.SoftBlue
import com.lipabill.app.ui.theme.Space
import com.lipabill.app.ui.tickets.QrScannerScreen
import com.lipabill.app.ui.util.InterceptSystemIme
import com.lipabill.app.ui.util.copyPlainText
import com.lipabill.app.ui.util.hideKeyboardOnOutsideTap
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayProfileScreen(
    onBack: () -> Unit,
    onPay: (PayLink.Target) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as LipaBillApp
    val prefs = app.securePreferences
    val snackbar = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current
    var collectKind by remember {
        mutableStateOf(prefs.payCollectKind.toPayKind())
    }
    var simPhone by remember {
        mutableStateOf(context.mpesaSimPhone(prefs.preferredSimSubscriptionId))
    }
    var savedPhone by remember { mutableStateOf(prefs.payPhone.orEmpty()) }
    var savedTill by remember { mutableStateOf(prefs.payTill.orEmpty()) }
    var savedBill by remember { mutableStateOf(prefs.payBillBusiness.orEmpty()) }
    var savedAccount by remember { mutableStateOf(prefs.payBillAccount.orEmpty()) }
    var editing by remember {
        mutableStateOf(!hasDestination(collectKind, simPhone, savedPhone, savedTill, savedBill, savedAccount))
    }
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
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val next = context.mpesaSimPhone(prefs.preferredSimSubscriptionId)
                simPhone = next
                if (next != null && collectKind == PayLink.Kind.PHONE) editing = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val phone = simPhone ?: savedPhone
    var amountText by remember { mutableStateOf("") }
    var fieldError by remember { mutableStateOf<String?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var showCameraGuide by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(notice) {
        val message = notice ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        notice = null
    }
    LaunchedEffect(amountFocused, primaryFocused, accountFocused) {
        if (amountFocused || primaryFocused || accountFocused) {
            delay(64)
            scroll.animateScrollTo(scroll.maxValue)
        }
    }

    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) scanning = true
    }

    var payQrFixed by remember { mutableStateOf(prefs.payQrFixed) }
    var dayTick by remember { mutableStateOf(PayLink.dayIndex()) }
    LaunchedEffect(payQrFixed) {
        if (payQrFixed) return@LaunchedEffect
        while (true) {
            dayTick = PayLink.dayIndex()
            delay(60_000)
        }
    }
    val amount = amountText.toLongOrNull()?.takeIf { it > 0L }
    val ready = !editing && hasDestination(
        collectKind, simPhone, savedPhone, savedTill, savedBill, savedAccount
    )
    val link = remember(
        collectKind, phone, savedTill, savedBill, savedAccount, amount, payQrFixed, dayTick, ready
    ) {
        if (!ready) return@remember null
        when (collectKind) {
            PayLink.Kind.PHONE -> PayLink.encode(
                kind = PayLink.Kind.PHONE,
                identifier = phone,
                amountShillings = amount,
                rotateDaily = !payQrFixed
            )
            PayLink.Kind.TILL -> PayLink.encode(
                kind = PayLink.Kind.TILL,
                identifier = savedTill,
                amountShillings = amount,
                rotateDaily = !payQrFixed
            )
            PayLink.Kind.PAYBILL -> PayLink.encode(
                kind = PayLink.Kind.PAYBILL,
                identifier = savedBill,
                account = savedAccount,
                amountShillings = amount,
                rotateDaily = !payQrFixed
            )
        }
    }
    val qr = remember(link?.shareUrl) {
        link?.shareUrl?.let { encodePayQr(it) }
    }
    val mark = remember {
        ContextCompat.getDrawable(context, R.mipmap.ic_launcher)
            ?.toBitmap(width = 192, height = 192)
            ?.asImageBitmap()
    }
    if (scanning) {
        QrScannerScreen(
            onResult = { raw ->
                scanning = false
                val target = PayLink.parse(raw)
                if (target == null) {
                    notice = "That code is not a LipaBill pay code."
                } else {
                    onPay(target)
                }
            },
            onCancel = { scanning = false }
        )
        return
    }

    fun selectKind(kind: PayLink.Kind) {
        if (kind == collectKind) return
        collectKind = kind
        prefs.payCollectKind = kind.storageKey()
        fieldError = null
        primaryFocused = false
        accountFocused = false
        amountFocused = false
        focusManager.clearFocus(force = true)
        draftPrimary = when (kind) {
            PayLink.Kind.PHONE -> savedPhone
            PayLink.Kind.TILL -> savedTill
            PayLink.Kind.PAYBILL -> savedBill
        }
        draftAccount = savedAccount
        editing = !hasDestination(kind, simPhone, savedPhone, savedTill, savedBill, savedAccount)
    }

    fun saveDestination() {
        when (collectKind) {
            PayLink.Kind.PHONE -> {
                if (simPhone != null) {
                    editing = false
                    return
                }
                val saved = PayLink.canonicalPhone(draftPrimary)
                if (saved == null) {
                    fieldError = "Enter a Safaricom or Airtel number."
                    return
                }
                prefs.payPhone = saved
                savedPhone = saved
            }
            PayLink.Kind.TILL -> {
                val till = PayLink.canonicalMerchantId(draftPrimary)
                if (till == null) {
                    fieldError = "Enter a till number (5+ digits)."
                    return
                }
                prefs.payTill = till
                savedTill = till
            }
            PayLink.Kind.PAYBILL -> {
                val business = PayLink.canonicalMerchantId(draftPrimary)
                val account = PayLink.canonicalAccount(draftAccount)
                if (business == null) {
                    fieldError = "Enter a paybill number (5+ digits)."
                    return
                }
                if (account == null) {
                    fieldError = "Enter the account / shop code."
                    return
                }
                prefs.payBillBusiness = business
                prefs.payBillAccount = account
                savedBill = business
                savedAccount = account
            }
        }
        editing = false
        primaryFocused = false
        accountFocused = false
        focusManager.clearFocus(force = true)
    }

    Scaffold(
        containerColor = Canvas,
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Canvas)
                .navigationBarsPadding()
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
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .hideKeyboardOnOutsideTap {
                            amountFocused = false
                            primaryFocused = false
                            accountFocused = false
                        }
                        .verticalScroll(scroll)
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
                        Text(
                            text = "Request",
                            modifier = Modifier.weight(1f),
                            style = HomeType.greeting,
                            color = Ink,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.size(40.dp))
                    }
                    Spacer(modifier = Modifier.height(Space.block))
                    CollectKindRow(selected = collectKind, onSelect = ::selectKind)
                    Spacer(modifier = Modifier.height(Space.block))
                    if (editing) {
                        Text(
                            text = when (collectKind) {
                                PayLink.Kind.PHONE ->
                                    "Add the number for your M-Pesa SIM. It stays off this screen."
                                PayLink.Kind.TILL ->
                                    "Buy Goods till that should receive the payment."
                                PayLink.Kind.PAYBILL ->
                                    "Paybill and account that should receive the payment."
                            },
                            style = HomeType.body,
                            color = Mute,
                            textAlign = TextAlign.Center
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
                                    PayLink.Kind.PHONE -> "M-Pesa number"
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
                                    placeholder = "Account / shop code",
                                    sanitize = ::sanitizePayAccount,
                                    onFocused = { accountFocused = it },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        Text(
                            text = fieldError ?: when (collectKind) {
                                PayLink.Kind.PHONE -> "Used only to build the code."
                                PayLink.Kind.TILL -> "Someone who scans pays this till."
                                PayLink.Kind.PAYBILL -> "Someone who scans pays this paybill."
                            },
                            style = HomeType.caption,
                            color = if (fieldError != null) Debit else Mute,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, top = 6.dp)
                        )
                        Spacer(modifier = Modifier.height(Space.gap))
                        Button(
                            onClick = ::saveDestination,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Accent,
                                contentColor = OnAccent
                            )
                        ) {
                            Text("Continue", style = HomeType.rowTitle)
                        }
                    } else if (link != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(ActionPay),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Outlined.QrCode2,
                                contentDescription = null,
                                tint = BrandNavy,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(18.dp))
                        Text(
                            text = "Scan the QR code",
                            style = HomeType.section.copy(
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = Ink,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = destinationCaption(collectKind, payQrFixed, link),
                            style = HomeType.body,
                            color = Mute,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                        Spacer(modifier = Modifier.height(28.dp))
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            RequestDot(Modifier.align(Alignment.CenterStart).offset(y = (-70).dp))
                            RequestDot(Modifier.align(Alignment.CenterStart).offset(y = 54.dp))
                            RequestDot(Modifier.align(Alignment.CenterEnd).offset(y = (-46).dp))
                            RequestDot(Modifier.align(Alignment.CenterEnd).offset(y = 78.dp))
                            if (qr != null) {
                                Box(
                                    modifier = Modifier
                                        .size(248.dp)
                                        .clip(RoundedCornerShape(22.dp))
                                        .background(Color.White)
                                        .recordingPrivacyCover()
                                        .clickable {
                                            context.copyPlainText(
                                                "LipaBill pay link",
                                                link.shareUrl
                                            )
                                            notice = "Link copied"
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        bitmap = qr,
                                        contentDescription = "Pay code",
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(18.dp),
                                        contentScale = ContentScale.Fit
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(Color.White)
                                            .padding(4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (mark != null) {
                                            Image(
                                                bitmap = mark,
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(RoundedCornerShape(12.dp))
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(18.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
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
                                    style = HomeType.rowTitle,
                                    color = Ink
                                )
                                Text(
                                    text = if (payQrFixed) {
                                        "This QR stays until you change the destination or amount."
                                    } else {
                                        "Off by default. A new QR appears each day."
                                    },
                                    style = HomeType.caption,
                                    color = Mute,
                                    modifier = Modifier.padding(top = 2.dp)
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
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(SoftBlue)
                                .clickable {
                                    context.copyPlainText("LipaBill pay link", link.shareUrl)
                                    notice = "Link copied"
                                }
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(ActionPay),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Outlined.Info,
                                    contentDescription = null,
                                    tint = BrandNavy,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Text(
                                text = "They confirm, then enter their M-Pesa PIN. Tap here to copy the link.",
                                style = HomeType.caption,
                                color = Ink,
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
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
                                            amountText = next.filter { it.isDigit() }.take(6)
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
                        Spacer(modifier = Modifier.height(28.dp))
                        Button(
                            onClick = {
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, shareText(link))
                                }
                                context.startActivity(Intent.createChooser(send, "Share pay link"))
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(28.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Accent,
                                contentColor = OnAccent
                            )
                        ) {
                            Text("Share link", style = HomeType.rowTitle)
                            Spacer(modifier = Modifier.size(8.dp))
                            Icon(
                                Icons.Outlined.IosShare,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = "Scan a code",
                            style = HomeType.rowTitle,
                            color = Accent,
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .clickable {
                                    if (context.permissionGranted(Manifest.permission.CAMERA)) {
                                        scanning = true
                                    } else {
                                        showCameraGuide = true
                                    }
                                }
                                .padding(vertical = 8.dp)
                        )
                        if (collectKind != PayLink.Kind.PHONE || simPhone == null) {
                            Text(
                                text = "Change destination",
                                style = HomeType.label,
                                color = Mute,
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .clickable {
                                        draftPrimary = when (collectKind) {
                                            PayLink.Kind.PHONE -> phone
                                            PayLink.Kind.TILL -> savedTill
                                            PayLink.Kind.PAYBILL -> savedBill
                                        }
                                        draftAccount = savedAccount
                                        editing = true
                                        amountFocused = false
                                    }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(Space.section))
                }
            }
            if (primaryFocused || accountFocused) {
                InAppKeyboard(
                    startOnDigits = true,
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
            } else if (amountFocused) {
                AmountKeypad(
                    onKey = { key ->
                        if (key.length == 1 && key[0].isDigit()) {
                            amountText = (amountText + key).filter { it.isDigit() }.take(6)
                        }
                    },
                    onBackspace = { amountText = amountText.dropLast(1) },
                    onClear = { amountText = "" },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }

    if (showCameraGuide) {
        PermissionGuideDialog(
            lesson = PermissionLesson.Camera,
            onAllow = {
                showCameraGuide = false
                cameraPermission.launch(Manifest.permission.CAMERA)
            },
            onNotNow = { showCameraGuide = false }
        )
    }
}

@Composable
private fun CollectKindRow(
    selected: PayLink.Kind,
    onSelect: (PayLink.Kind) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PayLink.Kind.entries.forEach { kind ->
            val on = kind == selected
            Text(
                text = when (kind) {
                    PayLink.Kind.PHONE -> "Phone"
                    PayLink.Kind.TILL -> "Till"
                    PayLink.Kind.PAYBILL -> "Paybill"
                },
                style = HomeType.caption.copy(fontWeight = FontWeight.SemiBold),
                color = if (on) OnAccent else Ink,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (on) Accent else CardWhite)
                    .border(
                        width = 1.dp,
                        color = if (on) Accent else SoftBlue,
                        shape = RoundedCornerShape(14.dp)
                    )
                    .clickable { onSelect(kind) }
                    .padding(vertical = 12.dp)
            )
        }
    }
}

@Composable
private fun RequestDot(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(LabelBlue)
    )
}

private fun hasDestination(
    kind: PayLink.Kind,
    simPhone: String?,
    phone: String,
    till: String,
    bill: String,
    account: String
): Boolean = when (kind) {
    PayLink.Kind.PHONE -> !simPhone.isNullOrBlank() || phone.isNotBlank()
    PayLink.Kind.TILL -> till.isNotBlank()
    PayLink.Kind.PAYBILL -> bill.isNotBlank() && account.isNotBlank()
}

private fun destinationCaption(
    kind: PayLink.Kind,
    fixed: Boolean,
    link: PayLink.Target
): String {
    val where = when (kind) {
        PayLink.Kind.PHONE -> "Send Money"
        PayLink.Kind.TILL -> "Till ${link.identifier}"
        PayLink.Kind.PAYBILL -> "Paybill ${link.identifier} · Acc ${link.account}"
    }
    return if (fixed) {
        "$where"
    } else {
        "$where. Code refreshes every day."
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

private fun Context.mpesaSimPhone(preferredSubscriptionId: Int): String? {
    val lines = SimLineHelper.listActiveLines(this)
    val line = SimLineHelper.payLine(lines, preferredSubscriptionId) ?: return null
    return PayLink.canonicalPhone(line.numberHint)
}

private fun sanitizePayPhone(raw: String): String =
    raw.filter { it.isDigit() || it == '+' || it == ' ' }.take(16)

private fun sanitizeDigits(raw: String): String = raw.filter { it.isDigit() }.take(12)

private fun sanitizePayAccount(raw: String): String =
    raw.filter { it.isLetterOrDigit() }.take(12)

private fun shareText(link: PayLink.Target): String {
    val lead = when (link.kind) {
        PayLink.Kind.PHONE -> if (link.amountShillings != null) {
            "Pay KES ${link.amountShillings} with LipaBill"
        } else {
            "Pay me with LipaBill"
        }
        PayLink.Kind.TILL -> if (link.amountShillings != null) {
            "Pay KES ${link.amountShillings} to till ${link.identifier}"
        } else {
            "Pay till ${link.identifier} with LipaBill"
        }
        PayLink.Kind.PAYBILL -> if (link.amountShillings != null) {
            "Pay KES ${link.amountShillings} to paybill ${link.identifier}"
        } else {
            "Pay paybill ${link.identifier} with LipaBill"
        }
    }
    return "$lead\n${link.shareUrl}"
}
