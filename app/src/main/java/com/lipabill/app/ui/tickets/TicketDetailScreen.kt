package com.lipabill.app.ui.tickets

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lipabill.app.data.model.BoardingLeg
import com.lipabill.app.data.model.BoardingPassSnapshot
import com.lipabill.app.data.model.TicketBarcodeFormat
import com.lipabill.app.data.model.TicketStatus
import com.lipabill.app.data.tickets.BookingConfirmationParser
import com.lipabill.app.metrics.AppMetrics
import com.lipabill.app.metrics.TicketImportResult
import com.lipabill.app.ui.permissions.permissionGranted
import com.lipabill.app.ui.permissions.rememberCameraAccessOffer
import com.lipabill.app.ui.components.DetailSkeleton
import com.lipabill.app.ui.theme.Canvas
import com.lipabill.app.ui.theme.Expense
import com.lipabill.app.ui.theme.GeometricSansFamily
import com.lipabill.app.ui.theme.HomeType
import com.lipabill.app.ui.theme.Income
import com.lipabill.app.ui.theme.Ink
import com.lipabill.app.ui.theme.Mute
import com.lipabill.app.ui.theme.Space
import com.lipabill.app.ui.util.findComponentActivity
import com.lipabill.app.ui.util.takePersistableRead
import com.lipabill.app.viewmodel.TicketDetailViewModel
import com.lipabill.app.viewmodel.label
import java.util.Calendar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketDetailScreen(
    viewModel: TicketDetailViewModel,
    onBack: () -> Unit
) {
    val ticket by viewModel.ticket.collectAsStateWithLifecycle()
    val ticketLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val hasOriginal by viewModel.hasOriginal.collectAsStateWithLifecycle()
    if (ticketLoading || ticket == null) {
        DetailSkeleton()
        return
    }
    val view = LocalView.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    var attaching by remember { mutableStateOf(false) }
    var showAttachScanner by remember { mutableStateOf(false) }
    var attachLeg by remember { mutableStateOf<BoardingLeg?>(null) }
    var showBoardingQrDialog by remember { mutableStateOf(false) }

    fun openOriginal() {
        viewModel.viewOriginal(
            onOpened = { uri, mime ->
                runCatching {
                    context.startActivity(
                        android.content.Intent(android.content.Intent.ACTION_VIEW)
                            .setDataAndType(uri, mime)
                            .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    )
                }.onFailure {
                    error = "Couldn’t open the original file."
                }
            },
            onMissing = { error = "No original file is saved for this pass." }
        )
    }

    // Gate scan: only boost brightness / keep-awake for active tickets.
    val boostScanBrightness = ticket?.effectiveStatus() == TicketStatus.ACTIVE
    DisposableEffect(boostScanBrightness) {
        if (!boostScanBrightness) {
            return@DisposableEffect onDispose { }
        }
        val win = view.context.findComponentActivity()?.window
        val previousBrightness = win?.attributes?.screenBrightness
            ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        if (win != null) {
            win.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val attrs = win.attributes
            attrs.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
            win.attributes = attrs
        }
        onDispose {
            if (win != null) {
                win.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                val attrs = win.attributes
                attrs.screenBrightness = previousBrightness
                win.attributes = attrs
            }
        }
    }

    suspend fun attachUri(uri: Uri) {
        if (attaching) return
        attaching = true
        try {
            val ok = viewModel.attachBoardingPass(context, uri, attachLeg)
            AppMetrics.ticketImport(
                "pdf",
                if (ok) TicketImportResult.Saved else TicketImportResult.Duplicate
            )
            if (!ok) error = "Couldn’t add that boarding pass. It may already be saved, or it couldn’t be read."
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            AppMetrics.ticketImportFailure("pdf", t)
            error = t.message?.takeIf { it.isNotBlank() }
                ?: "Couldn’t read that boarding pass."
        } finally {
            attaching = false
            attachLeg = null
        }
    }

    val offerCamera = rememberCameraAccessOffer {
        showAttachScanner = true
    }

    val documentPicker = rememberLauncherForActivityResult(
        OpenTicketDocumentContract()
    ) { uri ->
        if (uri == null) {
            attachLeg = null
            return@rememberLauncherForActivityResult
        }
        context.takePersistableRead(uri)
        scope.launch { attachUri(uri) }
    }

    fun launchAttachPicker(leg: BoardingLeg? = null) {
        attachLeg = leg
        documentPicker.launch(Unit)
    }

    fun launchAttachScan(leg: BoardingLeg? = null) {
        attachLeg = leg
        if (context.permissionGranted(Manifest.permission.CAMERA)) {
            showAttachScanner = true
        } else {
            offerCamera()
        }
    }

    if (showAttachScanner) {
        QrScannerScreen(
            onResult = { contents, spec ->
                showAttachScanner = false
                val leg = attachLeg
                scope.launch {
                    attaching = true
                    try {
                        val ok = viewModel.attachBoardingPassScan(contents, leg, spec)
                        AppMetrics.ticketImport(
                            "camera",
                            when {
                                contents.isBlank() -> TicketImportResult.Unreadable
                                ok -> TicketImportResult.Saved
                                else -> TicketImportResult.Duplicate
                            }
                        )
                        if (!ok) error = "Couldn’t add that code. It’s already on another pass."
                    } finally {
                        attaching = false
                        attachLeg = null
                    }
                }
            },
            onCancel = {
                showAttachScanner = false
                attachLeg = null
            }
        )
        return
    }

    val current = ticket
    val isRailDetail = current?.isRailTravel == true
    val isEventDetail = current != null && !current.isTravelTicket
    val detailStage = when {
        isRailDetail -> Canvas
        isEventDetail -> EventPassNight
        else -> Canvas
    }

    Scaffold(
        containerColor = detailStage,
        topBar = {
            if (isEventDetail) {
                // Event pass draws its own glass header.
            } else if (isRailDetail) {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            "Boarding pass",
                            color = Ink,
                            fontFamily = GeometricSansFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 17.sp
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "Back",
                                tint = Ink
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Canvas)
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            if (isRailDetail) "Boarding pass" else (current?.title ?: "Ticket"),
                            style = HomeType.greeting
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Canvas)
                )
            }
        }
    ) { padding ->
        if (current == null) {
            Text(
                "Ticket not found",
                modifier = Modifier.padding(padding).padding(Space.page),
                style = HomeType.body,
                color = Mute
            )
            return@Scaffold
        }

        val isRail = current.isRailTravel
        val outboundSnap = current.boardingSnapshot(BoardingLeg.OUTBOUND)
        val outboundQr = rememberBoardingQr(if (isRail) outboundSnap else null)

        // Event QR (non-travel)
        val eventGateCode = current.gateBarcodeValue.takeIf {
            !current.isTravelTicket
        }
        val hasEventPayload = !eventGateCode.isNullOrBlank() &&
            !BookingConfirmationParser.isConfirmationPlaceholder(eventGateCode) &&
            !eventGateCode.startsWith("ETKT:", ignoreCase = true) &&
            // An SMS-only event ticket has no QR yet: never draw its placeholder.
            !com.lipabill.app.data.tickets.EventTicketSms.isPending(eventGateCode) &&
            !eventGateCode.startsWith("BOARDING:", ignoreCase = true)
        val gateQr = rememberTicketQrBitmap(
            value = eventGateCode.takeIf { hasEventPayload },
            format = current.gateBarcodeFormat,
            sizePx = 480
        )

        val displayStatus = current.effectiveStatus()
        val statusColor = when (displayStatus) {
            TicketStatus.ACTIVE -> Income
            TicketStatus.USED -> Mute
            TicketStatus.PAST_DUE -> Expense
        }
        val isUsed = displayStatus == TicketStatus.USED
                val bookingPdf417 = rememberHorizontalPdf417ImageBitmap(
            bcbpPayload = if (isRail) {
                val ref = current.orderId?.takeIf { it.isNotBlank() }
                    ?: current.barcodeValue
                        .removePrefix("REF:")
                        .removePrefix("ref:")
                        .trim()
                        .takeIf { it.isNotBlank() }
                ref?.let { "REF:$it" }
            } else {
                null
            },
            // White bars on Accent stub (navy in both themes).
            foregroundColor = android.graphics.Color.WHITE,
            backgroundColor = android.graphics.Color.TRANSPARENT,
            allowPlaceholder = true
        )
        if (isRail) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = Space.block)
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    SgrTicketStyleCard(
                        ticket = current,
                        pdf417 = bookingPdf417,
                        isUsed = isUsed,
                        showViewBoardingQr = current.hasBoardingPass && outboundQr != null,
                        onViewBoardingQr = { showBoardingQrDialog = true }
                    )

                    if (hasOriginal) {
                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(
                            onClick = { openOriginal() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("View original", color = Mute)
                        }
                    }
                    if (!isUsed && (!current.hasBoardingPass || current.canMarkUsedFromEventDay())) {
                        Spacer(modifier = Modifier.height(16.dp))
                        if (!current.hasBoardingPass) {
                            SgrPillButton("Add boarding pass", onClick = { launchAttachPicker(BoardingLeg.OUTBOUND) })
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(
                                onClick = { launchAttachScan(BoardingLeg.OUTBOUND) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Scan boarding QR", color = Mute)
                            }
                        } else {
                            Spacer(modifier = Modifier.height(8.dp))
                            SgrPillButton("Mark as used", onClick = { viewModel.markUsed() })
                        }
                    }
                }
                Spacer(modifier = Modifier.height(Space.gap))
            }

            if (showBoardingQrDialog && outboundQr != null) {
                AlertDialog(
                    onDismissRequest = { showBoardingQrDialog = false },
                    containerColor = Color.White,
                    titleContentColor = Color(0xFF14101F),
                    textContentColor = Color(0xFF14101F),
                    confirmButton = {
                        TextButton(onClick = { showBoardingQrDialog = false }) {
                            Text("Close", color = Color(0xFF14101F), fontFamily = GeometricSansFamily)
                        }
                    },
                    title = {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            MadarakaExpressLogo()
                        }
                    },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            BoardingQrCard(code = outboundQr)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Show this code at the gate",
                                color = Color(0xFF6B6B76),
                                fontFamily = GeometricSansFamily,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                )
            }
        } else if (isEventDetail) {
            EventPassContent(
                ticket = current,
                qrBitmap = gateQr,
                status = displayStatus,
                canMarkUsed = !isUsed && current.canMarkUsedFromEventDay(),
                contentPadding = padding,
                onBack = onBack,
                onMarkUsed = { viewModel.markUsed() },
                onChangeDate = if (!isUsed) {
                    {
                        pickEventDateTime(view.context, current.startsAtMillis) {
                            viewModel.setEventStartsAt(it)
                        }
                    }
                } else {
                    null
                },
                onViewOriginal = if (hasOriginal) {
                    { openOriginal() }
                } else {
                    null
                },
                printedCode = eventGateCode.takeIf {
                    hasEventPayload && current.gateBarcodeFormat == TicketBarcodeFormat.TEXT
                }
            )
        } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Space.page)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = Space.block),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(Space.gap))
                Text(
                    text = displayStatus.label(),
                    style = HomeType.label,
                    color = statusColor,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(statusColor.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
                Spacer(modifier = Modifier.height(Space.block))

                // Only non-SGR travel passes reach here (events have their own screen).
                Text(
                    "This pass type is no longer available. Swipe left on Passes to delete it.",
                    style = HomeType.body,
                    color = Mute,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(Space.gap))
        }
        } // end non-rail
    }

    error?.let { msg ->
        AlertDialog(
            onDismissRequest = { error = null },
            confirmButton = {
                TextButton(onClick = { error = null }) { Text("OK") }
            },
            title = { Text("Boarding pass") },
            text = { Text(msg) }
        )
    }

    if (attaching) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("Attaching…") },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.block)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), color = Ink)
                    Text("Only the boarding pass will be added. Your booking details won’t change.", style = HomeType.body, color = Mute)
                }
            }
        )
    }
}

@Composable
private fun rememberBoardingQr(snapshot: BoardingPassSnapshot?): BoardingCode? {
    val code = snapshot?.barcodeValue?.takeIf { it.isNotBlank() }
    val usable = code != null &&
        !BookingConfirmationParser.isConfirmationPlaceholder(code) &&
        !code.startsWith("ETKT:", ignoreCase = true) &&
        !code.startsWith("BOARDING:", ignoreCase = true)
    return rememberBoardingCode(
        value = code.takeIf { usable },
        format = snapshot?.barcodeFormat,
        serializedSpec = snapshot?.barcodeSpec
    )
}

internal fun pickEventDateTime(
    context: Context,
    initialMillis: Long?,
    onPicked: (Long) -> Unit
) {
    val cal = Calendar.getInstance().apply {
        if (initialMillis != null) timeInMillis = initialMillis
    }
    DatePickerDialog(
        context,
        { _, year, month, day ->
            cal.set(Calendar.YEAR, year)
            cal.set(Calendar.MONTH, month)
            cal.set(Calendar.DAY_OF_MONTH, day)
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    cal.set(Calendar.HOUR_OF_DAY, hour)
                    cal.set(Calendar.MINUTE, minute)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    onPicked(cal.timeInMillis)
                },
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE),
                false
            ).show()
        },
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH),
        cal.get(Calendar.DAY_OF_MONTH)
    ).show()
}

/** Full-width pill in the SGR stub colour, for the actions under a boarding pass. */
@Composable
private fun SgrPillButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = RoundedCornerShape(28.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = SgrTicketFill,
            contentColor = SgrOnTicket
        )
    ) {
        Text(label, fontFamily = GeometricSansFamily, fontWeight = FontWeight.SemiBold)
    }
}
