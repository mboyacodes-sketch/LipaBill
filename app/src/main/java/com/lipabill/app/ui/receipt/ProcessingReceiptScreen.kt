package com.lipabill.app.ui.receipt

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lipabill.app.LipaBillApp
import com.lipabill.app.data.model.MpesaTransaction
import com.lipabill.app.data.model.TransactionType
import com.lipabill.app.data.parser.extractAccount
import com.lipabill.app.data.parser.isPhoneLikeName
import com.lipabill.app.ui.privacy.recordingPrivacyCover
import com.lipabill.app.ui.theme.Accent
import com.lipabill.app.ui.permissions.PermissionGuideDialog
import com.lipabill.app.ui.permissions.PermissionLesson
import com.lipabill.app.ui.permissions.PermissionPromptMemory
import com.lipabill.app.ui.permissions.shouldOfferPostNotifications
import com.lipabill.app.ui.theme.Canvas
import com.lipabill.app.ui.theme.CardWhite
import com.lipabill.app.ui.theme.OnAccent
import com.lipabill.app.ui.theme.Expense
import com.lipabill.app.ui.theme.Hairline
import com.lipabill.app.ui.theme.Income
import com.lipabill.app.ui.theme.Ink
import com.lipabill.app.ui.theme.Mute
import com.lipabill.app.ui.theme.Space
import com.lipabill.app.ui.util.displayLabel
import com.lipabill.app.ui.util.formatKes
import com.lipabill.app.ui.util.formatTimestamp
import com.lipabill.app.ussd.PendingPaymentMatcher
import com.lipabill.app.ussd.PendingPaymentReceipt
import com.lipabill.app.ussd.RepeatOutcome
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private val DetailBorder: Color
    @androidx.compose.runtime.Composable
    @androidx.compose.runtime.ReadOnlyComposable
    get() = Hairline

private val LabelGrey: Color
    @androidx.compose.runtime.Composable
    @androidx.compose.runtime.ReadOnlyComposable
    get() = Mute
private val TagProcessingBg = Color(0xFFFFF4E5)
private val TagProcessingFg = Color(0xFFB45309)
private val TagDoneBg: Color
    @androidx.compose.runtime.Composable
    @androidx.compose.runtime.ReadOnlyComposable
    get() = Income.copy(alpha = 0.14f)

private val TagDoneFg: Color
    @androidx.compose.runtime.Composable
    @androidx.compose.runtime.ReadOnlyComposable
    get() = Income
private val TagWarnBg = Color(0xFFFEE2E2)
private val TagWarnFg = Color(0xFFB91C1C)
private val HaloGrey = Color(0xFF9CA3AF)
private val HaloMist = Color(0xFFD1D5DB)

/** After the PIN is sent, leave if the confirmation SMS has not arrived. */
private const val SMS_ARRIVAL_WAIT_MS = 10_000L

/** Confirmed receipt stays up long enough to read, then returns home. */
private const val CONFIRMED_HOLD_MS = 2_500L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProcessingReceiptScreen(
    auditId: Long,
    onDone: () -> Unit,
    onBack: () -> Unit
) {
    val app = LocalContext.current.applicationContext as LipaBillApp
    val context = LocalContext.current
    var askNotifications by remember { mutableStateOf(false) }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) PermissionPromptMemory.notificationsDeclined = true
    }
    LaunchedEffect(Unit) {
        askNotifications = context.shouldOfferPostNotifications()
    }
    val snapshot = remember(auditId) { PendingPaymentReceipt.peek(auditId) }
    val attempt by app.repeatRepository.observeById(auditId)
        .collectAsStateWithLifecycle(initialValue = null)
    var matchedTx by remember { mutableStateOf<MpesaTransaction?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current

    val recent by app.repository.observeTransactions(limit = 40)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    DisposableEffect(lifecycleOwner, auditId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                app.smsInboxSyncWatcher.syncQuietNow()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val amount = matchedTx?.amount ?: snapshot?.amount ?: attempt?.amount
    val smsName = matchedTx?.counterpartyName?.trim()?.takeIf { it.isNotBlank() }
    val dialName = snapshot?.counterpartyName ?: attempt?.counterpartyName
    // Prefer M-Pesa SMS name when the dial was a bare number / unknown contact.
    val name = when {
        smsName != null && !isPhoneLikeName(smsName) -> smsName
        smsName != null && (dialName == null || isPhoneLikeName(dialName)) -> smsName
        else -> dialName ?: smsName
    }
    val phone = matchedTx?.counterpartyPhone?.takeIf { it.isNotBlank() }
        ?: snapshot?.counterpartyPhone
        ?: attempt?.counterpartyPhone
    val type = snapshot?.type ?: matchedTx?.type ?: TransactionType.UNKNOWN
    val account = snapshot?.accountHint
        ?: matchedTx?.rawBody?.let { extractAccount(it) }
    val startedAt = snapshot?.startedAtMillis ?: attempt?.createdAtMillis
        ?: System.currentTimeMillis()

    LaunchedEffect(snapshot, recent, matchedTx) {
        if (matchedTx != null || snapshot == null) return@LaunchedEffect
        matchedTx = PendingPaymentMatcher.findMatch(snapshot, recent)
    }

    LaunchedEffect(matchedTx, attempt?.outcome) {
        if (matchedTx != null) return@LaunchedEffect
        val aborted = when (attempt?.outcome) {
            RepeatOutcome.ABORTED_MISMATCH,
            RepeatOutcome.ABORTED_ERROR,
            RepeatOutcome.USER_CANCELLED ->
                attempt?.detail != "pending"
            else -> false
        }
        if (aborted) return@LaunchedEffect
        while (isActive) {
            app.smsInboxSyncWatcher.syncQuietNow()
            delay(2_500L)
        }
    }

    val tag: ReceiptTag = when {
        matchedTx != null -> ReceiptTag.DONE
        attempt?.outcome == RepeatOutcome.ABORTED_MISMATCH ||
            attempt?.outcome == RepeatOutcome.ABORTED_ERROR -> ReceiptTag.INTERRUPTED
        attempt?.outcome == RepeatOutcome.USER_CANCELLED &&
            attempt?.detail != "pending" -> ReceiptTag.CANCELLED
        else -> ReceiptTag.PROCESSING
    }

    LaunchedEffect(matchedTx?.id, attempt?.outcome) {
        when {
            matchedTx != null -> delay(CONFIRMED_HOLD_MS)
            attempt?.outcome == RepeatOutcome.COMPLETED_TO_PIN -> delay(SMS_ARRIVAL_WAIT_MS)
            else -> return@LaunchedEffect
        }
        PendingPaymentReceipt.clear()
        onDone()
    }

    val headerTitle = when (tag) {
        ReceiptTag.DONE -> "Confirmed"
        ReceiptTag.INTERRUPTED -> "Interrupted"
        ReceiptTag.CANCELLED -> "Cancelled"
        ReceiptTag.PROCESSING -> "Payment"
    }
    val headerSubtitle = when (tag) {
        ReceiptTag.DONE ->
            "M-Pesa confirmation received"
        ReceiptTag.INTERRUPTED ->
            "Automation stopped — check M-Pesa on your phone"
        ReceiptTag.CANCELLED ->
            "This payment was cancelled"
        ReceiptTag.PROCESSING -> when (attempt?.outcome) {
            RepeatOutcome.COMPLETED_TO_PIN ->
                "PIN submitted — waiting for confirmation SMS"
            else ->
                "Enter your PIN; updates when SMS arrives"
        }
    }

    val partyLabel = when (type) {
        TransactionType.PAYBILL -> "Paybill"
        TransactionType.BUY_GOODS -> "Till"
        TransactionType.POCHI -> "Pochi"
        else -> "Phone"
    }
    val confirmCode = matchedTx?.code?.takeIf { !it.startsWith("MANUAL") }
    val toValue = name?.takeIf { it.isNotBlank() }
        ?: phone?.takeIf { it.isNotBlank() }
        ?: "—"
    // Avoid repeating the same string under To and Phone.
    val idValue = phone?.takeIf { it.isNotBlank() && it != name }

    Scaffold(
        containerColor = HaloMist.copy(alpha = 0.55f),
        topBar = {
            TopAppBar(
                title = { Text("Payment", color = Ink) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Ink
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Ink,
                    navigationIconContentColor = Ink
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            // Edge-to-edge grey halo (full phone, including under the top bar).
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                HaloMist.copy(alpha = 0.98f),
                                HaloGrey.copy(alpha = 0.45f),
                                HaloGrey.copy(alpha = 0.28f),
                                HaloMist.copy(alpha = 0.70f)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Space.page, vertical = Space.gap),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Floating receipt at 80% — halo fills the screen behind it.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .scale(0.8f)
                        .shadow(
                            elevation = 20.dp,
                            shape = RoundedCornerShape(22.dp),
                            clip = false,
                            ambientColor = HaloGrey.copy(alpha = 0.28f),
                            spotColor = HaloGrey.copy(alpha = 0.22f)
                        )
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .clip(RoundedCornerShape(22.dp))
                        .background(CardWhite)
                ) {
                    PaymentCongratsHeader(
                        tag = tag,
                        title = headerTitle,
                        subtitle = headerSubtitle
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    PaymentDetailsBox(
                        amount = formatKes(amount ?: matchedTx?.amount),
                        typeLabel = type.displayLabel(),
                        toValue = toValue,
                        partyLabel = partyLabel,
                        idValue = idValue,
                        account = account,
                        startedLabel = formatTimestamp(startedAt)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    PaymentTicketPerforation()
                    PaymentStub(
                        tag = tag,
                        confirmCode = confirmCode,
                        waitingOnSms = tag == ReceiptTag.PROCESSING &&
                            attempt?.outcome == RepeatOutcome.COMPLETED_TO_PIN
                    )
                }

                Spacer(modifier = Modifier.height(Space.block))
                Button(
                    onClick = {
                        PendingPaymentReceipt.clear()
                        onDone()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Accent,
                        contentColor = OnAccent
                    )
                ) {
                    Text(if (tag == ReceiptTag.DONE) "Done" else "Close")
                }
                TextButton(onClick = {
                    PendingPaymentReceipt.clear()
                    onBack()
                }) {
                    Text("Back to home")
                }
                Spacer(modifier = Modifier.height(Space.section))
            }
        }
    }
    if (askNotifications) {
        PermissionGuideDialog(
            lesson = PermissionLesson.Notifications,
            onAllow = {
                askNotifications = false
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
            onNotNow = {
                askNotifications = false
                PermissionPromptMemory.notificationsDeclined = true
            }
        )
    }
}

private enum class ReceiptTag(val label: String) {
    PROCESSING("Processing"),
    DONE("Done"),
    INTERRUPTED("Interrupted"),
    CANCELLED("Cancelled")
}

@Composable
private fun PaymentCongratsHeader(
    tag: ReceiptTag,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .border(
                    2.dp,
                    when (tag) {
                        ReceiptTag.DONE -> Accent
                        ReceiptTag.PROCESSING -> TagProcessingFg
                        else -> Mute
                    },
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            when (tag) {
                ReceiptTag.PROCESSING ->
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = TagProcessingFg
                    )
                ReceiptTag.DONE ->
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        tint = Accent,
                        modifier = Modifier.size(26.dp)
                    )
                else ->
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        tint = Mute,
                        modifier = Modifier.size(26.dp)
                    )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = title,
                    color = when (tag) {
                        ReceiptTag.DONE -> Accent
                        ReceiptTag.PROCESSING -> TagProcessingFg
                        else -> Mute
                    },
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                StatusTagChip(tag = tag)
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = Mute,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun StatusTagChip(tag: ReceiptTag) {
    val (bg, fg) = when (tag) {
        ReceiptTag.PROCESSING -> TagProcessingBg to TagProcessingFg
        ReceiptTag.DONE -> TagDoneBg to TagDoneFg
        ReceiptTag.INTERRUPTED,
        ReceiptTag.CANCELLED -> TagWarnBg to TagWarnFg
    }
    Text(
        text = tag.label.uppercase(),
        color = fg,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.5.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

@Composable
private fun PaymentDetailsBox(
    amount: String,
    typeLabel: String,
    toValue: String,
    partyLabel: String,
    idValue: String?,
    account: String?,
    startedLabel: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .border(BorderStroke(1.dp, DetailBorder), RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                PaymentField(
                    label = "Amount",
                    value = amount,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(12.dp))
                PaymentField(
                    label = "Type",
                    value = typeLabel,
                    modifier = Modifier.width(96.dp),
                    alignEnd = true
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                PaymentField(
                    label = "To",
                    value = toValue,
                    modifier = Modifier.weight(1f),
                    cover = true
                )
                if (idValue != null) {
                    Spacer(modifier = Modifier.width(12.dp))
                    PaymentField(
                        label = partyLabel,
                        value = idValue,
                        modifier = Modifier.width(110.dp),
                        alignEnd = true,
                        cover = true
                    )
                }
            }

            if (!account.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                PaymentField(label = "Account", value = account, cover = true)
            }

            Spacer(modifier = Modifier.height(16.dp))
            PaymentField(label = "Date & Time", value = startedLabel)
        }

        Text(
            text = "PAYMENT DETAILS",
            color = Color(0xFF6B7280),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .background(CardWhite)
                .padding(horizontal = 10.dp)
        )
    }
}

@Composable
private fun PaymentStub(
    tag: ReceiptTag,
    confirmCode: String?,
    waitingOnSms: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardWhite)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(110.dp)
                .border(1.dp, Hairline, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            when (tag) {
                ReceiptTag.PROCESSING ->
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        strokeWidth = 3.dp,
                        color = Accent
                    )
                ReceiptTag.DONE ->
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        tint = Income,
                        modifier = Modifier.size(40.dp)
                    )
                else ->
                    Text("—", color = Mute, fontSize = 18.sp)
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = when (tag) {
                    ReceiptTag.DONE -> "Payment complete"
                    ReceiptTag.PROCESSING -> "Awaiting SMS"
                    ReceiptTag.INTERRUPTED -> "Not completed"
                    ReceiptTag.CANCELLED -> "Cancelled"
                },
                color = when (tag) {
                    ReceiptTag.DONE -> Ink
                    ReceiptTag.INTERRUPTED,
                    ReceiptTag.CANCELLED -> Expense
                    else -> Ink
                },
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = when {
                    !confirmCode.isNullOrBlank() -> "Ref $confirmCode"
                    waitingOnSms -> "Heading home if the SMS is slow"
                    tag == ReceiptTag.PROCESSING -> "Updates when the SMS arrives"
                    else -> "Return home when ready"
                },
                color = Mute,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.recordingPrivacyCover(!confirmCode.isNullOrBlank())
            )
        }
    }
}

@Composable
private fun PaymentField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    alignEnd: Boolean = false,
    cover: Boolean = false
) {
    Column(
        modifier = modifier,
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start
    ) {
        Text(
            text = label,
            color = LabelGrey,
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            color = Ink,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.recordingPrivacyCover(cover)
        )
    }
}

@Composable
private fun PaymentTicketPerforation() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .background(CardWhite),
        contentAlignment = Alignment.Center
    ) {
        val dash = LabelGrey.copy(alpha = 0.55f)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .matchParentSize()
        ) {
            val r = 10.dp.toPx()
            val cy = size.height / 2f
            drawCircle(
                color = Color.Black,
                radius = r,
                center = Offset(0f, cy),
                blendMode = BlendMode.Clear
            )
            drawCircle(
                color = Color.Black,
                radius = r,
                center = Offset(size.width, cy),
                blendMode = BlendMode.Clear
            )
            drawLine(
                color = dash,
                start = Offset(r + 4.dp.toPx(), cy),
                end = Offset(size.width - r - 4.dp.toPx(), cy),
                strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
            )
        }
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(CardWhite, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.ContentCut,
                contentDescription = null,
                tint = LabelGrey,
                modifier = Modifier
                    .size(16.dp)
                    .rotate(-45f)
            )
        }
    }
}
