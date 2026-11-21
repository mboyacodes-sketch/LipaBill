package com.lipabill.app.ui.tickets

import com.lipabill.app.data.tickets.EventTicketSms
import androidx.compose.runtime.ReadOnlyComposable
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.produceState
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import com.lipabill.app.data.tickets.intelligence.DuplicateBookingRefRule
import com.lipabill.app.data.tickets.intelligence.OverlappingEventsRule
import com.lipabill.app.data.tickets.intelligence.TodayStage
import com.lipabill.app.data.tickets.intelligence.TodayStages
import com.lipabill.app.data.tickets.intelligence.TodayStatus
import com.lipabill.app.ui.theme.LipaPalette
import com.lipabill.app.ui.theme.LocalLipaColors
import kotlinx.coroutines.delay
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
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.Train
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material.icons.outlined.Delete
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
import com.lipabill.app.data.model.TicketSource
import com.lipabill.app.data.tickets.AlreadySavedException
import com.lipabill.app.data.tickets.intelligence.ContextFinding
import com.lipabill.app.data.tickets.intelligence.evaluateContextRules
import com.lipabill.app.data.tickets.PassDraft
import com.lipabill.app.data.tickets.TicketDocumentKind
import com.lipabill.app.ui.adapt.LocalWindowForm
import com.lipabill.app.ui.privacy.pinsExactSeat
import com.lipabill.app.ui.privacy.recordingPrivacyCover
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
import com.lipabill.app.ui.components.PassesSkeleton
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
    val ticketsLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    // Surface events + SGR only; leftover flight tickets stay off the list.
    val visibleTickets = remember(tickets) {
        tickets.filter { !it.expectsBoardingPass || it.isRailTravel }
    }
    if (ticketsLoading) {
        PassesSkeleton(modifier = modifier)
        return
    }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var addMode by remember { mutableStateOf<AddTicketMode?>(null) }
    var addMenuOpen by remember { mutableStateOf(false) }
    var pendingDocumentKind by remember { mutableStateOf(TicketDocumentKind.AUTO) }
    var error by remember { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }
    var reviewDraft by remember { mutableStateOf<ReviewDraft?>(null) }
    var pendingDeleteId by remember { mutableStateOf<Long?>(null) }

    suspend fun importDocument(
        uri: Uri,
        kind: TicketDocumentKind = pendingDocumentKind,
        source: String = ticketImportSource(kind)
    ) {
        if (importing) return
        importing = true
        try {
            val read = viewModel.readDocument(context, uri, kind)
            val draft = read.draft
            // PDF / photo event tickets: let the user check what was read before saving.
            if (draft.source == TicketSource.E_TICKET && !draft.expectsBoardingPass) {
                reviewDraft = ReviewDraft(read, kind, source)
                return
            }
            val id = viewModel.saveImport(draft, kind, read)
            if (id == null) {
                AppMetrics.ticketImport(source, TicketImportResult.Duplicate)
                error = "That ticket is already saved, or has no usable code."
            } else {
                AppMetrics.ticketImport(source, TicketImportResult.Saved)
                addMode = null
                pendingDocumentKind = TicketDocumentKind.AUTO
                onOpenTicket(id)
            }
        } catch (t: AlreadySavedException) {
            // Same file as a saved pass: show that pass rather than an error.
            AppMetrics.ticketImport(source, TicketImportResult.Duplicate)
            addMode = null
            pendingDocumentKind = TicketDocumentKind.AUTO
            onOpenTicket(t.ticketId)
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
                        label = "Ticket SMS",
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
            // Ticks so the Today pill ("Starts in 25 min") and its stage stay current.
            val now by produceState(System.currentTimeMillis()) {
                while (true) {
                    delay(30_000L)
                    value = System.currentTimeMillis()
                }
            }
            val minute = now / 60_000L
            val counts = PassCategory.entries.associateWith { cat ->
                visibleTickets.count { it.passCategory() == cat }
            }
            LaunchedEffect(counts) {
                if (category != null && (counts[category] ?: 0) == 0) category = null
            }
            // Overlaps and twin bookings — news the list itself can't show.
            val notices = remember(visibleTickets) {
                evaluateContextRules(
                    visibleTickets,
                    System.currentTimeMillis(),
                    rules = listOf(OverlappingEventsRule, DuplicateBookingRefRule)
                ).distinctBy { it.ruleId to it.ticketIds.toSet() }.take(2)
            }
            val entries = remember(visibleTickets, category, minute, notices) {
                buildPassList(visibleTickets, category, zone, now, hasNotices = notices.isNotEmpty())
            }
            val window = LocalWindowForm.current.width
            val columns = when (window) {
                WindowWidth.Compact -> GridCells.Fixed(1)
                WindowWidth.Medium -> GridCells.Fixed(2)
                WindowWidth.Expanded -> GridCells.Adaptive(400.dp)
            }
            val sidePad = if (window == WindowWidth.Compact) Space.page else 24.dp
            LazyVerticalGrid(
                columns = columns,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(
                    start = sidePad,
                    end = sidePad,
                    top = Space.gap,
                    bottom = 88.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(Space.block),
                verticalArrangement = Arrangement.spacedBy(Space.block)
            ) {
                items(
                    items = entries,
                    key = { it.key },
                    span = { entry ->
                        if (entry is PassListItem.Pass) GridItemSpan(1) else GridItemSpan(maxLineSpan)
                    }
                ) { entry ->
                    when (entry) {
                        is PassListItem.Section -> PassSectionHeader(title = entry.title)
                        is PassListItem.TodayHero -> PassSwipeToDelete(
                            onRequestDelete = { pendingDeleteId = entry.ticket.id }
                        ) {
                            TodayPassHero(
                                ticket = entry.ticket,
                                status = entry.status,
                                onOpen = { onOpenTicket(entry.ticket.id) },
                                onMarkUsed = { viewModel.markUsed(entry.ticket.id) }
                            )
                        }
                        is PassListItem.AlsoToday -> PassSwipeToDelete(
                            onRequestDelete = { pendingDeleteId = entry.ticket.id }
                        ) {
                            AlsoTodayRow(
                                ticket = entry.ticket,
                                status = entry.status,
                                onClick = { onOpenTicket(entry.ticket.id) },
                                onMarkUsed = { viewModel.markUsed(entry.ticket.id) }
                            )
                        }
                        is PassListItem.NextUp -> NextUpCard(
                            ticket = entry.ticket,
                            nowMillis = now,
                            zone = zone,
                            onClick = { onOpenTicket(entry.ticket.id) }
                        )
                        PassListItem.Notices -> Column(verticalArrangement = Arrangement.spacedBy(Space.tight)) {
                            notices.forEach { finding ->
                                PassFindingNotice(
                                    finding = finding,
                                    onOpen = { finding.ticketIds.firstOrNull()?.let(onOpenTicket) }
                                )
                            }
                        }
                        PassListItem.Filters -> PassCategoryChips(
                            selected = category,
                            counts = counts,
                            onSelect = { category = it },
                            modifier = Modifier.padding(top = Space.gap)
                        )
                        is PassListItem.Pass -> PassSwipeToDelete(
                            onRequestDelete = { pendingDeleteId = entry.ticket.id }
                        ) {
                            TicketListRow(
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

    pendingDeleteId?.let { deleteId ->
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("Delete pass?") },
            text = { Text("This removes the pass from this phone. You can add it again later.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDeleteId = null
                    viewModel.delete(deleteId)
                }) { Text("Delete", color = Expense) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) { Text("Cancel") }
            }
        )
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

    reviewDraft?.let { review ->
        TicketReviewDialog(
            draft = review.read.draft,
            extraction = review.read.extraction,
            initialKind = review.kind,
            loadPreview = { viewModel.preview(review.read, maxWidth = 720) },
            onCancel = {
                AppMetrics.ticketImport(review.source, TicketImportResult.Cancelled)
                viewModel.discardDocument(review.read)
                reviewDraft = null
            },
            onSave = { edited, kind ->
                reviewDraft = null
                scope.launch {
                    try {
                        val id = viewModel.saveImport(edited, kind, review.read)
                        if (id == null) {
                            AppMetrics.ticketImport(review.source, TicketImportResult.Duplicate)
                            error = "That ticket is already saved, or has no usable code."
                        } else {
                            AppMetrics.ticketImport(review.source, TicketImportResult.Saved)
                            addMode = null
                            pendingDocumentKind = TicketDocumentKind.AUTO
                            onOpenTicket(id)
                        }
                    } catch (t: Throwable) {
                        if (t is CancellationException) throw t
                        AppMetrics.ticketImportFailure(review.source, t)
                        error = t.message?.takeIf { it.isNotBlank() } ?: "Couldn’t save that ticket."
                    }
                }
            }
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
            title = "Ticket SMS",
            body = "Paste the SMS your SGR booking or event ticket came in.",
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
            .background(Canvas)
            .clickable(onClick = onClick)
            .padding(10.dp)
            .semantics { contentDescription = "Add ticket SMS" }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "NBO",
                color = Ink,
                fontFamily = GeometricSansFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
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
                fontSize = 20.sp
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Accent)
        ) {
            Text(
                text = "SGR",
                color = OnAccent,
                fontFamily = GeometricSansFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.sp,
                letterSpacing = 1.sp,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 10.dp, bottom = 4.dp)
            )
            NotchTicketPerforation(
                notchColor = Canvas,
                dashColor = Color.White.copy(alpha = 0.35f),
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
    Column(
        modifier = Modifier.clip(RoundedCornerShape(6.dp)),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        cells.chunked(5).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                row.forEach { on ->
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(RoundedCornerShape(1.5.dp))
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

private sealed interface PassListItem {
    val key: String

    data class Section(val title: String) : PassListItem {
        override val key: String = "section-$title"
    }

    /** The next pass on today's calendar — the one card that matters at the gate. */
    data class TodayHero(val ticket: Ticket, val status: TodayStatus) : PassListItem {
        override val key: String = "today-${ticket.id}"
    }

    data class AlsoToday(val ticket: Ticket, val status: TodayStatus) : PassListItem {
        override val key: String = "also-${ticket.id}"
    }

    /** Nothing on today: the next dated pass, in place of the old summary strip. */
    data class NextUp(val ticket: Ticket) : PassListItem {
        override val key: String = "next-${ticket.id}"
    }

    data object Notices : PassListItem {
        override val key: String = "notices"
    }

    data object Filters : PassListItem {
        override val key: String = "filters"
    }

    data class Pass(val ticket: Ticket) : PassListItem {
        override val key: String = "pass-${ticket.id}"
    }
}

/**
 * Today (never filtered) → Next when nothing is on today → notices → filters →
 * Coming up → Used. Filters only narrow the lists below them, and only show when
 * there is more than one kind of pass to choose between.
 */
private fun buildPassList(
    tickets: List<Ticket>,
    category: PassCategory?,
    zone: ZoneId,
    nowMillis: Long,
    hasNotices: Boolean
): List<PassListItem> {
    // Same order as the live notification, so both lead with the same pass.
    val todays = TodayStages.onToday(tickets, nowMillis, zone)
    // The ticket card is for a pass still ahead or on; an ended one becomes a plain row.
    val hero = todays.firstOrNull { it.second.stage != TodayStage.FINISHED }
    val items = mutableListOf<PassListItem>()
    if (todays.isNotEmpty()) {
        items += PassListItem.Section("Today")
        hero?.let { items += PassListItem.TodayHero(it.first, it.second) }
        todays.filter { it !== hero }.forEach { items += PassListItem.AlsoToday(it.first, it.second) }
    }
    val todayIds = todays.map { it.first.id }.toSet()
    val rest = tickets.filter { it.id !in todayIds }
    if (todays.isEmpty()) {
        rest.filter { t ->
            val start = t.occasionMillis
            start != null && start > nowMillis && t.effectiveStatus(nowMillis, zone) == TicketStatus.ACTIVE
        }.minByOrNull { it.occasionMillis ?: Long.MAX_VALUE }?.let { items += PassListItem.NextUp(it) }
    }
    if (hasNotices) items += PassListItem.Notices
    val kinds = tickets.map { it.passCategory() }.toSet()
    if (kinds.size > 1) items += PassListItem.Filters
    val pool = rest.filter { category == null || it.passCategory() == category }
    val upcoming = pool
        .filter { it.effectiveStatus(nowMillis, zone) != TicketStatus.USED }
        .sortedWith(
            compareBy<Ticket>(
                { it.effectiveStatus(nowMillis, zone) == TicketStatus.PAST_DUE },
                { it.occasionMillis == null },
                { it.occasionMillis ?: Long.MAX_VALUE }
            )
        )
    val used = pool
        .filter { it.effectiveStatus(nowMillis, zone) == TicketStatus.USED }
        .sortedByDescending { it.occasionMillis ?: it.createdAtMillis }
    if (upcoming.isNotEmpty()) {
        items += PassListItem.Section("Coming up")
        upcoming.forEach { items += PassListItem.Pass(it) }
    }
    if (used.isNotEmpty()) {
        items += PassListItem.Section("Used")
        used.forEach { items += PassListItem.Pass(it) }
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
private fun PassSectionHeader(title: String) {
    Text(
        text = title,
        style = HomeType.section,
        color = Ink,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Space.section, bottom = Space.tight)
            .semantics { heading() }
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PassSwipeToDelete(
    onRequestDelete: () -> Unit,
    content: @Composable () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { distance -> distance * 0.28f },
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> {
                    onRequestDelete()
                    false // spring back; confirm dialog owns the delete
                }
                SwipeToDismissBoxValue.StartToEnd -> false
                SwipeToDismissBoxValue.Settled -> true
            }
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        modifier = Modifier.fillMaxWidth(),
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            val reveal = dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart ||
                dismissState.targetValue == SwipeToDismissBoxValue.EndToStart
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (reveal) Expense else Color.Transparent)
                    .padding(horizontal = 22.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (reveal) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "Delete",
                        tint = OnAccent
                    )
                }
            }
        }
    ) {
        content()
    }
}

/** The only extra colours on Passes: amber for "this needs doing", and its dark-mode pair. */
private object PassTone {
    private val dark: Boolean
        @Composable @ReadOnlyComposable get() = LocalLipaColors.current == LipaPalette.Dark
    val warnInk: Color
        @Composable @ReadOnlyComposable get() = if (dark) Color(0xFFF5C26B) else Color(0xFF92400E)
    /** Amber that reads on the navy ticket in both themes. */
    val OnTicketWarn = Color(0xFFF5C26B)
}

private val PassRowShape = RoundedCornerShape(16.dp)

/**
 * Today's pass as a ticket: the navy body carries when and where, a notched tear line,
 * then a stub holding the one action. Same notch-and-dash language as the pass screens.
 */
@Composable
private fun TodayPassHero(
    ticket: Ticket,
    status: TodayStatus,
    onOpen: () -> Unit,
    onMarkUsed: () -> Unit
) {
    val stage = status.stage
    val schedule = formatEventSchedule(ticket.occasionMillis, ticket.notes)
    val venue = ticket.venue?.takeIf { it.isNotBlank() }
    val seat = ticket.seatOrTier?.takeIf { it.isNotBlank() }
    val faint = OnAccent.copy(alpha = 0.72f)
    val action = when (stage) {
        TodayStage.NEEDS_BOARDING_PASS -> "Add boarding pass"
        TodayStage.CODE_PENDING -> "Add entry code"
        else -> "Show ticket"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Accent)
            .clickable(onClickLabel = action, onClick = onOpen)
    ) {
        Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(status.label, style = HomeType.label, color = faint, modifier = Modifier.weight(1f))
                Text(ticket.passCategory().label, style = HomeType.caption, color = faint)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = schedule.time ?: "Today",
                    color = OnAccent,
                    fontFamily = GeometricSansFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 40.sp,
                    lineHeight = 42.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = status.relative,
                    style = HomeType.rowTitle,
                    // Within the hour the countdown is the one thing worth a second colour.
                    color = if (stage == TodayStage.SOON) PassTone.OnTicketWarn else faint,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = ticket.title,
                color = OnAccent,
                fontFamily = GeometricSansFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 19.sp,
                lineHeight = 23.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (venue != null || seat != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Row {
                    if (venue != null) {
                        Text(venue, style = HomeType.caption, color = faint, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                    }
                    // Seat pins a person to a spot: covered while the screen is recorded.
                    if (seat != null) {
                        Text(
                            text = if (venue != null) ", $seat" else seat,
                            style = HomeType.caption,
                            color = faint,
                            maxLines = 1,
                            modifier = Modifier.recordingPrivacyCover(pinsExactSeat(seat))
                        )
                    }
                }
            }
            if (stage.isBlocked) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = if (stage == TodayStage.NEEDS_BOARDING_PASS) {
                        "Add your boarding pass before you reach the station."
                    } else {
                        "The entry code hasn't arrived yet. It usually comes by SMS before the day."
                    },
                    style = HomeType.caption,
                    color = PassTone.OnTicketWarn
                )
            }
        }
        TicketTear()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 12.dp, top = 6.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = action,
                style = HomeType.rowTitle,
                color = OnAccent,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            if (stage == TodayStage.LIVE) {
                TextButton(onClick = onMarkUsed) {
                    Text("Mark as used", style = HomeType.label, color = faint)
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = OnAccent,
                modifier = Modifier.padding(vertical = 10.dp)
            )
        }
    }
}

/** Notches cut from the card's sides with a dashed line between them, as on a paper ticket. */
@Composable
private fun TicketTear() {
    val notch = Canvas
    val dash = OnAccent.copy(alpha = 0.35f)
    androidx.compose.foundation.Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(20.dp)
    ) {
        val r = size.height / 2f
        drawCircle(notch, radius = r, center = androidx.compose.ui.geometry.Offset(0f, r))
        drawCircle(notch, radius = r, center = androidx.compose.ui.geometry.Offset(size.width, r))
        drawLine(
            color = dash,
            start = androidx.compose.ui.geometry.Offset(r + 8.dp.toPx(), r),
            end = androidx.compose.ui.geometry.Offset(size.width - r - 8.dp.toPx(), r),
            strokeWidth = 1.5.dp.toPx(),
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
        )
    }
}

/** Day and month (or time) as the row's lead: the content is the visual, not an icon. */
@Composable
private fun LeadBlock(top: String, main: String, muted: Boolean = false) {
    Column(
        modifier = Modifier
            .width(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (muted) Canvas else SoftBlue)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(top, style = HomeType.label, color = if (muted) Mute else Accent, maxLines = 1)
        Text(
            text = main,
            color = if (muted) Mute else Ink,
            fontFamily = GeometricSansFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            lineHeight = 22.sp,
            maxLines = 1
        )
    }
}

private val MONTH = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)
private val WEEKDAY = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)
private val LONG_WEEKDAY = DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH)
private val CLOCK_MAIN = DateTimeFormatter.ofPattern("h:mm", Locale.ENGLISH)
private val CLOCK_HALF = DateTimeFormatter.ofPattern("a", Locale.ENGLISH)

/** A plain, flat row: hairline edge, no shadow, no badge unless something needs doing. */
@Composable
private fun PassRowFrame(
    muted: Boolean = false,
    onClick: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .fadeWhenUsed(muted)
            .clip(PassRowShape)
            .background(CardWhite)
            .border(1.dp, Hairline, PassRowShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/** Other passes today, and today's pass once it has ended. */
@Composable
private fun AlsoTodayRow(
    ticket: Ticket,
    status: TodayStatus,
    onClick: () -> Unit,
    onMarkUsed: () -> Unit
) {
    val start = Instant.ofEpochMilli(status.startMillis).atZone(ZoneId.systemDefault())
    val venue = ticket.venue?.takeIf { it.isNotBlank() }
    PassRowFrame(onClick = onClick) {
        LeadBlock(top = start.format(CLOCK_HALF), main = start.format(CLOCK_MAIN))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(ticket.title, style = HomeType.rowTitle, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = listOfNotNull("${status.label} ${status.relative}".trim(), venue).joinToString(", "),
                style = HomeType.caption,
                color = if (status.stage.isBlocked) PassTone.warnInk else Mute,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (status.stage == TodayStage.FINISHED) {
            TextButton(onClick = onMarkUsed) {
                Text("Mark as used", style = HomeType.label, color = Accent, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Nothing on today: the next dated pass and how far off it is. */
@Composable
private fun NextUpCard(
    ticket: Ticket,
    nowMillis: Long,
    zone: ZoneId,
    onClick: () -> Unit
) {
    val start = ticket.occasionMillis ?: return
    val day = Instant.ofEpochMilli(start).atZone(zone)
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    val days = java.time.temporal.ChronoUnit.DAYS.between(today, day.toLocalDate())
    val time = formatEventSchedule(start, ticket.notes).time
    val whenText = if (days <= 1L) {
        listOfNotNull("Tomorrow", time).joinToString(", ")
    } else {
        listOfNotNull(day.format(LONG_WEEKDAY) + (time?.let { " $it" } ?: ""), "in $days days").joinToString(", ")
    }
    PassRowFrame(onClick = onClick) {
        LeadBlock(top = day.format(MONTH), main = day.dayOfMonth.toString())
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Next", style = HomeType.label, color = Mute)
            Text(ticket.title, style = HomeType.section, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(whenText, style = HomeType.caption, color = Mute, maxLines = 1)
        }
    }
}

/**
 * A pass in Coming up or Used. Only exceptions get a word on the right; a pass that is
 * simply ready says nothing.
 */
@Composable
private fun TicketListRow(
    ticket: Ticket,
    onClick: () -> Unit
) {
    val status = ticket.effectiveStatus()
    val used = !ticket.isConfirmationOnly && status == TicketStatus.USED
    val note: Pair<String, Color>? = when {
        ticket.isConfirmationOnly -> "Add boarding pass" to PassTone.warnInk
        used -> "Used" to Mute
        EventTicketSms.isPending(ticket.barcodeValue) -> "Code pending" to Mute
        status == TicketStatus.PAST_DUE -> "Past due" to Expense
        else -> null
    }
    val zone = ZoneId.systemDefault()
    val start = ticket.occasionMillis?.let { Instant.ofEpochMilli(it).atZone(zone) }
    val time = formatEventSchedule(ticket.occasionMillis, ticket.notes).time
    val whenText = start?.let { it.format(WEEKDAY) + (time?.let { t -> " $t" } ?: "") }
    val where = ticket.seatOrTier?.takeIf { it.isNotBlank() } ?: ticket.venue?.takeIf { it.isNotBlank() }
    PassRowFrame(muted = used, onClick = onClick) {
        if (start != null) {
            LeadBlock(top = start.format(MONTH), main = start.dayOfMonth.toString(), muted = used)
        } else {
            AccentGlyphBadge(
                icon = if (ticket.isRailTravel) Icons.Outlined.Train else Icons.Outlined.ConfirmationNumber,
                size = 48.dp
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = ticket.title,
                style = HomeType.rowTitle,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val subtitle = listOfNotNull(whenText, where).joinToString(", ").ifBlank { ticket.phaseLabel() }
            Text(
                subtitle,
                style = HomeType.caption,
                color = Mute,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.recordingPrivacyCover(pinsExactSeat(ticket.seatOrTier))
            )
        }
        if (note != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(note.first, style = HomeType.label, color = note.second, fontWeight = FontWeight.SemiBold)
        }
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
        title = { Text("Ticket SMS") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.gap)) {
                Text(
                    "Paste the SMS your ticket came in. SGR: Ref No, train, date, seat. Event: match, gate, seat, ticket no. " +
                        "The SMS has no QR, so add the ticket screenshot or PDF next: it joins this same pass.",
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
                // Known upcoming events: one tap fills the blanks for a code-only ticket.
                com.lipabill.app.data.tickets.EventPresets.upcoming().forEach { preset ->
                    com.lipabill.app.ui.LipaChoiceChip(
                        label = "Quick fill: ${preset.label}",
                        selected = title == preset.title && venue == preset.venue,
                        onClick = {
                            if (title.isBlank()) title = preset.title
                            if (venue.isBlank()) venue = preset.venue
                            if (startsAtMillis == null) startsAtMillis = preset.startsAtMillis
                        }
                    )
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Event / title") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth(),
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

/** Solid brand stage behind event pass previews in the list. */
val EventStage: Color
    @Composable
    @ReadOnlyComposable
    get() = Accent

/** One rule finding over the pass list. Copy never names a code or booking reference. */
@Composable
private fun PassFindingNotice(
    finding: ContextFinding,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SoftBlue)
            .clickable(onClickLabel = "Open the first pass", onClick = onOpen)
            .padding(horizontal = Space.card, vertical = Space.block),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Outlined.Info,
            contentDescription = null,
            tint = Ink,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(Space.block))
        Column(modifier = Modifier.weight(1f)) {
            Text(finding.title, style = HomeType.rowTitle, color = Ink)
            Text(finding.body, style = HomeType.caption, color = Mute)
        }
    }
}

/** A read ticket waiting on the user's review before it's saved. */
private data class ReviewDraft(
    val read: PassDraft,
    val kind: TicketDocumentKind,
    val source: String
)
