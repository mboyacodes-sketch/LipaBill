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
            // "Keep" on a finished card folds it into Also today for the rest of the visit.
            var kept by remember { mutableStateOf(emptySet<Long>()) }
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
            val entries = remember(visibleTickets, category, minute, kept, notices) {
                buildPassList(visibleTickets, category, zone, now, kept, hasNotices = notices.isNotEmpty())
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
                        is PassListItem.Section -> PassSectionHeader(
                            title = entry.title,
                            trailing = entry.trailing
                        )
                        is PassListItem.TodayHero -> PassSwipeToDelete(
                            onRequestDelete = { pendingDeleteId = entry.ticket.id }
                        ) {
                            TodayPassHero(
                                ticket = entry.ticket,
                                status = entry.status,
                                onOpen = { onOpenTicket(entry.ticket.id) },
                                onMarkUsed = { viewModel.markUsed(entry.ticket.id) },
                                onKeep = { kept = kept + entry.ticket.id }
                            )
                        }
                        is PassListItem.AlsoToday -> PassSwipeToDelete(
                            onRequestDelete = { pendingDeleteId = entry.ticket.id }
                        ) {
                            AlsoTodayRow(
                                ticket = entry.ticket,
                                status = entry.status,
                                onClick = { onOpenTicket(entry.ticket.id) }
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

    data class Section(val title: String, val trailing: String?) : PassListItem {
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

private val TODAY_HEADER = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

/**
 * Today (never filtered) → Next up when nothing is on today → notices → filters →
 * Upcoming → Used. Filters only narrow the lists below them.
 */
private fun buildPassList(
    tickets: List<Ticket>,
    category: PassCategory?,
    zone: ZoneId,
    nowMillis: Long,
    kept: Set<Long>,
    hasNotices: Boolean
): List<PassListItem> {
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    val todays = tickets
        .filter { it.isOnDay(zone, today) && it.effectiveStatus(nowMillis, zone) != TicketStatus.USED }
        .mapNotNull { t -> TodayStages.stageOf(t, nowMillis, zone)?.let { t to it } }
        // Unfinished first, soonest start first; finished ones sink.
        .sortedWith(compareBy({ it.second.stage == TodayStage.FINISHED }, { it.second.startMillis }))
    val hero = todays.firstOrNull { it.first.id !in kept }
    val items = mutableListOf<PassListItem>()
    if (todays.isNotEmpty()) {
        val trailing = if (todays.size == 1) "1 pass" else "${todays.size} passes"
        items += PassListItem.Section("Today · ${today.format(TODAY_HEADER)}", trailing)
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
    items += PassListItem.Filters
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
        items += PassListItem.Section("Upcoming · ${upcoming.size}", null)
        upcoming.forEach { items += PassListItem.Pass(it) }
    }
    if (used.isNotEmpty()) {
        items += PassListItem.Section("Used · ${used.size}", null)
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
                    "${cat.label} · ${counts[cat]}",
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
    trailing: String?
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Space.block, bottom = Space.tight)
            .semantics(mergeDescendants = true) { heading() }
    ) {
        Text(
            text = title.uppercase(Locale.ENGLISH),
            style = HomeType.label,
            color = Mute,
            letterSpacing = 1.2.sp,
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) {
            Text(text = trailing, style = HomeType.caption, color = Mute)
        }
    }
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

/** Warning and live tones for the Today card and badges; each has a dark-mode pair. */
private object PassTone {
    private val dark: Boolean
        @Composable @ReadOnlyComposable get() = LocalLipaColors.current == LipaPalette.Dark
    val warnFill: Color
        @Composable @ReadOnlyComposable get() = if (dark) Color(0xFF3A2A10) else Color(0xFFFFF7E6)
    val warnEdge: Color
        @Composable @ReadOnlyComposable get() = if (dark) Color(0xFF8A5A14) else Color(0xFFF5C26B)
    val warnInk: Color
        @Composable @ReadOnlyComposable get() = if (dark) Color(0xFFF5C26B) else Color(0xFF92400E)
    val warnPill: Color
        @Composable @ReadOnlyComposable get() = if (dark) Color(0xFF4A3415) else Color(0xFFFDE7C2)
    val onWarnButton: Color
        @Composable @ReadOnlyComposable get() = if (dark) Color(0xFF1F2937) else Color.White
    val liveFill: Color
        @Composable @ReadOnlyComposable get() = if (dark) Color(0xFF1E3A2A) else Color(0xFFDCEFE3)
    val liveInk: Color
        @Composable @ReadOnlyComposable get() = if (dark) Color(0xFF86EFAC) else Color(0xFF14532D)
    val liveDot: Color
        @Composable @ReadOnlyComposable get() = if (dark) Color(0xFF4ADE80) else Color(0xFF15803D)
    val Soon = Color(0xFFF5B544)
    val OnSoon = Color(0xFF1F2937)
    /** The gate button stays white on navy in both themes. */
    val GateButton = Color.White
    val OnGateButton = Color(0xFF111827)
}

/**
 * The one card that matters today. Its look and buttons follow [TodayStage]:
 * navy while it's ahead or on, white once finished, amber when it can't be used yet.
 */
@Composable
private fun TodayPassHero(
    ticket: Ticket,
    status: TodayStatus,
    onOpen: () -> Unit,
    onMarkUsed: () -> Unit,
    onKeep: () -> Unit
) {
    val stage = status.stage
    val navy = stage != TodayStage.FINISHED && !stage.isBlocked
    val shape = RoundedCornerShape(24.dp)
    val fill = when {
        stage.isBlocked -> PassTone.warnFill
        stage == TodayStage.FINISHED -> CardWhite
        else -> Accent
    }
    val edge = when {
        stage == TodayStage.SOON -> PassTone.Soon
        stage.isBlocked -> PassTone.warnEdge
        stage == TodayStage.FINISHED -> Hairline
        else -> null
    }
    val ink = if (navy) OnAccent else Ink
    val soft = if (navy) OnAccent.copy(alpha = 0.82f) else Mute
    val schedule = formatEventSchedule(ticket.occasionMillis, ticket.notes)
    val venue = ticket.venue?.takeIf { it.isNotBlank() }
    val seat = ticket.seatOrTier?.takeIf { it.isNotBlank() }
    val subtitle = when (stage) {
        TodayStage.LIVE -> listOfNotNull(schedule.time?.let { "Started $it" }, venue).joinToString(" · ")
        TodayStage.FINISHED -> "Did you go? Mark it used to tidy Passes."
        TodayStage.NEEDS_BOARDING_PASS -> "Booking saved. Add the boarding pass before you reach the gate."
        TodayStage.CODE_PENDING -> "Saved from the SMS. Add the entry code when it arrives."
        else -> venue.orEmpty()
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(if (navy) 14.dp else 0.dp, shape, spotColor = Accent.copy(alpha = 0.3f))
            .clip(shape)
            .background(fill)
            .then(if (edge != null) Modifier.border(if (stage == TodayStage.SOON) 3.dp else 1.dp, edge, shape) else Modifier)
            .clickable(onClickLabel = "Open pass", onClick = onOpen)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StagePill(stage, status.pill)
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = ticket.passCategory().icon,
                contentDescription = null,
                tint = soft,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(ticket.passCategory().label, style = HomeType.caption, color = soft)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val showsClock = stage == TodayStage.LATER || stage == TodayStage.SOON || stage.isBlocked
            if (showsClock && schedule.time != null) {
                Text(
                    text = schedule.time,
                    color = ink,
                    fontFamily = GeometricSansFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 40.sp,
                    lineHeight = 44.sp
                )
            }
            Text(
                text = ticket.title,
                color = ink,
                fontFamily = GeometricSansFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                lineHeight = 24.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = HomeType.caption,
                        color = soft,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                // Seat pins a person to a spot: covered while the screen is recorded.
                if (seat != null && (stage == TodayStage.LATER || stage == TodayStage.SOON)) {
                    Text(
                        text = if (subtitle.isNotBlank()) " · $seat" else seat,
                        style = HomeType.caption,
                        color = soft,
                        maxLines = 1,
                        modifier = Modifier.recordingPrivacyCover(pinsExactSeat(seat))
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            when (stage) {
                TodayStage.LATER -> HeroButton(
                    "Show code", onOpen, PassTone.GateButton, PassTone.OnGateButton,
                    modifier = Modifier.weight(1f)
                )
                TodayStage.SOON -> HeroButton(
                    "Show code", onOpen, PassTone.GateButton, PassTone.OnGateButton,
                    tall = true, modifier = Modifier.weight(1f)
                )
                TodayStage.LIVE -> {
                    HeroButton(
                        "Show code", onOpen, PassTone.GateButton, PassTone.OnGateButton,
                        modifier = Modifier.weight(1f)
                    )
                    HeroButton("Mark used", onMarkUsed, Color.Transparent, OnAccent, border = OnAccent.copy(alpha = 0.4f))
                }
                TodayStage.FINISHED -> {
                    HeroButton("Mark used", onMarkUsed, Accent, OnAccent, modifier = Modifier.weight(1f))
                    HeroButton("Keep", onKeep, Color.Transparent, Ink, border = Hairline)
                }
                TodayStage.NEEDS_BOARDING_PASS, TodayStage.CODE_PENDING -> {
                    HeroButton(
                        if (stage == TodayStage.NEEDS_BOARDING_PASS) "Add boarding pass" else "Add code",
                        onOpen, PassTone.warnInk, PassTone.onWarnButton,
                        modifier = Modifier.weight(1f)
                    )
                    HeroButton("Open", onOpen, Color.Transparent, PassTone.warnInk, border = PassTone.warnEdge)
                }
            }
        }
    }
}

@Composable
private fun StagePill(stage: TodayStage, label: String) {
    val (bg, fg) = when (stage) {
        TodayStage.LATER -> OnAccent.copy(alpha = 0.16f) to OnAccent
        TodayStage.SOON -> PassTone.Soon to PassTone.OnSoon
        TodayStage.LIVE -> PassTone.liveFill to PassTone.liveInk
        TodayStage.FINISHED -> Canvas to Ink
        TodayStage.NEEDS_BOARDING_PASS, TodayStage.CODE_PENDING -> PassTone.warnPill to PassTone.warnInk
    }
    val dot = when (stage) {
        TodayStage.SOON -> PassTone.OnSoon
        TodayStage.LIVE -> PassTone.liveDot
        else -> null
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        if (dot != null) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dot)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        if (stage.isBlocked) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(text = label, style = HomeType.label, color = fg, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun HeroButton(
    label: String,
    onClick: () -> Unit,
    fill: Color,
    content: Color,
    modifier: Modifier = Modifier,
    border: Color? = null,
    tall: Boolean = false
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .height(if (tall) 52.dp else 48.dp)
            .clip(shape)
            .background(fill)
            .then(if (border != null) Modifier.border(1.dp, border, shape) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = HomeType.rowTitle,
            color = content,
            fontWeight = if (tall) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

/** Second and later passes today: one compact row each under the hero. */
@Composable
private fun AlsoTodayRow(
    ticket: Ticket,
    status: TodayStatus,
    onClick: () -> Unit
) {
    val time = formatEventSchedule(ticket.occasionMillis, ticket.notes).time
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(18.dp), spotColor = Color.Black.copy(alpha = 0.06f))
            .clip(RoundedCornerShape(18.dp))
            .background(CardWhite)
            .clickable(onClick = onClick)
            .padding(horizontal = Space.card, vertical = Space.cardH),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AccentGlyphBadge(icon = ticket.passCategory().icon, size = 40.dp)
        Spacer(modifier = Modifier.size(Space.block))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (status.stage.isBlocked) "Also today · ${status.pill}" else "Also today",
                style = HomeType.caption,
                color = if (status.stage.isBlocked) PassTone.warnInk else Mute
            )
            Text(
                text = listOfNotNull(ticket.title, time).joinToString(" · "),
                style = HomeType.rowTitle,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Mute
        )
    }
}

/** Nothing on today: the next dated pass, with how far off it is. */
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
    val when_ = if (days <= 1L) "tomorrow" else "in $days days"
    val schedule = formatEventSchedule(start, ticket.notes)
    val detail = listOfNotNull(
        day.format(DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)) +
            (schedule.time?.let { " $it" } ?: ""),
        ticket.venue?.takeIf { it.isNotBlank() }
    ).joinToString(" · ")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CardWhite)
            .border(1.dp, Hairline, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .width(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(SoftBlue)
                .padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = day.format(DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)).uppercase(Locale.ENGLISH),
                style = HomeType.label,
                color = Accent,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = day.dayOfMonth.toString(),
                color = Accent,
                fontFamily = GeometricSansFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                lineHeight = 24.sp
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "NEXT UP · ${when_.uppercase(Locale.ENGLISH)}",
                style = HomeType.label,
                color = Mute,
                letterSpacing = 1.sp
            )
            Text(
                text = ticket.title,
                style = HomeType.section,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (detail.isNotBlank()) {
                Text(detail, style = HomeType.caption, color = Mute, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Mute
        )
    }
}

@Composable
private fun TicketListRow(
    ticket: Ticket,
    onClick: () -> Unit
) {
    val status = ticket.effectiveStatus()
    val used = !ticket.isConfirmationOnly && status == TicketStatus.USED
    val pending = EventTicketSms.isPending(ticket.barcodeValue)
    val (badge, badgeFill, badgeInk) = when {
        ticket.isConfirmationOnly -> Triple("Needs pass", PassTone.warnPill, PassTone.warnInk)
        used -> Triple("Used", Mute.copy(alpha = 0.18f), Mute)
        pending -> Triple("Code pending", SoftBlue, Accent)
        status == TicketStatus.PAST_DUE -> Triple("Past due", Expense.copy(alpha = 0.16f), Expense)
        else -> Triple("Ready", Income.copy(alpha = 0.16f), Income)
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
                overflow = TextOverflow.Ellipsis
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
                modifier = Modifier.recordingPrivacyCover(pinsExactSeat(ticket.seatOrTier))
            )
        }
        Text(
            text = badge,
            style = HomeType.label,
            color = badgeInk,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(badgeFill)
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
