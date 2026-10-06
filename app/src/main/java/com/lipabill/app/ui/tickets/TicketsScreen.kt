package com.lipabill.app.ui.tickets

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.Wallet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lipabill.app.data.model.BoardingLeg
import com.lipabill.app.data.model.BoardingPassSnapshot
import com.lipabill.app.data.model.Ticket
import com.lipabill.app.data.model.TicketBarcodeFormat
import com.lipabill.app.data.model.TicketStatus
import com.lipabill.app.OpenedPass
import com.lipabill.app.data.tickets.BookingConfirmationParser
import com.lipabill.app.data.tickets.TicketDocumentKind
import com.lipabill.app.ui.adapt.LocalWindowForm
import com.lipabill.app.ui.permissions.PermissionGuideDialog
import com.lipabill.app.ui.permissions.PermissionLesson
import com.lipabill.app.ui.permissions.permissionGranted
import com.lipabill.app.ui.adapt.WindowWidth
import com.lipabill.app.ui.theme.Canvas
import com.lipabill.app.ui.theme.CardWhite
import com.lipabill.app.ui.theme.Expense
import com.lipabill.app.ui.theme.GeometricSansFamily
import com.lipabill.app.ui.theme.HomeType
import com.lipabill.app.ui.theme.Income
import com.lipabill.app.ui.theme.Ink
import com.lipabill.app.ui.theme.Accent
import com.lipabill.app.ui.theme.RouteBlue
import com.lipabill.app.ui.theme.Mute
import com.lipabill.app.ui.theme.SoftBlue
import com.lipabill.app.ui.theme.Space
import com.lipabill.app.ui.util.formatEventSchedule
import com.lipabill.app.ui.util.formatEventWhen
import com.lipabill.app.viewmodel.TicketDetailViewModel
import com.lipabill.app.viewmodel.TicketsViewModel
import com.lipabill.app.viewmodel.label
import com.lipabill.app.viewmodel.phaseLabel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private enum class AddTicketMode {
    CHOOSER,
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
    // Flight / other travel designs paused — surface events + SGR only for now.
    val visibleTickets = remember(tickets) {
        tickets.filter { !it.expectsBoardingPass || it.isRailTravel }
    }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var addMode by remember { mutableStateOf<AddTicketMode?>(null) }
    var pendingDocumentKind by remember { mutableStateOf(TicketDocumentKind.AUTO) }
    var error by remember { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }

    suspend fun importDocument(uri: Uri, kind: TicketDocumentKind = pendingDocumentKind) {
        if (importing) return
        importing = true
        try {
            val id = viewModel.addFromDocument(context, uri, kind)
            if (id == null) {
                error = "That ticket is already saved, or has no usable code."
            } else {
                addMode = null
                pendingDocumentKind = TicketDocumentKind.AUTO
                onOpenTicket(id)
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
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
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
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
        importDocument(uri, TicketDocumentKind.AUTO)
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
            FloatingActionButton(
                onClick = { addMode = AddTicketMode.CHOOSER },
                containerColor = Accent,
                contentColor = CardWhite
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "Add pass")
            }
        }
    ) { padding ->
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
            val eventCount = visibleTickets.count { it.passCategory() == PassCategory.EVENTS }
            val sgrCount = visibleTickets.count { it.passCategory() == PassCategory.SGR }
            LaunchedEffect(eventCount, sgrCount) {
                if (category == PassCategory.EVENTS && eventCount == 0) category = null
                if (category == PassCategory.SGR && sgrCount == 0) category = null
            }
            val entries = remember(visibleTickets, category, today) {
                buildPassList(visibleTickets, category, zone, today)
            }
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
                PassCategoryChips(
                    selected = category,
                    eventCount = eventCount,
                    sgrCount = sgrCount,
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
                            is PassListItem.Section -> PassSectionLabel(entry.title)
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
    }

    when (addMode) {
        AddTicketMode.CHOOSER -> AddTicketChooserDialog(
            onDismiss = { addMode = null },
            onEventTicket = {
                addMode = null
                launchDocumentPicker(TicketDocumentKind.EVENT)
            },
            onSgrSms = { addMode = AddTicketMode.CONFIRMATION }
        )
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
            TicketDocumentKind.FLIGHT_E_TICKET -> "Reading flight e-ticket…"
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
                        when (pendingDocumentKind) {
                            TicketDocumentKind.FLIGHT_E_TICKET ->
                                "Saving the booking — you’ll add the boarding pass on this ticket."
                            else -> "Creating your ticket."
                        },
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
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(CardWhite)
                .padding(horizontal = Space.page, vertical = Space.section),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Outlined.ConfirmationNumber,
                contentDescription = null,
                tint = Accent,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(SoftBlue)
                    .padding(14.dp)
            )
            Spacer(modifier = Modifier.height(Space.block))
            Text("No passes yet", style = HomeType.section, color = RouteBlue)
            Spacer(modifier = Modifier.height(Space.gap))
            Text(
                text = "Add an event ticket, or paste your SGR booking SMS. More travel types coming soon.",
                style = HomeType.body,
                color = Mute,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(Space.block))
            Button(
                onClick = onEventTicket,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent)
            ) {
                Icon(Icons.Outlined.Wallet, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text("Event ticket")
            }
            Spacer(modifier = Modifier.height(Space.gap))
            OutlinedButton(
                onClick = onSgrSms,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp)
            ) {
                Icon(Icons.Outlined.Sms, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text("SGR booking SMS")
            }
        }
    }
}

private enum class PassCategory(val label: String) {
    EVENTS("Events"),
    SGR("SGR")
}

private fun Ticket.passCategory(): PassCategory =
    if (isRailTravel) PassCategory.SGR else PassCategory.EVENTS

private fun Ticket.isOnDay(zone: ZoneId, day: LocalDate): Boolean {
    val millis = occasionMillis ?: return false
    return Instant.ofEpochMilli(millis).atZone(zone).toLocalDate() == day
}

private sealed interface PassListItem {
    val key: String

    data class Section(val title: String) : PassListItem {
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
        items += PassListItem.Section("Today")
        heroes.forEach { items += PassListItem.TodayHero(it) }
    }
    PassCategory.entries.forEach { cat ->
        if (category != null && category != cat) return@forEach
        val rows = rest.filter { it.passCategory() == cat }.sortedWith(order)
        if (rows.isEmpty()) return@forEach
        if (category == null || heroes.isNotEmpty()) {
            items += PassListItem.Section(cat.label)
        }
        rows.forEach { items += PassListItem.Pass(it) }
    }
    return items
}

@Composable
private fun PassCategoryChips(
    selected: PassCategory?,
    eventCount: Int,
    sgrCount: Int,
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
            val count = if (cat == PassCategory.EVENTS) eventCount else sgrCount
            if (count > 0) {
                PassChip(
                    cat.label,
                    selected = selected == cat,
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
    onClick: () -> Unit
) {
    Text(
        text = label,
        style = HomeType.label,
        color = if (selected) CardWhite else Ink,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) Accent else CardWhite)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

@Composable
private fun PassSectionLabel(title: String) {
    Text(
        text = title,
        style = HomeType.label,
        color = Mute,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 2.dp)
    )
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(24.dp), spotColor = Color.Black.copy(alpha = 0.10f))
            .clip(RoundedCornerShape(24.dp))
            .background(CardWhite)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(Accent)
        )
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "TODAY",
                    style = HomeType.label,
                    color = Accent,
                    letterSpacing = 1.2.sp
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = ticket.passCategory().label,
                    style = HomeType.caption,
                    color = Mute
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = time ?: ticket.title,
                color = Ink,
                fontFamily = GeometricSansFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = if (time != null) 32.sp else 24.sp,
                lineHeight = if (time != null) 36.sp else 28.sp,
                letterSpacing = (-0.4).sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (time != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = ticket.title,
                    color = Ink,
                    fontFamily = GeometricSansFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    lineHeight = 22.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (place.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = place,
                    style = HomeType.caption,
                    color = Mute,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
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
            .background(if (used) Color(0xFFE4E2DC) else CardWhite)
            .clickable(onClick = onClick)
            .padding(horizontal = Space.card, vertical = Space.cardH),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (ticket.isConfirmationOnly) Icons.Outlined.Sms else Icons.Outlined.ConfirmationNumber,
            contentDescription = null,
            tint = if (used) Mute else Accent,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (used) Color(0xFFD5D2CB) else SoftBlue)
                .padding(10.dp)
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
            Text(subtitle, style = HomeType.caption, color = Mute, maxLines = 2)
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
private fun AddTicketChooserDialog(
    onDismiss: () -> Unit,
    onEventTicket: () -> Unit,
    onSgrSms: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add pass") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.gap)) {
                Text(
                    text = "Event = one upload. SGR = booking SMS first, then boarding pass on the ticket.",
                    style = HomeType.body,
                    color = Mute
                )
                Button(
                    onClick = onEventTicket,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent)
                ) {
                    Icon(Icons.Outlined.Wallet, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Event ticket (photo / PDF / .pkpass)")
                }
                OutlinedButton(
                    onClick = onSgrSms,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(Icons.Outlined.Sms, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("SGR booking SMS")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
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
                    modifier = Modifier.fillMaxWidth(),
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
                    modifier = Modifier.fillMaxWidth(),
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
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = venue,
                    onValueChange = { venue = it },
                    label = { Text("Venue (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
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
    var showCameraGuide by remember { mutableStateOf(false) }
    var attachLeg by remember { mutableStateOf<BoardingLeg?>(null) }
    var showBoardingQrDialog by remember { mutableStateOf(false) }

    // Gate scan: only boost brightness / keep-awake for active tickets.
    val boostScanBrightness = ticket?.effectiveStatus() == TicketStatus.ACTIVE
    DisposableEffect(boostScanBrightness) {
        if (!boostScanBrightness) {
            return@DisposableEffect onDispose { }
        }
        val win = findActivity(view.context)?.window
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
            if (!ok) error = "Couldn’t attach that boarding pass (duplicate QR or unreadable)."
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            error = t.message?.takeIf { it.isNotBlank() }
                ?: "Couldn’t read that boarding pass."
        } finally {
            attaching = false
            attachLeg = null
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showAttachScanner = true
        } else {
            error = "Camera permission is needed to scan a boarding pass."
            attachLeg = null
        }
    }

    val documentPicker = rememberLauncherForActivityResult(
        OpenTicketDocumentContract()
    ) { uri ->
        if (uri == null) {
            attachLeg = null
            return@rememberLauncherForActivityResult
        }
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        scope.launch { attachUri(uri) }
    }

    fun launchAttachPicker(leg: BoardingLeg? = null) {
        attachLeg = leg
        documentPicker.launch(Unit)
    }

    fun launchAttachScan(leg: BoardingLeg? = null) {
        attachLeg = leg
        val granted = context.permissionGranted(Manifest.permission.CAMERA)
        if (granted) {
            showAttachScanner = true
        } else {
            showCameraGuide = true
        }
    }

    if (showCameraGuide) {
        PermissionGuideDialog(
            lesson = PermissionLesson.Camera,
            onAllow = {
                showCameraGuide = false
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            },
            onNotNow = {
                showCameraGuide = false
                attachLeg = null
            }
        )
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
                            overflow = TextOverflow.Ellipsis
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
                    title = { Text(current?.title ?: "Ticket", style = HomeType.greeting) },
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

        val isReturnTrip = current.isReturnTrip
        val isRail = current.isRailTravel
        val outboundSnap = current.boardingSnapshot(BoardingLeg.OUTBOUND)
        val returnSnap = current.boardingSnapshot(BoardingLeg.RETURN)

        // Flight boarding keeps PDF417; SGR uses QR instead
        val outboundPdf417 = rememberBoardingPdf417(if (isRail) null else outboundSnap)
        val returnPdf417 = rememberBoardingPdf417(if (isRail) null else returnSnap)
        val outboundQr = rememberBoardingQr(if (isRail) outboundSnap else null)
        val returnQr = rememberBoardingQr(if (isRail) returnSnap else null)

        // Event QR (non-travel)
        val eventGateCode = current.gateBarcodeValue.takeIf { !current.isTravelTicket }
        val hasEventPayload = !eventGateCode.isNullOrBlank() &&
            !BookingConfirmationParser.isConfirmationPlaceholder(eventGateCode) &&
            !eventGateCode.startsWith("ETKT:", ignoreCase = true) &&
            !eventGateCode.startsWith("BOARDING:", ignoreCase = true)
        val gateQr = if (hasEventPayload) {
            rememberTicketQrBitmap(
                value = eventGateCode!!,
                format = current.gateBarcodeFormat,
                sizePx = 480
            )
        } else {
            null
        }

        val displayStatus = current.effectiveStatus()
        val statusColor = when (displayStatus) {
            TicketStatus.ACTIVE -> Income
            TicketStatus.USED -> Mute
            TicketStatus.PAST_DUE -> Expense
        }
        val isUsed = displayStatus == TicketStatus.USED
        val bookingPdf417 = rememberHorizontalPdf417ImageBitmap(
            bcbpPayload = when {
                current.isTravelTicket && !isRail && current.barcodeValue.isNotBlank() ->
                    current.barcodeValue
                isRail -> {
                    val ref = current.orderId?.takeIf { it.isNotBlank() }
                        ?: current.barcodeValue
                            .removePrefix("REF:")
                            .removePrefix("ref:")
                            .trim()
                            .takeIf { it.isNotBlank() }
                    ref?.let { "REF:$it" }
                }
                else -> null
            },
            allowPlaceholder = true
        )
        val bookingQrPayload = when {
            !current.isTravelTicket || !isRail -> null
            !current.orderId.isNullOrBlank() -> current.orderId
            else -> current.barcodeValue
                .removePrefix("REF:")
                .removePrefix("ref:")
                .trim()
                .takeIf { it.isNotBlank() }
        }
        // Unused for rail booking stub (PDF417 only); kept for non-rail paths below
        val bookingQr = rememberTicketQrBitmap(
            value = bookingQrPayload.orEmpty(),
            format = TicketBarcodeFormat.QR_CODE,
            sizePx = 480
        ).takeIf { !bookingQrPayload.isNullOrBlank() && !isRail }

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
                        fontWeight = FontWeight.Bold
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
                                    modifier = Modifier.size(240.dp)
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
                    // Travel: booking first, boarding pass below
                    SectionLabel("Booking ticket")
                    Spacer(modifier = Modifier.height(Space.gap))
                    BookingTicketStyleCard(
                        ticket = current,
                        pdf417 = bookingPdf417,
                        qrBitmap = bookingQr,
                        modifier = Modifier.fadeWhenUsed(isUsed),
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

                    Spacer(modifier = Modifier.height(Space.block))
                    TravelBoardingLegBlock(
                        sectionLabel = if (isReturnTrip) "Outbound boarding" else "Boarding pass",
                        headline = if (isReturnTrip) "Outbound Boarding Pass" else "Boarding Pass",
                        snapshot = outboundSnap,
                        pdf417 = outboundPdf417,
                        qrBitmap = outboundQr,
                        orderId = current.orderId,
                        isUsed = isUsed,
                        emptyHint = if (isReturnTrip) {
                            "Import the outbound boarding pass when you have it."
                        } else {
                            "Import the boarding pass photo/PDF or scan the QR when you have it."
                        },
                        onImport = { launchAttachPicker(BoardingLeg.OUTBOUND) },
                        onScan = { launchAttachScan(BoardingLeg.OUTBOUND) }
                    )

                    if (isReturnTrip) {
                        Spacer(modifier = Modifier.height(Space.block))
                        TravelBoardingLegBlock(
                            sectionLabel = "Return boarding",
                            headline = "Return Boarding Pass",
                            snapshot = returnSnap,
                            pdf417 = returnPdf417,
                            qrBitmap = returnQr,
                            orderId = current.orderId,
                            isUsed = isUsed,
                            emptyHint = "Import the return boarding pass when you have it.",
                            onImport = { launchAttachPicker(BoardingLeg.RETURN) },
                            onScan = { launchAttachScan(BoardingLeg.RETURN) }
                        )
                    }
                } else {
                    // Event: confirmed-style card (mirrors flight booking chrome)
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

            if (!isUsed && current.canMarkUsedFromEventDay() && (
                    !current.isTravelTicket ||
                        current.hasBoardingPass ||
                        (current.isReturnTrip && current.hasReturnBoardingPass)
                    )
            ) {
                if (isEventDetail) {
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
                } else {
                    OutlinedButton(
                        onClick = { viewModel.markUsed() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(26.dp)
                    ) { Text("Mark as used") }
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
private fun rememberBoardingPdf417(snapshot: BoardingPassSnapshot?): ImageBitmap? {
    val code = snapshot?.barcodeValue?.takeIf { it.isNotBlank() } ?: return null
    if (BookingConfirmationParser.isConfirmationPlaceholder(code)) return null
    if (code.startsWith("ETKT:", ignoreCase = true)) return null
    if (code.startsWith("BOARDING:", ignoreCase = true)) return null
    val format = snapshot.barcodeFormat
    val looksFlight = format == TicketBarcodeFormat.AZTEC ||
        format == TicketBarcodeFormat.PDF_417 ||
        snapshot.notes.orEmpty().contains("Boarding pass", ignoreCase = true) ||
        code.startsWith("M1") ||
        code.startsWith("M2")
    if (!looksFlight) return null
    return rememberHorizontalPdf417ImageBitmap(code)
}

@Composable
private fun rememberBoardingQr(snapshot: BoardingPassSnapshot?): ImageBitmap? {
    val code = snapshot?.barcodeValue?.takeIf { it.isNotBlank() } ?: return null
    if (BookingConfirmationParser.isConfirmationPlaceholder(code)) return null
    if (code.startsWith("ETKT:", ignoreCase = true)) return null
    if (code.startsWith("BOARDING:", ignoreCase = true)) return null
    return rememberTicketQrBitmap(
        value = code,
        format = TicketBarcodeFormat.QR_CODE,
        sizePx = 480
    )
}

@Composable
private fun TravelBoardingLegBlock(
    sectionLabel: String,
    headline: String,
    snapshot: BoardingPassSnapshot?,
    pdf417: ImageBitmap?,
    qrBitmap: ImageBitmap?,
    orderId: String?,
    isUsed: Boolean,
    emptyHint: String,
    onImport: () -> Unit,
    onScan: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().fadeWhenUsed(isUsed)) {
    SectionLabel(sectionLabel)
    Spacer(modifier = Modifier.height(Space.gap))
    val barcode = qrBitmap ?: pdf417
    when {
        snapshot != null && barcode != null -> {
            BoardingPassStyleCard(
                snapshot = snapshot,
                pdf417 = barcode,
                orderId = orderId,
                headline = headline,
                barcodeIsQr = qrBitmap != null,
                onReplace = if (!isUsed) onImport else null
            )
        }
        snapshot != null -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(6.dp, RoundedCornerShape(24.dp), clip = false)
                    .clip(RoundedCornerShape(24.dp))
                    .background(CardWhite)
                    .padding(Space.page),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = snapshot.title ?: headline,
                    style = HomeType.rowTitle,
                    color = Ink,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(Space.block))
                Text(
                    "Couldn’t draw a code from that import — re-import a clearer stub photo or scan the QR.",
                    style = HomeType.caption,
                    color = Mute,
                    textAlign = TextAlign.Center
                )
                if (!isUsed) {
                    Spacer(modifier = Modifier.height(Space.block))
                    TextButton(onClick = onImport) { Text("Replace boarding pass") }
                    Spacer(modifier = Modifier.height(Space.gap))
                    OutlinedButton(
                        onClick = onScan,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Icon(Icons.Outlined.QrCodeScanner, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Scan boarding QR")
                    }
                }
            }
        }
        !isUsed -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(6.dp, RoundedCornerShape(24.dp), clip = false)
                    .clip(RoundedCornerShape(24.dp))
                    .background(CardWhite)
                    .padding(Space.page),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Outlined.ConfirmationNumber,
                    contentDescription = null,
                    tint = Mute,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(SoftBlue.copy(alpha = 0.5f))
                        .padding(12.dp)
                )
                Spacer(modifier = Modifier.height(Space.block))
                Text("Not attached yet", style = HomeType.rowTitle, color = Ink)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    emptyHint,
                    style = HomeType.caption,
                    color = Mute,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(Space.block))
                Button(
                    onClick = onImport,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent)
                ) {
                    Icon(Icons.Outlined.Wallet, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Import boarding pass")
                }
                Spacer(modifier = Modifier.height(Space.gap))
                OutlinedButton(
                    onClick = onScan,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(Icons.Outlined.QrCodeScanner, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Scan boarding QR")
                }
            }
        }
        else -> {
            Text(
                "No boarding pass on this ticket yet.",
                style = HomeType.body,
                color = Mute,
                textAlign = TextAlign.Center
            )
        }
    }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = HomeType.label,
        color = Mute,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        textAlign = TextAlign.Start
    )
}

private fun pickEventDateTime(
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

private fun findActivity(context: Context): android.app.Activity? {
    var ctx = context
    while (ctx is android.content.ContextWrapper) {
        if (ctx is android.app.Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
