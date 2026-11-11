package com.lipabill.app.ui.tickets

import android.Manifest
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.Train
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lipabill.app.data.model.Ticket
import com.lipabill.app.data.model.TicketStatus
import com.lipabill.app.OpenedPass
import com.lipabill.app.metrics.AppMetrics
import com.lipabill.app.metrics.TicketImportResult
import com.lipabill.app.data.tickets.TicketDocumentKind
import com.lipabill.app.ui.adapt.LocalWindowForm
import com.lipabill.app.ui.permissions.rememberCameraAccessOffer
import com.lipabill.app.ui.privacy.recordingPrivacyCover
import com.lipabill.app.ui.permissions.permissionGranted
import com.lipabill.app.ui.adapt.WindowWidth
import com.lipabill.app.ui.theme.Canvas
import com.lipabill.app.ui.theme.Hairline
import com.lipabill.app.ui.theme.CardWhite
import com.lipabill.app.ui.theme.OnAccent
import com.lipabill.app.ui.theme.Expense
import com.lipabill.app.ui.theme.GeometricSansFamily
import com.lipabill.app.ui.LipaChipHeight
import com.lipabill.app.ui.theme.HomeType
import com.lipabill.app.ui.theme.Income
import com.lipabill.app.ui.theme.Ink
import com.lipabill.app.ui.theme.asPillLabel
import com.lipabill.app.ui.components.AccentGlyphBadge
import com.lipabill.app.ui.components.SpeedDialColumn
import com.lipabill.app.ui.components.SpeedDialItem
import com.lipabill.app.ui.theme.Accent
import com.lipabill.app.ui.util.takePersistableRead
import com.lipabill.app.ui.theme.Mute
import com.lipabill.app.ui.theme.SoftBlue
import com.lipabill.app.ui.theme.Space
import com.lipabill.app.ui.util.formatEventSchedule
import com.lipabill.app.ui.util.formatEventWhen
import com.lipabill.app.viewmodel.TicketsViewModel
import com.lipabill.app.viewmodel.label
import com.lipabill.app.viewmodel.phaseLabel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private enum class AddTicketMode {
    CONFIRMATION,
    MANUAL
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketsScreen(
    viewModel: TicketsViewModel,
    onBack: () -> Unit,
    onOpenTicket: (Long) -> Unit,
    pendingPkPassUri: Uri? = null,
    onPendingPkPassConsumed: () -> Unit = {},
    openedPass: OpenedPass = OpenedPass.Idle,
    onOpenedPassHandled: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val tickets by viewModel.tickets.collectAsStateWithLifecycle()
    // Surface events + SGR only; leftover flight tickets stay off the list.
    val visibleTickets = remember(tickets) {
        tickets.filter { !it.expectsBoardingPass || it.isRailTravel }
    }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var addMode by remember { mutableStateOf<AddTicketMode?>(null) }
    var addMenuOpen by remember { mutableStateOf(false) }
    var pendingDocumentKind by remember { mutableStateOf(TicketDocumentKind.AUTO) }
    var error by remember { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }

    suspend fun importDocument(
        uri: Uri,
        kind: TicketDocumentKind = pendingDocumentKind,
        source: String = ticketImportSource(kind)
    ) {
        if (importing) return
        importing = true
        try {
            val id = viewModel.addFromDocument(context, uri, kind)
            if (id == null) {
                AppMetrics.ticketImport(source, TicketImportResult.Duplicate)
                error = "That ticket is already saved, or has no usable code."
            } else {
                AppMetrics.ticketImport(source, TicketImportResult.Saved)
                addMode = null
                pendingDocumentKind = TicketDocumentKind.AUTO
                onOpenTicket(id)
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            AppMetrics.ticketImportFailure(source, t)
            error = t.message?.takeIf { it.isNotBlank() }
                ?: "Couldn’t read that ticket file."
        } finally {
            importing = false
        }
    }

    val documentPicker = rememberLauncherForActivityResult(
        OpenTicketDocumentContract()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        context.takePersistableRead(uri)
        scope.launch { importDocument(uri, pendingDocumentKind) }
    }

    fun launchDocumentPicker(kind: TicketDocumentKind) {
        pendingDocumentKind = kind
        documentPicker.launch(Unit)
    }

    // Hold the pass here so clearing the activity URI does not cancel this import.
    var queuedPass by remember { mutableStateOf<Uri?>(null) }
    SideEffect {
        val incoming = pendingPkPassUri ?: return@SideEffect
        if (incoming != queuedPass) queuedPass = incoming
    }
    LaunchedEffect(queuedPass) {
        val uri = queuedPass ?: return@LaunchedEffect
        onPendingPkPassConsumed()
        importDocument(uri, TicketDocumentKind.AUTO, source = "share")
    }
    LaunchedEffect(openedPass) {
        when (val pass = openedPass) {
            is OpenedPass.Saved -> {
                onOpenedPassHandled()
                onOpenTicket(pass.id)
            }
            is OpenedPass.Failed -> {
                onOpenedPassHandled()
                error = pass.message
            }
            OpenedPass.Idle, OpenedPass.Reading -> Unit
        }
    }

    BackHandler(enabled = addMenuOpen) { addMenuOpen = false }

    Scaffold(
        modifier = modifier,
        containerColor = Canvas,
        topBar = {
            TopAppBar(
                title = { Text("Passes", style = HomeType.greeting, color = Ink) },
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
                    containerColor = Canvas,
                    titleContentColor = Ink,
                    navigationIconContentColor = Ink
                )
            )
        },
        floatingActionButton = {
            SpeedDialColumn(
                expanded = addMenuOpen,
                onToggle = { addMenuOpen = !addMenuOpen },
                contentDescriptionCollapsed = "Add pass",
                actions = listOf(
                    SpeedDialItem(
                        label = "SGR booking SMS",
                        icon = Icons.Outlined.Train
                    ) {
                        addMenuOpen = false
                        addMode = AddTicketMode.CONFIRMATION
                    },
                    SpeedDialItem(
                        label = "Event ticket",
                        icon = Icons.Outlined.ConfirmationNumber
                    ) {
                        addMenuOpen = false
                        launchDocumentPicker(TicketDocumentKind.EVENT)
                    }
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (visibleTickets.isEmpty()) {
            EmptyTickets(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(Space.page),
                onEventTicket = { launchDocumentPicker(TicketDocumentKind.EVENT) },
                onSgrSms = { addMode = AddTicketMode.CONFIRMATION }
            )
        } else {
            var category by remember { mutableStateOf<PassCategory?>(null) }
            val zone = remember { ZoneId.systemDefault() }
            val today = remember { LocalDate.now(zone) }
            val counts = PassCategory.entries.associateWith { cat ->
                visibleTickets.count { it.passCategory() == cat }
            }
            LaunchedEffect(counts) {
                if (category != null && (counts[category] ?: 0) == 0) category = null
            }
            val entries = remember(visibleTickets, category, today) {
                buildPassList(visibleTickets, category, zone, today)
            }
            val upcomingCount = visibleTickets.count {
                it.effectiveStatus() != TicketStatus.USED
            }
            val todayCount = visibleTickets.count {
                it.isOnDay(zone, today) && it.effectiveStatus() != TicketStatus.USED
            }
            val usedCount = visibleTickets.size - upcomingCount
            val window = LocalWindowForm.current.width
            val columns = when (window) {
                WindowWidth.Compact -> GridCells.Fixed(1)
                WindowWidth.Medium -> GridCells.Fixed(2)
                WindowWidth.Expanded -> GridCells.Adaptive(400.dp)
            }
            val sidePad = if (window == WindowWidth.Compact) Space.page else 24.dp
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                PassStoryStrip(
                    todayCount = todayCount,
                    upcomingCount = upcomingCount,
                    usedCount = usedCount,
                    modifier = Modifier.padding(horizontal = sidePad, vertical = Space.gap)
                )
                PassCategoryChips(
                    selected = category,
                    counts = counts,
                    onSelect = { category = it },
                    modifier = Modifier.padding(horizontal = sidePad, vertical = Space.gap)
                )
                LazyVerticalGrid(
                    columns = columns,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(
                        start = sidePad,
                        end = sidePad,
                        bottom = 88.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(Space.block),
                    verticalArrangement = Arrangement.spacedBy(Space.block)
                ) {
                    items(
                        items = entries,
                        key = { it.key },
                        span = { entry ->
                            if (entry is PassListItem.Pass) {
                                GridItemSpan(1)
                            } else {
                                GridItemSpan(maxLineSpan)
                            }
                        }
                    ) { entry ->
                        when (entry) {
                            is PassListItem.Section -> PassSectionHeader(
                                title = entry.title,
                                count = entry.count
                            )
                            is PassListItem.TodayHero -> TodayPassCard(
                                ticket = entry.ticket,
                                onClick = { onOpenTicket(entry.ticket.id) }
                            )
                            is PassListItem.Pass -> TicketListRow(
                                ticket = entry.ticket,
                                onClick = { onOpenTicket(entry.ticket.id) }
                            )
                        }
                    }
                }
            }
            }
            AnimatedVisibility(
                visible = addMenuOpen,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Ink.copy(alpha = 0.28f))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { addMenuOpen = false }
                )
            }
        }
    }

    when (addMode) {
        AddTicketMode.CONFIRMATION -> PasteConfirmationDialog(
            onDismiss = { addMode = null },
            onSave = { message ->
                scope.launch {
                    try {
                        val id = viewModel.addFromConfirmation(message)
                        if (id == null) {
                            error = "That booking ref is already saved."
                        } else {
                            addMode = null
                            onOpenTicket(id)
                        }
                    } catch (t: Throwable) {
                        if (t is CancellationException) throw t
                        AppMetrics.ticketImportFailure("paste", t)
                        error = t.message?.takeIf { it.isNotBlank() }
                            ?: "Couldn’t parse that confirmation."
                    }
                }
            }
        )
        AddTicketMode.MANUAL -> ManualTicketDialog(
            onDismiss = { addMode = null },
            onSave = { title, code, venue, startsAt ->
                scope.launch {
                    val id = viewModel.addFromPaste(title, code, venue, startsAt)
                    if (id == null) {
                        error = "Couldn’t save — empty code or already added."
                    } else {
                        addMode = null
                        onOpenTicket(id)
                    }
                }
            }
        )
        null -> Unit
    }

    error?.let { msg ->
        AlertDialog(
            onDismissRequest = { error = null },
            confirmButton = {
                TextButton(onClick = { error = null }) { Text("OK") }
            },
            title = { Text("Passes") },
            text = { Text(msg) }
        )
    }

    if (importing || openedPass is OpenedPass.Reading) {
        val readingLabel = when (pendingDocumentKind) {
            TicketDocumentKind.EVENT -> "Reading event ticket…"
            else -> "Reading ticket…"
        }
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text(readingLabel) },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.block)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), color = Ink)
                    Text(
                        "Creating your ticket.",
                        style = HomeType.body,
                        color = Mute
                    )
                }
            }
        )
    }
}

@Composable
private fun EmptyTickets(
    onEventTicket: () -> Unit,
    onSgrSms: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(Space.block))
        Text(
            text = "The trip. The night out.",
            style = HomeType.greeting,
            color = Ink,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Nothing here yet. A pass is what the money already became — show it at the gate even without data.",
            style = HomeType.body,
            color = Mute,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EmptyEventPreview(
                onClick = onEventTicket,
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer { rotationZ = -5f }
            )
            EmptySgrPreview(
                onClick = onSgrSms,
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer { rotationZ = 5f }
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        PassPathCard(
            icon = Icons.Outlined.ConfirmationNumber,
            title = "Event ticket",
            body = "The night out is already paid. Import a PDF, photo, or pass.",
            onClick = onEventTicket
        )
        Spacer(modifier = Modifier.height(10.dp))
        PassPathCard(
            icon = Icons.Outlined.Train,
            title = "SGR booking SMS",
            body = "The fare is already paid. Paste the SMS you got after booking.",
            onClick = onSgrSms
        )
        Spacer(modifier = Modifier.height(88.dp))
    }
}

@Composable
private fun PassPathCard(
    icon: ImageVector,
    title: String,
    body: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardWhite)
            .clickable(onClick = onClick)
            .padding(horizontal = Space.card, vertical = Space.card)
    ) {
        AccentGlyphBadge(icon)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = HomeType.rowTitle, color = Ink)
            Spacer(modifier = Modifier.height(Space.tight))
            Text(body, style = HomeType.caption, color = Mute)
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Mute
        )
    }
}

@Composable
private fun PassStoryStrip(
    todayCount: Int,
    upcomingCount: Int,
    usedCount: Int,
    modifier: Modifier = Modifier
) {
    val icon = when {
        todayCount > 0 -> Icons.Outlined.CalendarMonth
        upcomingCount > 0 -> Icons.Outlined.ConfirmationNumber
        else -> Icons.Outlined.Check
    }
    val title = when {
        todayCount > 0 -> "Today is the day"
        upcomingCount > 0 -> "Ready at the gate"
        else -> "Already shown"
    }
    val detail = when {
        todayCount > 0 && todayCount == 1 -> "One pass to show at the gate."
        todayCount > 1 -> "$todayCount passes today."
        upcomingCount == 1 -> "One upcoming pass."
        upcomingCount > 1 -> "$upcomingCount upcoming."
        usedCount == 1 -> "The night or trip that already happened."
        else -> "The nights and trips that already happened."
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardWhite)
            .padding(horizontal = Space.card, vertical = Space.card)
    ) {
        AccentGlyphBadge(icon, size = 36.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = HomeType.section, color = Ink)
            Spacer(modifier = Modifier.height(Space.tight))
            Text(detail, style = HomeType.caption, color = Mute)
        }
    }
}

@Composable
private fun EmptyEventPreview(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .shadow(10.dp, RoundedCornerShape(22.dp), clip = false)
            .clip(RoundedCornerShape(22.dp))
            .background(EventStage)
            .clickable(onClick = onClick)
            .padding(10.dp)
            .semantics { contentDescription = "Add event ticket" }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(CardWhite)
        ) {
            Row(
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(SoftBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        tint = Accent,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.size(8.dp))
                Column {
                    Text(
                        text = "Congrats",
                        color = Accent,
                        fontFamily = GeometricSansFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Event ticket",
                        color = Mute,
                        fontFamily = GeometricSansFamily,
                        fontSize = 10.sp
                    )
                }
            }
            NotchTicketPerforation(
                notchColor = EventStage,
                dashColor = Mute.copy(alpha = 0.45f),
                bandHeight = 14.dp,
                dashInset = 10.dp
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                EmptyPreviewQr()
            }
        }
    }
}

@Composable
private fun EmptySgrPreview(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .shadow(10.dp, RoundedCornerShape(22.dp), clip = false)
            .clip(RoundedCornerShape(22.dp))
            .background(SgrStage)
            .clickable(onClick = onClick)
            .padding(10.dp)
            .semantics { contentDescription = "Add SGR booking SMS" }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(CardWhite)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "NBO",
                    color = Ink,
                    fontFamily = GeometricSansFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Icon(
                    imageVector = Icons.Outlined.Train,
                    contentDescription = null,
                    tint = Accent,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "MSA",
                    color = Ink,
                    fontFamily = GeometricSansFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(20.dp))
                    .background(SgrMint)
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "SGR",
                    color = Ink,
                    fontFamily = GeometricSansFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            NotchTicketPerforation(
                notchColor = SgrStage,
                dashColor = Mute.copy(alpha = 0.45f),
                bandHeight = 14.dp,
                dashInset = 10.dp
            )
            EmptyPreviewBarcode(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun EmptyPreviewQr() {
    val cells = remember {
        listOf(
            1, 1, 1, 0, 1,
            1, 0, 1, 1, 1,
            1, 1, 0, 1, 0,
            0, 1, 1, 0, 1,
            1, 0, 1, 1, 1
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        cells.chunked(5).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                row.forEach { on ->
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(if (on == 1) Ink else Color.Transparent)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyPreviewBarcode(modifier: Modifier = Modifier) {
    val widths = remember { listOf(2, 1, 3, 1, 2, 2, 1, 3, 1, 2, 1, 3, 2, 1, 2, 3, 1, 2) }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        widths.forEach { w ->
            Box(
                modifier = Modifier
                    .width(w.dp)
                    .fillMaxHeight()
                    .background(Ink)
            )
        }
    }
}

private enum class PassCategory(val label: String) {
    EVENTS("Events"),
    SGR("SGR");

    val icon: ImageVector
        get() = when (this) {
            EVENTS -> Icons.Outlined.ConfirmationNumber
            SGR -> Icons.Outlined.Train
        }
}

private fun Ticket.passCategory(): PassCategory = when {
    isRailTravel -> PassCategory.SGR
    else -> PassCategory.EVENTS
}

private fun Ticket.isOnDay(zone: ZoneId, day: LocalDate): Boolean {
    val millis = occasionMillis ?: return false
    return Instant.ofEpochMilli(millis).atZone(zone).toLocalDate() == day
}

private sealed interface PassListItem {
    val key: String

    data class Section(val title: String, val count: Int) : PassListItem {
        override val key: String = "section-$title"
    }

    data class TodayHero(val ticket: Ticket) : PassListItem {
        override val key: String = "today-${ticket.id}"
    }

    data class Pass(val ticket: Ticket) : PassListItem {
        override val key: String = "pass-${ticket.id}"
    }
}

private fun buildPassList(
    tickets: List<Ticket>,
    category: PassCategory?,
    zone: ZoneId,
    today: LocalDate
): List<PassListItem> {
    val pool = tickets.filter { category == null || it.passCategory() == category }
    val heroes = pool
        .filter { it.isOnDay(zone, today) && it.effectiveStatus() != TicketStatus.USED }
        .sortedBy { it.occasionMillis ?: Long.MAX_VALUE }
    val heroIds = heroes.map { it.id }.toSet()
    val rest = pool.filter { it.id !in heroIds }
    val order = compareBy<Ticket>(
        { it.effectiveStatus() == TicketStatus.USED },
        { it.occasionMillis == null },
        { it.occasionMillis ?: Long.MAX_VALUE }
    )
    val items = mutableListOf<PassListItem>()
    if (heroes.isNotEmpty()) {
        items += PassListItem.Section("Today", heroes.size)
        heroes.forEach { items += PassListItem.TodayHero(it) }
    }
    PassCategory.entries.forEach { cat ->
        if (category != null && category != cat) return@forEach
        val rows = rest.filter { it.passCategory() == cat }.sortedWith(order)
        if (rows.isEmpty()) return@forEach
        items += PassListItem.Section(cat.label, rows.size)
        rows.forEach { items += PassListItem.Pass(it) }
    }
    return items
}

@Composable
private fun PassCategoryChips(
    selected: PassCategory?,
    counts: Map<PassCategory, Int>,
    onSelect: (PassCategory?) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PassChip("All", selected = selected == null, onClick = { onSelect(null) })
        PassCategory.entries.forEach { cat ->
            if ((counts[cat] ?: 0) > 0) {
                PassChip(
                    cat.label,
                    selected = selected == cat,
                    icon = cat.icon,
                    onClick = { onSelect(cat) }
                )
            }
        }
    }
}

@Composable
private fun PassChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(LipaChipHeight)
            .clip(RoundedCornerShape(percent = 50))
            .background(if (selected) Accent else CardWhite)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) OnAccent else Accent,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = label,
            style = HomeType.label.asPillLabel(),
            color = if (selected) OnAccent else Ink,
            maxLines = 1
        )
    }
}

@Composable
private fun PassSectionHeader(
    title: String,
    count: Int
) {
    val icon = when (title) {
        "Today" -> Icons.Outlined.CalendarMonth
        PassCategory.SGR.label -> Icons.Outlined.Train
        else -> Icons.Outlined.ConfirmationNumber
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Space.block, bottom = Space.gap)
    ) {
        AccentGlyphBadge(icon, size = 36.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            style = HomeType.section,
            color = Ink,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = count.toString(),
            style = HomeType.caption,
            color = Mute
        )
    }
}

@Composable
private fun TodayPassCard(
    ticket: Ticket,
    onClick: () -> Unit
) {
    val schedule = formatEventSchedule(ticket.occasionMillis, ticket.notes)
    val time = schedule.time
    val place = listOfNotNull(
        ticket.venue?.takeIf { it.isNotBlank() },
        ticket.seatOrTier?.takeIf { it.isNotBlank() }
    ).joinToString(" · ")
    val zoned = ticket.occasionMillis?.let {
        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())
    }
    val dayNumber = zoned?.dayOfMonth?.toString()
    val month = zoned?.format(DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH))?.uppercase(Locale.ENGLISH)
    val countdown = ticket.occasionMillis?.let { start ->
        val minutes = (start - System.currentTimeMillis()) / 60_000L
        when {
            minutes < 0 -> null
            minutes < 60 -> "Starting soon"
            minutes < 120 -> "In 1 hour"
            minutes < 18 * 60 -> "In ${minutes / 60} hours"
            else -> null
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(16.dp, RoundedCornerShape(28.dp), spotColor = Accent.copy(alpha = 0.35f))
            .clip(RoundedCornerShape(28.dp))
            .background(Accent)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 18.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(CardWhite)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "TODAY",
                    style = HomeType.label,
                    color = Accent,
                    letterSpacing = 1.1.sp
                )
                Text(
                    text = dayNumber ?: "—",
                    color = Ink,
                    fontFamily = GeometricSansFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 32.sp,
                    lineHeight = 34.sp
                )
                if (month != null) {
                    Text(
                        text = month,
                        style = HomeType.label,
                        color = Mute,
                        letterSpacing = 0.8.sp
                    )
                }
            }
            Spacer(modifier = Modifier.size(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AccentGlyphBadge(
                        icon = ticket.passCategory().icon,
                        fill = OnAccent.copy(alpha = 0.16f),
                        tint = OnAccent,
                        size = 22.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = ticket.passCategory().label,
                        style = HomeType.caption,
                        color = OnAccent.copy(alpha = 0.72f)
                    )
                    if (countdown != null) {
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = countdown,
                            style = HomeType.caption,
                            color = OnAccent
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = ticket.title,
                    color = OnAccent,
                    fontFamily = GeometricSansFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 20.sp,
                    lineHeight = 24.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.recordingPrivacyCover()
                )
                if (time != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = time,
                        color = OnAccent,
                        fontFamily = GeometricSansFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        lineHeight = 20.sp
                    )
                }
                if (place.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = place,
                        style = HomeType.caption,
                        color = OnAccent.copy(alpha = 0.78f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.recordingPrivacyCover()
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(OnAccent.copy(alpha = 0.18f))
        )
        Text(
            text = "Open pass",
            style = HomeType.label,
            color = OnAccent,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)
        )
    }
}

@Composable
private fun TicketListRow(
    ticket: Ticket,
    onClick: () -> Unit
) {
    val used = !ticket.isConfirmationOnly && ticket.effectiveStatus() == TicketStatus.USED
    val statusColor = when {
        ticket.isConfirmationOnly -> SoftBlue
        used -> Mute
        else -> when (ticket.effectiveStatus()) {
            TicketStatus.ACTIVE -> Income
            TicketStatus.USED -> Mute
            TicketStatus.PAST_DUE -> Expense
        }
    }
    val badge = when {
        ticket.isConfirmationOnly -> "Awaiting pass"
        else -> ticket.effectiveStatus().label()
    }
    val rowIcon = when {
        ticket.isConfirmationOnly -> Icons.Outlined.Sms
        ticket.isRailTravel -> Icons.Outlined.Train
        else -> Icons.Outlined.ConfirmationNumber
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .fadeWhenUsed(used)
            .shadow(
                if (used) 0.dp else 2.dp,
                RoundedCornerShape(18.dp),
                spotColor = Color.Black.copy(alpha = 0.06f)
            )
            .clip(RoundedCornerShape(18.dp))
            .background(if (used) Hairline else CardWhite)
            .clickable(onClick = onClick)
            .padding(horizontal = Space.card, vertical = Space.cardH),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AccentGlyphBadge(
            icon = rowIcon,
            fill = if (used) Canvas else SoftBlue,
            tint = if (used) Mute else Accent,
            size = 40.dp
        )
        Spacer(modifier = Modifier.size(Space.block))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = ticket.title,
                style = HomeType.rowTitle,
                color = if (used) Mute else Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.recordingPrivacyCover()
            )
            val subtitle = buildList {
                ticket.orderId?.takeIf { ticket.isConfirmationOnly }?.let { add("Ref $it") }
                    ticket.startsAtMillis?.let { start ->
                        val schedule = formatEventSchedule(start, ticket.notes)
                        add(listOfNotNull(schedule.date, schedule.time).joinToString(" · "))
                    }
                    ticket.seatOrTier?.let { add(it) }
                        ?: ticket.venue?.let { add(it) }
            }.joinToString(" · ").ifBlank { ticket.phaseLabel() }
            Text(
                subtitle,
                style = HomeType.caption,
                color = Mute,
                maxLines = 2,
                modifier = Modifier.recordingPrivacyCover(subtitle.isNotBlank())
            )
        }
        Text(
            text = badge,
            style = HomeType.label,
            color = if (ticket.isConfirmationOnly) Ink else statusColor,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(statusColor.copy(alpha = 0.18f))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun PasteConfirmationDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var message by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("SGR booking SMS") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.gap)) {
                Text(
                    "Paste the SMS you got after paying — Ref No, train, date, seat. Add the printed boarding pass on the ticket next.",
                    style = HomeType.body,
                    color = Mute
                )
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text("Confirmation message") },
                    minLines = 5,
                    modifier = Modifier
                        .fillMaxWidth()
                        .recordingPrivacyCover(message.isNotBlank()),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences
                    )
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(message) },
                enabled = message.isNotBlank()
            ) { Text("Create ticket") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ManualTicketDialog(
    onDismiss: () -> Unit,
    onSave: (title: String, code: String, venue: String?, startsAtMillis: Long?) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var venue by remember { mutableStateOf("") }
    var startsAtMillis by remember { mutableStateOf<Long?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Paste ticket code") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.gap)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Event / title") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .recordingPrivacyCover(title.isNotBlank()),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words
                    )
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("Ticket code / QR payload") },
                    singleLine = false,
                    minLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .recordingPrivacyCover(code.isNotBlank())
                )
                OutlinedTextField(
                    value = venue,
                    onValueChange = { venue = it },
                    label = { Text("Venue (optional)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .recordingPrivacyCover(venue.isNotBlank())
                )
                OutlinedButton(
                    onClick = {
                        pickEventDateTime(context, startsAtMillis) { startsAtMillis = it }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Outlined.Event, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        startsAtMillis?.let { formatEventWhen(it) } ?: "Set event date & time"
                    )
                }
                if (startsAtMillis != null) {
                    TextButton(onClick = { startsAtMillis = null }) {
                        Text("Clear event date")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(title, code, venue.ifBlank { null }, startsAtMillis)
                },
                enabled = code.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun ticketImportSource(kind: TicketDocumentKind): String =
    if (kind == TicketDocumentKind.PKPASS) "pkpass" else "pdf"
