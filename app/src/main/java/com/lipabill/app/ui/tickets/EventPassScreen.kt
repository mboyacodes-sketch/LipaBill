package com.lipabill.app.ui.tickets

import com.lipabill.app.data.tickets.EventTicketSms
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.lipabill.app.data.tickets.PassColors
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.lipabill.app.LipaBillApp
import com.lipabill.app.data.model.AppRegion
import com.lipabill.app.data.model.Ticket
import com.lipabill.app.data.model.TicketStatus
import com.lipabill.app.data.model.firstNotesValue
import com.lipabill.app.data.model.notesValue
import com.lipabill.app.ui.components.QrImageShape
import com.lipabill.app.ui.privacy.recordingPrivacyCover
import com.lipabill.app.ui.theme.GeometricSansFamily
import com.lipabill.app.ui.util.formatEventSchedule
import kotlinx.coroutines.delay
import java.util.Locale

// Palette from the "My pass" reference.
val EventPassNight = Color(0xFF130B09)

/** Colours for one event pass: the "My pass" orange by default, or a Wallet pass's own scheme. */
@Immutable
data class EventPassPalette(
    val stageTop: Color,
    val stageMid: Color,
    val stageBottom: Color,
    /** Text and glass buttons on the top of the stage. */
    val onStage: Color,
    val card: Color,
    val ink: Color,
    val label: Color,
    val chip: Color,
    val chipText: Color,
    val rim: Color,
    val glows: List<Color>
) {
    companion object {
        val Default = EventPassPalette(
            stageTop = Color(0xFFE66A3C),
            stageMid = Color(0xFF8E3417),
            stageBottom = EventPassNight,
            onStage = Color.White,
            card = Color(0xFFF5F1EC),
            ink = Color(0xFF1C1714),
            label = Color(0xFF8A817B),
            chip = Color(0xFFE8553A),
            chipText = Color.White,
            rim = Color.White,
            glows = listOf(Color(0xFFFF8A3D), Color(0xFFFFC069), Color(0xFFE8553A), Color(0xFFFF6F91))
        )

        /**
         * Card = pass background, values = foreground, labels = label colour, stage = the
         * background deepening to night. Text falls back to black / white when the pass's
         * own pair is below 3:1 contrast.
         */
        fun from(colors: PassColors?): EventPassPalette {
            val bgArgb = colors?.background ?: return Default
            val bg = Color(bgArgb)
            val fgArgb = colors.foreground
                ?.takeIf { PassColors.contrast(it, bgArgb) >= 3.0 }
                ?: readableOn(bgArgb)
            val labelArgb = colors.label
                ?.takeIf { PassColors.contrast(it, bgArgb) >= 2.2 }
            val ink = Color(fgArgb)
            val label = labelArgb?.let { Color(it) } ?: ink.copy(alpha = 0.68f)
            val lightBg = PassColors.luminance(bgArgb) > 0.45
            val mid = lerp(bg, Color.Black, 0.55f)
            return EventPassPalette(
                stageTop = bg,
                stageMid = mid,
                stageBottom = lerp(bg, Color.Black, 0.9f),
                onStage = if (lightBg) Color(0xFF14110F) else Color.White,
                card = bg,
                ink = ink,
                label = label,
                chip = ink,
                chipText = bg,
                rim = if (PassColors.luminance(bgArgb) > 0.8) lerp(bg, Color.Black, 0.1f) else Color.White,
                glows = listOf(
                    lerp(bg, Color.White, 0.35f),
                    ink,
                    label.copy(alpha = 1f),
                    lerp(bg, ink, 0.5f)
                )
            )
        }

        private fun readableOn(bg: Int): Int =
            if (PassColors.contrast(0xFF000000.toInt(), bg) >= PassColors.contrast(0xFFFFFFFF.toInt(), bg)) {
                0xFF14110F.toInt()
            } else {
                0xFFFFFFFF.toInt()
            }
    }
}

private val LocalPassPalette = staticCompositionLocalOf { EventPassPalette.Default }

private val PassGlow: Color @Composable @ReadOnlyComposable get() = LocalPassPalette.current.stageTop
private val PassEmber: Color @Composable @ReadOnlyComposable get() = LocalPassPalette.current.stageMid
private val PassNight: Color @Composable @ReadOnlyComposable get() = LocalPassPalette.current.stageBottom
private val PassOnStage: Color @Composable @ReadOnlyComposable get() = LocalPassPalette.current.onStage
private val PassCream: Color @Composable @ReadOnlyComposable get() = LocalPassPalette.current.card
private val PassInk: Color @Composable @ReadOnlyComposable get() = LocalPassPalette.current.ink

/**
 * Label on the white primary action. Fixed near-black: the pass's own ink can be white (dark
 * Wallet passes), which vanished on the white button.
 */
private val PrimaryActionInk = Color(0xFF14110F)
private val PassLabel: Color @Composable @ReadOnlyComposable get() = LocalPassPalette.current.label
private val PassChip: Color @Composable @ReadOnlyComposable get() = LocalPassPalette.current.chip
private val PassChipText: Color @Composable @ReadOnlyComposable get() = LocalPassPalette.current.chipText
private val PassRim: Color @Composable @ReadOnlyComposable get() = LocalPassPalette.current.rim

private val CardCorner = 30.dp
private val NotchRadius = 14.dp
private val CardInset = 9.dp

/**
 * Full-screen event pass: warm gradient stage, "You're in, <name>." headline with a
 * countdown, a cream ticket with a poster banner, details grid, perforation and a
 * QR stub, plus pill actions at the bottom.
 */
@Composable
fun EventPassContent(
    ticket: Ticket,
    qrBitmap: ImageBitmap?,
    status: TicketStatus,
    canMarkUsed: Boolean,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onMarkUsed: () -> Unit,
    onChangeDate: (() -> Unit)?,
    onViewOriginal: (() -> Unit)? = null
) {
    val appContext = LocalContext.current.applicationContext
    val home = remember {
        (appContext as? LipaBillApp)?.securePreferences?.appRegion ?: AppRegion.DEFAULT
    }
    val model = remember(ticket, home) { ticket.toEventPassModel(home) }
    val palette = remember(ticket.passColors) { EventPassPalette.from(PassColors.parse(ticket.passColors)) }
    CompositionLocalProvider(LocalPassPalette provides palette) {
        EventPassBody(
            ticket, model, qrBitmap, status, canMarkUsed, contentPadding,
            onBack, onMarkUsed, onChangeDate, onViewOriginal
        )
    }
}

@Composable
private fun EventPassBody(
    ticket: Ticket,
    model: EventPassModel,
    qrBitmap: ImageBitmap?,
    status: TicketStatus,
    canMarkUsed: Boolean,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onMarkUsed: () -> Unit,
    onChangeDate: (() -> Unit)?,
    onViewOriginal: (() -> Unit)?
) {
    val context = LocalContext.current
    val isUsed = status == TicketStatus.USED
    StatusBarIcons(darkIcons = PassOnStage.luminance() < 0.5f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to PassGlow,
                    0.38f to PassEmber,
                    0.72f to PassNight,
                    1f to PassNight
                )
            )
            .padding(contentPadding)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            PassTopBar(
                onBack = onBack,
                onChangeDate = onChangeDate,
                hasDate = ticket.startsAtMillis != null,
                onMarkUsed = if (canMarkUsed) onMarkUsed else null
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(modifier = Modifier.height(18.dp))
                PassHeadline(model = model, status = status, startsAtMillis = ticket.startsAtMillis)
                Spacer(modifier = Modifier.height(22.dp))
                PassCard(model = model, qrBitmap = qrBitmap, isUsed = isUsed)
                Spacer(modifier = Modifier.height(20.dp))
            }
            PassActions(
                canMarkUsed = canMarkUsed,
                onDirections = model.mapsQuery?.let { q -> { openVenueInMapsSearch(context, q) } },
                onMarkUsed = onMarkUsed,
                onChangeDate = onChangeDate,
                hasDate = ticket.startsAtMillis != null,
                onViewOriginal = onViewOriginal
            )
        }
    }
}

/** Status-bar icon tone that reads on the pass stage; restored when the pass closes. */
@Composable
private fun StatusBarIcons(darkIcons: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, darkIcons) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val wasLight = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = darkIcons
        onDispose {
            if (controller != null && wasLight != null) controller.isAppearanceLightStatusBars = wasLight
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun GlassCircle(
    onClick: () -> Unit,
    description: String,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(PassOnStage.copy(alpha = 0.16f))
            .border(1.dp, PassOnStage.copy(alpha = 0.22f), CircleShape)
            .clickable(onClickLabel = description, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
private fun PassTopBar(
    onBack: () -> Unit,
    onChangeDate: (() -> Unit)?,
    hasDate: Boolean,
    onMarkUsed: (() -> Unit)?
) {
    var menu by remember { mutableStateOf(false) }
    val hasMenu = onChangeDate != null || onMarkUsed != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlassCircle(onClick = onBack, description = "Back") {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Back",
                tint = PassOnStage,
                modifier = Modifier.size(26.dp)
            )
        }
        Text(
            text = "My pass",
            color = PassOnStage,
            fontFamily = GeometricSansFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 17.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
        if (hasMenu) {
            Box {
                GlassCircle(onClick = { menu = true }, description = "More options") {
                    Icon(Icons.Filled.MoreHoriz, contentDescription = "More options", tint = PassOnStage)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (onChangeDate != null) {
                        DropdownMenuItem(
                            text = { Text(if (hasDate) "Change date" else "Set date") },
                            onClick = { menu = false; onChangeDate() }
                        )
                    }
                    if (onMarkUsed != null) {
                        DropdownMenuItem(
                            text = { Text("Mark as used") },
                            onClick = { menu = false; onMarkUsed() }
                        )
                    }
                }
            }
        } else {
            Spacer(modifier = Modifier.size(44.dp))
        }
    }
}

@Composable
private fun PassHeadline(model: EventPassModel, status: TicketStatus, startsAtMillis: Long?) {
    val eyebrow = buildList {
        add(
            when (status) {
                TicketStatus.ACTIVE -> "CONFIRMED"
                TicketStatus.USED -> "USED"
                TicketStatus.PAST_DUE -> "ENDED"
            }
        )
        model.orderRef?.let { add("ORDER #${it.uppercase(Locale.US)}") }
    }.joinToString(" · ")
    Text(
        text = eyebrow,
        color = PassOnStage.copy(alpha = 0.72f),
        fontFamily = GeometricSansFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        letterSpacing = 1.6.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.recordingPrivacyCover(model.orderRef != null)
    )
    Spacer(modifier = Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.Bottom) {
        Column(modifier = Modifier.weight(1f)) {
            val lead = if (status == TicketStatus.ACTIVE) "You're in" else "You were in"
            Text(
                text = if (model.firstName != null) "$lead," else lead,
                color = PassOnStage,
                fontFamily = GeometricSansFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 46.sp,
                lineHeight = 48.sp,
                letterSpacing = (-1.4).sp
            )
            Text(
                text = model.firstName?.let { "$it." } ?: "for this one.",
                color = PassOnStage,
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.Normal,
                fontSize = if (model.firstName != null) 48.sp else 34.sp,
                lineHeight = 52.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.recordingPrivacyCover(model.firstName != null)
            )
        }
        if (status == TicketStatus.ACTIVE && startsAtMillis != null) {
            Countdown(startsAtMillis = startsAtMillis)
        }
    }
}

@Composable
private fun Countdown(startsAtMillis: Long) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startsAtMillis) {
        while (true) {
            now = System.currentTimeMillis()
            delay(30_000)
        }
    }
    val left = startsAtMillis - now
    val (label, value) = when {
        left <= 0 -> "Doors" to "Open now"
        left < 86_400_000L -> {
            val h = left / 3_600_000
            val m = (left % 3_600_000) / 60_000
            "Starts in" to String.format(Locale.US, "%02dh %02dm", h, m)
        }
        else -> {
            val d = left / 86_400_000
            val h = (left % 86_400_000) / 3_600_000
            "Starts in" to String.format(Locale.US, "%dd %02dh", d, h)
        }
    }
    Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(bottom = 10.dp)) {
        Text(
            text = label,
            color = PassOnStage.copy(alpha = 0.72f),
            fontFamily = GeometricSansFamily,
            fontSize = 12.sp
        )
        Text(
            text = value,
            color = PassOnStage,
            fontFamily = GeometricSansFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 25.sp
        )
    }
}

@Composable
private fun PassCard(model: EventPassModel, qrBitmap: ImageBitmap?, isUsed: Boolean) {
    var perforationY by remember { mutableStateOf<Float?>(null) }
    val shape = remember(perforationY) { NotchedPassShape(CardCorner, NotchRadius, perforationY) }
    val context = LocalContext.current
    val topInsetPx = with(LocalDensity.current) { CardInset.toPx() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(PassCream)
            .border(5.dp, PassRim, shape)
            .padding(top = CardInset, start = CardInset, end = CardInset)
    ) {
        PassBanner(model = model, isUsed = isUsed)
        Column(modifier = Modifier.padding(horizontal = 18.dp)) {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = model.title,
                color = PassInk,
                fontFamily = GeometricSansFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 23.sp,
                lineHeight = 27.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            model.venue?.let { venue ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = venue,
                    color = PassLabel,
                    fontFamily = GeometricSansFamily,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .then(
                            if (model.mapsQuery != null) {
                                Modifier.clickable(onClickLabel = "Open in Maps") {
                                    openVenueInMapsSearch(context, model.mapsQuery)
                                }
                            } else {
                                Modifier
                            }
                        )
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            PassGrid(model.fields)
        }
        // Perforation band: the shape cuts notches at its centre line.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .onGloballyPositioned { coords ->
                    // Children sit inside the card's top inset; the shape is measured from the card edge.
                    val y = topInsetPx + coords.positionInParent().y + coords.size.height / 2f
                    perforationY = y
                },
            contentAlignment = Alignment.Center
        ) {
            val dash = PassLabel.copy(alpha = 0.55f)
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .padding(horizontal = NotchRadius + 4.dp)
            ) {
                drawLine(
                    color = dash,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 6.dp.toPx()), 0f)
                )
            }
        }
        PassStub(model = model, qrBitmap = qrBitmap, isUsed = isUsed)
    }
}

@Composable
private fun PassBanner(model: EventPassModel, isUsed: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(196.dp)
            .clip(RoundedCornerShape(22.dp))
            .graphicsLayer { alpha = if (isUsed) 0.7f else 1f }
    ) {
        EventSkylineArt(
            skyline = model.skyline,
            startsAtMillis = model.startsAtMillis,
            seed = model.title,
            stageGlows = LocalPassPalette.current.glows,
            modifier = Modifier.fillMaxSize()
        )
        model.chip?.let { chip ->
            Text(
                text = chip.uppercase(Locale.US),
                color = PassChipText,
                fontFamily = GeometricSansFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 1.2.sp,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(14.dp)
                    .clip(RoundedCornerShape(50))
                    .background(PassChip)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
        model.counter?.let { counter ->
            Text(
                text = counter,
                color = Color.White,
                fontFamily = GeometricSansFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(14.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.35f))
                    .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            )
        }
    }
}

@Composable
private fun PassGrid(fields: List<PassField>) {
    if (fields.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        fields.chunked(3).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                for (col in 0 until 3) {
                    val field = row.getOrNull(col)
                    val end = col == 2
                    Column(
                        modifier = Modifier.weight(if (col == 0) 1.1f else 1f),
                        horizontalAlignment = if (end) Alignment.End else Alignment.Start
                    ) {
                        if (field != null) {
                            Text(
                                text = field.label,
                                color = PassLabel,
                                fontFamily = GeometricSansFamily,
                                fontSize = 13.sp,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = field.value,
                                color = PassInk,
                                fontFamily = GeometricSansFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 18.sp,
                                textAlign = if (end) TextAlign.End else TextAlign.Start,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.recordingPrivacyCover(field.private)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PassStub(model: EventPassModel, qrBitmap: ImageBitmap?, isUsed: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 26.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(150.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color.White)
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            if (qrBitmap != null) {
                Image(
                    bitmap = qrBitmap,
                    contentDescription = "Entry code",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(QrImageShape)
                        .recordingPrivacyCover()
                        .graphicsLayer { alpha = if (isUsed) UsedPassAlpha else 1f }
                )
            } else {
                Text(
                    if (model.codePending) "No code yet" else "No code",
                    color = PassLabel,
                    fontFamily = GeometricSansFamily,
                    fontSize = 12.sp
                )
            }
        }
        Spacer(modifier = Modifier.width(18.dp))
        Column(modifier = Modifier.weight(1f)) {
            if (model.codePending && !isUsed) {
                PendingCodeNote(model.ticketLink)
                return@Column
            }
            Text(
                text = when {
                    isUsed -> "Already used"
                    model.gate != null -> "Scan at gate"
                    else -> "Scan at entry"
                },
                color = PassInk,
                fontFamily = GeometricSansFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 21.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (isUsed) {
                    "This pass was marked as used."
                } else {
                    val where = model.gate?.let { "Gate $it" } ?: "the entrance"
                    "Show this code at $where. Brightness up, screenshot welcome."
                },
                color = PassLabel,
                fontFamily = GeometricSansFamily,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
            model.counter?.let { counter ->
                Spacer(modifier = Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(width = 22.dp, height = 7.dp)
                            .clip(RoundedCornerShape(50))
                            .background(PassChip)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(PassLabel.copy(alpha = 0.4f))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Ticket ${counter.replace(" / ", " of ").trimStart('0')}",
                        color = PassLabel,
                        fontFamily = GeometricSansFamily,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun PassActions(
    canMarkUsed: Boolean,
    onDirections: (() -> Unit)?,
    onMarkUsed: () -> Unit,
    onChangeDate: (() -> Unit)?,
    hasDate: Boolean,
    onViewOriginal: (() -> Unit)? = null
) {
    data class Action(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val run: () -> Unit)
    val actions = buildList {
        onDirections?.let { add(Action("Directions", Icons.Outlined.NearMe, it)) }
        if (canMarkUsed) add(Action("Mark as used", Icons.Outlined.Check, onMarkUsed))
        onViewOriginal?.let { add(Action("View original", Icons.Outlined.Description, it)) }
        if (size < 2 && onChangeDate != null) {
            add(Action(if (hasDate) "Change date" else "Set date", Icons.Outlined.Event, onChangeDate))
        }
    }.take(2)
    if (actions.isEmpty()) {
        Spacer(modifier = Modifier.height(16.dp))
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        actions.forEachIndexed { index, action ->
            val primary = index == 0
            Row(
                modifier = Modifier
                    .weight(if (primary && actions.size > 1) 1.5f else 1f)
                    .height(60.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (primary) Color.White else Color.White.copy(alpha = 0.08f))
                    .border(
                        1.dp,
                        if (primary) Color.Transparent else Color.White.copy(alpha = 0.22f),
                        RoundedCornerShape(50)
                    )
                    .clickable(role = Role.Button, onClick = action.run),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    action.icon,
                    contentDescription = null,
                    tint = if (primary) PrimaryActionInk else Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = action.label,
                    color = if (primary) PrimaryActionInk else Color.White,
                    fontFamily = GeometricSansFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    maxLines = 1
                )
            }
        }
    }
}

/** Rounded card with two notches cut through at [notchCenterY] (px from the top). */
private class NotchedPassShape(
    private val corner: Dp,
    private val notchRadius: Dp,
    private val notchCenterY: Float?
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { corner.toPx() }
        val body = Path().apply {
            addRoundRect(
                androidx.compose.ui.geometry.RoundRect(
                    Rect(0f, 0f, size.width, size.height),
                    androidx.compose.ui.geometry.CornerRadius(r, r)
                )
            )
        }
        val y = notchCenterY ?: return Outline.Generic(body)
        val nr = with(density) { notchRadius.toPx() }
        val notches = Path().apply {
            addOval(Rect(center = Offset(0f, y), radius = nr))
            addOval(Rect(center = Offset(size.width, y), radius = nr))
        }
        return Outline.Generic(Path().apply { op(body, notches, PathOperation.Difference) })
    }
}

internal data class PassField(val label: String, val value: String, val private: Boolean = false)

/** SMS-only pass: how to get the QR in, without pretending there is one. */
@Composable
private fun PendingCodeNote(link: String?) {
    val context = LocalContext.current
    Text(
        "QR not added yet",
        color = PassInk,
        fontFamily = GeometricSansFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 19.sp
    )
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        "Import your ticket screenshot or PDF in Passes and it joins this pass.",
        color = PassLabel,
        fontFamily = GeometricSansFamily,
        fontSize = 13.sp,
        lineHeight = 18.sp
    )
    if (link != null) {
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            "Open ticket link",
            color = PassInk,
            fontFamily = GeometricSansFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable(role = androidx.compose.ui.semantics.Role.Button) {
                    runCatching {
                        context.startActivity(
                            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(link))
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
                .padding(vertical = 8.dp)
        )
    }
}

internal data class EventPassModel(
    val title: String,
    val venue: String?,
    val mapsQuery: String?,
    val firstName: String?,
    val orderRef: String?,
    val chip: String?,
    val counter: String?,
    val gate: String?,
    val fields: List<PassField>,
    val skyline: EventSkyline = EventSkyline.CITY,
    val startsAtMillis: Long? = null,
    /** Saved from the ticket SMS: details are in, the QR is not yet. */
    val codePending: Boolean = false,
    /** The issuer's "view your ticket" link from the SMS. */
    val ticketLink: String? = null
)

/** Pulls display fields from ticket columns and `Label: value` notes; empty fields are dropped. */
internal fun Ticket.toEventPassModel(home: AppRegion = AppRegion.DEFAULT): EventPassModel {
    val notes = notes
    val schedule = formatEventSchedule(startsAtMillis, notes)
    val parts = seatOrTier.orEmpty()
        .split("·", "|", ",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    fun piece(vararg prefixes: String): String? = parts.firstOrNull { p ->
        prefixes.any { p.startsWith(it, ignoreCase = true) }
    }?.let { p ->
        prefixes.fold(p) { acc, pre -> acc.replace(Regex("(?i)^$pre\\s*[:#]?\\s*"), "") }.trim()
    }?.takeIf { it.isNotEmpty() }

    val seat = piece("Seat") ?: notes.notesValue("Seat")
        ?: parts.firstOrNull { Regex("""(?i)^\d+[A-Z]?$""").matches(it) }
    val tier = parts.firstOrNull {
        it.contains("VIP", true) || it.contains("Tier", true) || it.contains("General", true) ||
            Regex("""(?i)\bGA\b""").containsMatchIn(it) || it.contains("Premium", true) ||
            it.contains("Standing", true) || it.contains("Early", true) || it.contains("Regular", true)
    }?.replace(Regex("""(?i)^(Tier|Class)\s*"""), "")?.trim()
        ?: notes.firstNotesValue("Tier", "Zone", "Class", "Ticket type", "Type")
    val gate = piece("Gate", "Door", "Entrance") ?: notes.firstNotesValue("Gate", "Door", "Entrance")
    val section = piece("Section", "Block", "Stand") ?: notes.firstNotesValue("Section", "Block", "Stand")
    val row = piece("Row") ?: notes.notesValue("Row")
    val doors = notes.notesValue("Doors")
    val price = notes.firstNotesValue("Paid", "Price", "Amount", "Total", "Fare")
    val attendee = notes.firstNotesValue("Passenger", "Name", "Attendee", "Guest", "Holder")
    val admits = notes.notesValue("Admits")
    val host = notes.firstNotesValue("Organizer", "Host")

    val venueLabel = venue?.takeIf { it.isNotBlank() && !it.contains("→") }
        ?: notes.firstNotesValue("Venue", "Location", "Place")
    val city = notes.firstNotesValue("City", "Area")
    val venueLine = listOfNotNull(venueLabel, city?.takeIf { venueLabel?.contains(it, true) != true })
        .joinToString(" · ").ifBlank { null }

    val counter = notes.firstNotesValue("Ticket", "Admit")?.let { raw ->
        Regex("""(\d+)\s*(?:of|/)\s*(\d+)""").find(raw)?.let { m ->
            String.format(Locale.US, "%02d / %02d", m.groupValues[1].toInt(), m.groupValues[2].toInt())
        }
    }

    val title = title.trim()
        .takeIf { it.isNotBlank() && !it.equals("E-ticket", true) && !it.equals("Scanned ticket", true) }
        ?: "Event"

    val fields = buildList {
        schedule.date?.let { add(PassField("Date", it)) }
        // Start time always shows (kick-off for a match); doors / gates open alongside it.
        schedule.time?.let { add(PassField("Time", it)) }
        doors?.takeIf { it != schedule.time }?.let { add(PassField("Doors", it)) }
        tier?.let { add(PassField("Zone", it)) }
        attendee?.let { add(PassField("Guest", shortName(it), private = true)) }
        gate?.let { add(PassField("Gate", it)) }
        section?.let { add(PassField("Section", it)) }
        row?.let { add(PassField("Row", it, private = true)) }
        seat?.let { add(PassField("Seat", it, private = true)) }
        price?.let { add(PassField("Paid", it)) }
        admits?.let { add(PassField("Admits", it)) }
        host?.let { add(PassField("Host", it)) }
    }

    val ref = orderId?.takeIf { it.isNotBlank() }
        ?: barcodeValue.trim().takeIf { it.length in 4..14 && it.all { ch -> ch.isLetterOrDigit() || ch == '-' } }

    return EventPassModel(
        title = title.take(60),
        venue = venueLine?.take(60),
        mapsQuery = venueLabel?.trim()?.takeIf { it.isNotEmpty() },
        firstName = attendee?.let(::firstName),
        orderRef = ref?.take(16),
        chip = tier?.take(18),
        counter = counter,
        gate = gate?.take(12),
        fields = fields,
        skyline = EventSkyline.forTicket(home, venueLabel, city, venue, notes, title),
        startsAtMillis = startsAtMillis,
        codePending = EventTicketSms.isPending(barcodeValue),
        ticketLink = notes.notesValue(EventTicketSms.LINK_KEY)?.takeIf { it.startsWith("http", ignoreCase = true) }
    )
}

/** "MARA KHAN" → "Mara". */
internal fun firstName(full: String): String? =
    full.trim().split(Regex("\\s+")).firstOrNull()
        ?.takeIf { it.length >= 2 && it.any(Char::isLetter) }
        ?.lowercase(Locale.US)
        ?.replaceFirstChar { it.titlecase(Locale.US) }

/** "MARA KHAN" → "Mara K." */
internal fun shortName(full: String): String {
    val words = full.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val first = firstName(full) ?: return full.trim()
    val last = words.drop(1).lastOrNull()?.firstOrNull()?.uppercaseChar()
    return if (last != null) "$first $last." else first
}

private fun openVenueInMapsSearch(context: Context, venue: String) {
    val query = venue.trim()
    if (query.isEmpty()) return
    val encoded = Uri.encode(query)
    val maps = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$encoded")).apply {
        setPackage("com.google.android.apps.maps")
    }
    try {
        context.startActivity(maps)
    } catch (_: ActivityNotFoundException) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=$encoded"))
            )
        }
    }
}
