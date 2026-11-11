package com.lipabill.app.ui.tickets

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.lipabill.app.ui.privacy.recordingPrivacyCover
import com.lipabill.app.ui.theme.Accent
import com.lipabill.app.ui.theme.Canvas
import com.lipabill.app.ui.theme.CardWhite
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
    val view = LocalView.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var attaching by remember { mutableStateOf(false) }
    var showAttachScanner by remember { mutableStateOf(false) }
    var attachLeg by remember { mutableStateOf<BoardingLeg?>(null) }
    var showBoardingQrDialog by remember { mutableStateOf(false) }

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
            if (!ok) error = "Couldn’t attach that boarding pass (duplicate QR or unreadable)."
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
            onResult = { contents ->
                showAttachScanner = false
                val leg = attachLeg
                scope.launch {
                    attaching = true
                    try {
                        val ok = viewModel.attachBoardingPassScan(contents, leg)
                        AppMetrics.ticketImport(
                            "camera",
                            when {
                                contents.isBlank() -> TicketImportResult.Unreadable
                                ok -> TicketImportResult.Saved
                                else -> TicketImportResult.Duplicate
                            }
                        )
                        if (!ok) error = "Couldn’t attach that code (already used elsewhere)."
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
        isRailDetail -> SgrStage
        isEventDetail -> EventStage
        else -> Canvas
    }
    val useColoredStage = isRailDetail || isEventDetail
    val sgrHeader = remember(current) {
        current?.takeIf { it.isRailTravel }?.toSgrTicketUiModel()
    }

    Scaffold(
        containerColor = detailStage,
        topBar = {
            if (useColoredStage) {
                TopAppBar(
                    title = {
                        Text(
                            if (isRailDetail) "Upcoming Trips" else (current?.title ?: "Ticket"),
                            color = Color.White,
                            fontFamily = GeometricSansFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 17.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.recordingPrivacyCover(
                                !isRailDetail && !current?.title.isNullOrBlank()
                            )
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = detailStage,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White
                    )
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            current?.title ?: "Ticket",
                            style = HomeType.greeting,
                            modifier = Modifier.recordingPrivacyCover(!current?.title.isNullOrBlank())
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
                color = if (useColoredStage) Color.White.copy(alpha = 0.7f) else Mute
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
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = sgrHeader?.headerWhen ?: "",
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 13.sp,
                        fontFamily = GeometricSansFamily
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = sgrHeader?.headerRoute ?: "SGR",
                        color = Color.White,
                        fontSize = 26.sp,
                        fontFamily = GeometricSansFamily,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.recordingPrivacyCover(
                            !sgrHeader?.headerRoute.isNullOrBlank() && sgrHeader?.headerRoute != "SGR"
                        )
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    SgrTicketStyleCard(
                        ticket = current,
                        pdf417 = bookingPdf417,
                        stageColor = SgrStage,
                        isUsed = isUsed,
                        showViewBoardingQr = current.hasBoardingPass && outboundQr != null,
                        onViewBoardingQr = { showBoardingQrDialog = true }
                    )

                    if (!isUsed && (!current.hasBoardingPass || current.canMarkUsedFromEventDay())) {
                        Spacer(modifier = Modifier.height(16.dp))
                        if (!current.hasBoardingPass) {
                            Button(
                                onClick = { launchAttachPicker(BoardingLeg.OUTBOUND) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp),
                                shape = RoundedCornerShape(28.dp),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SgrMint,
                                    contentColor = Ink
                                )
                            ) {
                                Text(
                                    "Add boarding pass",
                                    fontFamily = GeometricSansFamily,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(
                                onClick = { launchAttachScan(BoardingLeg.OUTBOUND) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Scan boarding QR", color = Color.White.copy(alpha = 0.75f))
                            }
                        } else {
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { viewModel.markUsed() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp),
                                shape = RoundedCornerShape(28.dp),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SgrMint,
                                    contentColor = Ink
                                )
                            ) {
                                Text(
                                    "Mark as used",
                                    fontFamily = GeometricSansFamily,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
                TextButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Delete ticket", color = Expense)
                }
                Spacer(modifier = Modifier.height(Space.gap))
            }

            if (showBoardingQrDialog && outboundQr != null) {
                AlertDialog(
                    onDismissRequest = { showBoardingQrDialog = false },
                    containerColor = Color.White,
                    titleContentColor = Ink,
                    textContentColor = Ink,
                    confirmButton = {
                        TextButton(onClick = { showBoardingQrDialog = false }) {
                            Text("Close", color = Ink, fontFamily = GeometricSansFamily)
                        }
                    },
                    title = {
                        Text(
                            "Boarding QR",
                            fontFamily = GeometricSansFamily,
                            fontWeight = FontWeight.SemiBold,
                            color = Ink
                        )
                    },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.White)
                                    .padding(12.dp)
                            ) {
                                Image(
                                    bitmap = outboundQr,
                                    contentDescription = "Boarding QR code",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .size(240.dp)
                                        .recordingPrivacyCover()
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Show this code at the gate",
                                color = Mute,
                                fontFamily = GeometricSansFamily,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                )
            }
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
                    color = if (isEventDetail) Color.White else statusColor,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isEventDetail) {
                                Color.White.copy(alpha = 0.14f)
                            } else {
                                statusColor.copy(alpha = 0.12f)
                            }
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
                Spacer(modifier = Modifier.height(Space.block))

                if (current.isTravelTicket) {
                    Text(
                        "This pass type is no longer available. You can delete it.",
                        style = HomeType.body,
                        color = Mute,
                        textAlign = TextAlign.Center
                    )
                } else {
                    EventTicketStyleCard(
                        ticket = current,
                        qrBitmap = gateQr,
                        stageColor = EventStage,
                        isUsed = isUsed,
                        onChangeDate = if (!isUsed) {
                            {
                                pickEventDateTime(
                                    view.context,
                                    current.startsAtMillis
                                ) { viewModel.setEventStartsAt(it) }
                            }
                        } else {
                            null
                        }
                    )
                }
            }

            if (!isUsed && !current.isTravelTicket && current.canMarkUsedFromEventDay()) {
                Button(
                    onClick = { viewModel.markUsed() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(28.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CardWhite,
                        contentColor = Accent
                    )
                ) {
                    Text(
                        "Mark as used",
                        fontFamily = GeometricSansFamily,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            TextButton(
                onClick = { confirmDelete = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Delete ticket",
                    color = if (isEventDetail) Color.White.copy(alpha = 0.75f) else Expense
                )
            }
            Spacer(modifier = Modifier.height(Space.gap))
        }
        } // end non-rail
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ticket?") },
            text = { Text("This removes the ticket from this phone. You can add it again later.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete(onBack)
                }) { Text("Delete", color = Expense) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
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
                    Text("Attaching boarding pass only — booking details stay as they are.", style = HomeType.body, color = Mute)
                }
            }
        )
    }
}

@Composable
private fun rememberBoardingQr(snapshot: BoardingPassSnapshot?): ImageBitmap? {
    val code = snapshot?.barcodeValue?.takeIf { it.isNotBlank() }
    val usable = code != null &&
        !BookingConfirmationParser.isConfirmationPlaceholder(code) &&
        !code.startsWith("ETKT:", ignoreCase = true) &&
        !code.startsWith("BOARDING:", ignoreCase = true)
    return rememberTicketQrBitmap(
        value = code.takeIf { usable },
        format = TicketBarcodeFormat.QR_CODE,
        sizePx = 480
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
