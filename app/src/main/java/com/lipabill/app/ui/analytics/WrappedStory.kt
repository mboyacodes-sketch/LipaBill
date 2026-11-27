package com.lipabill.app.ui.analytics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.lipabill.app.R
import com.lipabill.app.data.model.TransactionType
import com.lipabill.app.ui.LipaChoiceChip
import com.lipabill.app.ui.LipaPillButton
import com.lipabill.app.ui.privacy.recordingPrivacyCover
import com.lipabill.app.ui.theme.HomeType
import com.lipabill.app.ui.util.formatKsh
import com.lipabill.app.viewmodel.CounterpartySpend
import com.lipabill.app.viewmodel.WrapCards
import com.lipabill.app.viewmodel.WrapPersonality
import com.lipabill.app.viewmodel.wrapShareText
import java.io.File
import java.time.DayOfWeek
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------------------------------------
// Period wording

private val DayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.UK)
private val DayMonthYear = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK)

private fun WrapCards.isWholeMonth(): Boolean =
    from.dayOfMonth == 1 && to == YearMonth.from(from).atEndOfMonth()

private fun WrapCards.isWholeYear(): Boolean =
    from.dayOfYear == 1 && to.year == from.year && to.monthValue == 12 && to.dayOfMonth == 31

/** Twelve whole months that are not one calendar year. */
private fun WrapCards.isTrailingYear(): Boolean =
    from.dayOfMonth == 1 && to == YearMonth.from(to).atEndOfMonth() &&
        YearMonth.from(from).plusMonths(11) == YearMonth.from(to) && !isWholeYear()

/** "October", "2026", "last 12 months", "2026 so far" or "1 Nov – 31 Oct 2026". */
internal fun WrapCards.periodName(): String = when {
    isWholeMonth() -> from.month.getDisplayName(TextStyle.FULL, Locale.UK)
    isWholeYear() -> from.year.toString()
    isTrailingYear() -> "last 12 months"
    from.dayOfYear == 1 && to.year == from.year -> "${from.year} so far"
    else -> "${from.format(DayMonth)} – ${to.format(DayMonthYear)}"
}

private fun WrapCards.periodLong(): String =
    if (isWholeMonth()) "${periodName()} ${from.year}" else periodName()

private fun WrapCards.priorName(): String =
    if (isWholeMonth()) from.minusMonths(1).month.getDisplayName(TextStyle.FULL, Locale.UK) else "the period before"

// ---------------------------------------------------------------------------------------------
// Money wording

private const val MASK = "KSh ••••"

private class Money(val show: Boolean) {
    fun full(v: Double) = if (show) formatKsh(v) else MASK
    /** KSh 950, KSh 176K, KSh 1.7M. */
    fun short(v: Double): String = if (!show) MASK else when {
        abs(v) >= 1_000_000 -> "KSh " + String.format(Locale.US, "%.1fM", v / 1_000_000).replace(".0M", "M")
        abs(v) >= 10_000 -> "KSh ${(v / 1000).roundToInt()}K"
        else -> formatKsh(v)
    }
}

// ---------------------------------------------------------------------------------------------
// Card kinds and looks

internal enum class WrapCardKind(val title: String) {
    INTRO("Intro"),
    NUMBERS("By the numbers"),
    FLOW("The flow"),
    FEES("Quiet cost"),
    FULIZA("Fuliza"),
    PEOPLE("Your people"),
    SPOTS("Top spots"),
    RHYTHM("Rhythm"),
    PERSONALITY("Personality")
}

/** Cards worth showing for [c], in story order. Empty ones are skipped. */
internal fun wrapKinds(c: WrapCards): List<WrapCardKind> = buildList {
    add(WrapCardKind.INTRO)
    add(WrapCardKind.NUMBERS)
    if (c.moneyIn > 0.0 || c.moneyOut > 0.0) add(WrapCardKind.FLOW)
    if (c.fees > 0.0) add(WrapCardKind.FEES)
    if (c.fuliza != null) add(WrapCardKind.FULIZA)
    if (c.topPeople.isNotEmpty()) add(WrapCardKind.PEOPLE)
    if (c.topSpots.isNotEmpty()) add(WrapCardKind.SPOTS)
    if (c.bars.isNotEmpty()) add(WrapCardKind.RHYTHM)
    add(WrapCardKind.PERSONALITY)
}

private val Lime = Color(0xFFB8F04A)
private val DeepGreen = Color(0xFF0F3B24)
private val Forest = Color(0xFF1E6A3E)
private val Ink = Color(0xFF111511)
private val Night = Color(0xFF0A0B0A)
private val Sun = Color(0xFFF2CF3B)
private val Coral = Color(0xFFE9735C)
private val Plum = Color(0xFF1C0D2B)

private class WrapStyle(
    val background: Brush,
    /** Main text colour; always white or near-black for contrast. */
    val ink: Color,
    val accent: Color,
    val motif: DrawScope.() -> Unit
) {
    val muted: Color get() = ink.copy(alpha = 0.78f)
}

private fun styleOf(kind: WrapCardKind): WrapStyle = when (kind) {
    WrapCardKind.INTRO -> WrapStyle(Brush.linearGradient(listOf(Forest, DeepGreen)), Color.White, Lime) { asterisk() }
    WrapCardKind.NUMBERS -> WrapStyle(Brush.verticalGradient(listOf(Color(0xFF2F7A4C), DeepGreen)), Color.White, Lime) { asterisk(small = true) }
    WrapCardKind.FLOW -> WrapStyle(Brush.verticalGradient(listOf(Night, Night)), Color.White, Lime) { brushSplit() }
    WrapCardKind.FEES -> WrapStyle(Brush.verticalGradient(listOf(Night, Color(0xFF151515))), Color.White, Lime) { streaks() }
    WrapCardKind.FULIZA -> WrapStyle(Brush.verticalGradient(listOf(Sun, Sun)), Ink, Ink) { blobAndFlower() }
    WrapCardKind.PEOPLE -> WrapStyle(Brush.verticalGradient(listOf(Color(0xFF3E7D57), Color(0xFF1F4D33))), Color.White, Color.White) { leaves(0.07f) }
    WrapCardKind.SPOTS -> WrapStyle(Brush.verticalGradient(listOf(Color(0xFF16301F), Color(0xFF0C1A11))), Color.White, Lime) { leaves(0.10f) }
    WrapCardKind.RHYTHM -> WrapStyle(Brush.verticalGradient(listOf(Coral, Color(0xFFDD6650))), Ink, Ink) { }
    WrapCardKind.PERSONALITY -> WrapStyle(Brush.verticalGradient(listOf(Plum, Color(0xFF12081C))), Color.White, Lime) { spray() }
}

// Motifs — vector only, deterministic so shared PNGs match what is on screen.

private fun DrawScope.asterisk(small: Boolean = false) {
    val c = if (small) Offset(size.width * 0.3f, size.height * 0.62f) else Offset(size.width * 0.32f, size.height * 0.3f)
    val len = size.width * if (small) 0.45f else 0.7f
    val thick = len * 0.22f
    listOf(0f, 60f, 120f).forEach { angle ->
        rotate(angle + 15f, c) {
            drawRoundRect(
                brush = Brush.radialGradient(listOf(Lime.copy(alpha = 0.35f), Lime.copy(alpha = 0.05f)), c, len / 1.6f),
                topLeft = Offset(c.x - len / 2, c.y - thick / 2),
                size = Size(len, thick),
                cornerRadius = CornerRadius(thick / 2)
            )
        }
    }
}

private fun DrawScope.brushSplit() {
    val edge = size.height * 0.52f
    drawRect(Lime, size = Size(size.width, edge + size.height * 0.08f))
    val jag = Path().apply {
        moveTo(0f, edge + size.height * 0.1f)
        val steps = 14
        for (i in 0..steps) {
            val x = size.width * i / steps
            val y = edge + size.height * (if (i % 2 == 0) 0.07f else -0.04f) * (1f - i / (steps * 1.6f))
            lineTo(x, y)
        }
        lineTo(size.width, size.height)
        lineTo(0f, size.height)
        close()
    }
    drawPath(jag, Night)
}

private fun DrawScope.streaks() {
    listOf(0.08f, 0.2f, 0.33f, 0.47f, 0.61f, 0.74f, 0.86f).forEachIndexed { i, fx ->
        val x = size.width * fx
        drawLine(Color.White.copy(alpha = 0.12f + 0.05f * (i % 3)), Offset(x, size.height * 0.18f), Offset(x, size.height * (0.38f + 0.06f * (i % 2))), 2f)
    }
    repeat(3) { i ->
        val y = size.height * (0.12f + i * 0.03f)
        val p = Path().apply {
            moveTo(0f, y)
            quadraticTo(size.width * 0.55f, y + size.height * 0.12f, size.width * 0.85f, y - size.height * 0.02f)
        }
        drawPath(p, Lime, style = Stroke(width = 5f - i, cap = StrokeCap.Round))
    }
}

private fun DrawScope.blobAndFlower() {
    val blob = Offset(size.width * 0.3f, size.height * 0.22f)
    val r = size.width * 0.15f
    listOf(Offset(-r * 0.7f, 0f), Offset(r * 0.7f, 0f), Offset(0f, -r * 0.7f), Offset(0f, r * 0.7f)).forEach {
        drawCircle(Coral, r, blob + it)
    }
    val flower = Offset(size.width * 0.76f, size.height * 0.45f)
    val pr = size.width * 0.06f
    repeat(6) { i ->
        rotate(i * 60f, flower) { drawCircle(Color(0xFF45B04A), pr, Offset(flower.x, flower.y - pr)) }
    }
    drawCircle(Color(0xFF45B04A), pr, flower)
}

private fun DrawScope.leaves(alpha: Float) {
    val leaf = Size(size.width * 0.28f, size.width * 0.1f)
    for (row in 0..6) for (col in 0..3) {
        val c = Offset(size.width * (col / 3f), size.height * (row / 6f))
        rotate(((row * 37 + col * 53) % 120 - 60).toFloat(), c) {
            drawOval(Color.White.copy(alpha = alpha), Offset(c.x - leaf.width / 2, c.y - leaf.height / 2), leaf)
        }
    }
}

private fun DrawScope.spray() {
    fun stroke(from: Offset, ctrl: Offset, to: Offset) {
        val p = Path().apply { moveTo(from.x, from.y); quadraticTo(ctrl.x, ctrl.y, to.x, to.y) }
        drawPath(p, Lime.copy(alpha = 0.18f), style = Stroke(width = size.width * 0.2f, cap = StrokeCap.Round))
        drawPath(p, Lime.copy(alpha = 0.55f), style = Stroke(width = size.width * 0.07f, cap = StrokeCap.Round))
    }
    val w = size.width
    val h = size.height
    stroke(Offset(w * 0.25f, h * 0.05f), Offset(w * 0.4f, h * 0.35f), Offset(w * 0.55f, h * 0.45f))
    stroke(Offset(w * 0.95f, h * 0.1f), Offset(w * 0.7f, h * 0.3f), Offset(w * 0.55f, h * 0.45f))
    stroke(Offset(w * 0.55f, h * 0.45f), Offset(w * 0.8f, h * 0.6f), Offset(w * 1.05f, h * 0.55f))
}

// ---------------------------------------------------------------------------------------------
// Teaser

/** Insights teaser: opens the story cards. */
@Composable
internal fun WrappedTeaser(cards: WrapCards, onOpen: () -> Unit, eyebrow: String = "WRAPPED") {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Brush.linearGradient(listOf(Forest, DeepGreen)))
            .padding(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(eyebrow, style = HomeType.caption, color = Lime, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        Text("Your ${cards.periodName()}, wrapped", style = HomeType.section, color = Color.White, fontWeight = FontWeight.Bold)
        Text(
            "Money in vs out, your people, top spots and your money personality, as cards you can share.",
            style = HomeType.caption,
            color = Color.White.copy(alpha = 0.8f)
        )
        LipaPillButton(label = "Open", onClick = onOpen, filled = false)
    }
}

// ---------------------------------------------------------------------------------------------
// Dialog

/**
 * Story cards, then a share screen. Amounts and names start hidden so nothing private
 * leaves the phone by accident.
 */
@Composable
internal fun WrappedDialog(cards: WrapCards, onDismiss: () -> Unit) {
    var showAmounts by remember { mutableStateOf(false) }
    var hideNames by remember { mutableStateOf(true) }
    val kinds = remember(cards) { wrapKinds(cards) }
    val hubPage = kinds.size
    val pager = rememberPagerState(pageCount = { kinds.size + 1 })
    val layers = remember { mutableStateMapOf<Int, GraphicsLayer>() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val onHub = pager.currentPage == hubPage
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF070A08))
                .safeDrawingPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (0..hubPage).forEach { i ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(if (i <= pager.currentPage) Color.White else Color.White.copy(alpha = 0.3f))
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (onHub) "Share" else kinds[pager.currentPage].title,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close", tint = Color.White)
                }
            }
            HorizontalPager(state = pager, modifier = Modifier.weight(1f), pageSpacing = 16.dp) { index ->
                if (index == hubPage) {
                    ShareHub(kinds, cards, showAmounts, hideNames)
                } else {
                    val layer = rememberGraphicsLayer()
                    layers[index] = layer
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        WrapCard(
                            kind = kinds[index],
                            cards = cards,
                            showAmounts = showAmounts,
                            hideNames = hideNames,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(9f / 14f)
                                .drawWithContent {
                                    layer.record { this@drawWithContent.drawContent() }
                                    drawLayer(layer)
                                }
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LipaChoiceChip(
                    label = if (showAmounts) "Amounts shown" else "Amounts hidden",
                    selected = showAmounts,
                    onClick = { showAmounts = !showAmounts }
                )
                LipaChoiceChip(
                    label = if (hideNames) "Names hidden" else "Names shown",
                    selected = !hideNames,
                    onClick = { hideNames = !hideNames }
                )
                Spacer(modifier = Modifier.weight(1f))
                if (!onHub) {
                    IconButton(onClick = {
                        val layer = layers[pager.currentPage] ?: return@IconButton
                        scope.launch { shareCard(context, layer) }
                    }) {
                        Icon(Icons.Outlined.Share, contentDescription = "Share this card", tint = Color.White)
                    }
                }
            }
        }
    }
}

/** Full-size layout of a story card; previews scale this down so shared PNGs stay sharp. */
private val CardWidth = 360.dp
private val CardHeight = 560.dp

/** Last story page: every card in a carousel, with share, save and copy. */
@Composable
internal fun ShareHub(kinds: List<WrapCardKind>, cards: WrapCards, showAmounts: Boolean, hideNames: Boolean) {
    val carousel = rememberPagerState(pageCount = { kinds.size })
    val layers = remember { mutableStateMapOf<Int, GraphicsLayer>() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val personality = personalityText(cards).first
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Your wrap · swipe for more cards", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val cardHeight = minOf(maxHeight, maxWidth * 0.72f * 14f / 9f)
            val cardWidth = cardHeight * 9f / 14f
            val scale = cardWidth / CardWidth
            HorizontalPager(
                state = carousel,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = ((maxWidth - cardWidth) / 2).coerceAtLeast(0.dp)),
                pageSpacing = 12.dp
            ) { i ->
                val layer = rememberGraphicsLayer()
                layers[i] = layer
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Box(modifier = Modifier.size(cardWidth, cardHeight)) {
                        WrapCard(
                            kind = kinds[i],
                            cards = cards,
                            showAmounts = showAmounts,
                            hideNames = hideNames,
                            modifier = Modifier
                                .requiredSize(CardWidth, CardHeight)
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                }
                                .drawWithContent {
                                    layer.record { this@drawWithContent.drawContent() }
                                    drawLayer(layer)
                                }
                        )
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                kinds.indices.forEach { i ->
                    Box(
                        modifier = Modifier
                            .size(width = if (i == carousel.currentPage) 14.dp else 6.dp, height = 6.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = if (i == carousel.currentPage) 1f else 0.35f))
                    )
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                "${carousel.currentPage + 1} of ${kinds.size} · ${kinds[carousel.currentPage].title}",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 13.sp
            )
        }
        Text(
            "Share the card you're on, or save it and post it to your status.",
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 14.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LipaPillButton(
                label = "Share card",
                onClick = { layers[carousel.currentPage]?.let { scope.launch { shareCard(context, it) } } },
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                LipaPillButton(
                    label = "Save card",
                    onClick = {
                        layers[carousel.currentPage]?.let { layer ->
                            scope.launch {
                                val bitmap = layer.toImageBitmap().asAndroidBitmap()
                                val saved = withContext(Dispatchers.IO) { saveToGallery(context, bitmap) }
                                Toast.makeText(
                                    context,
                                    if (saved) "Saved to Pictures/LipaBill" else "Couldn't save the card",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    },
                    filled = false,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
            }
        }
        LipaPillButton(
            label = "Copy text",
            onClick = {
                val text = wrapShareText(cards, cards.periodLong(), personality, showAmounts, hideNames)
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("LipaBill Wrapped", text))
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                }
            },
            filled = false
        )
    }
}

private suspend fun shareCard(context: Context, layer: GraphicsLayer) {
    val bitmap = layer.toImageBitmap().asAndroidBitmap()
    val file = withContext(Dispatchers.IO) {
        File(context.cacheDir, "wrapped").apply { mkdirs() }
            .resolve("lipabill-wrapped.png")
            .also { out -> out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }
    shareImage(context, file)
}

private fun shareImage(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.wrapped", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("image/png")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, "Share your Wrapped card").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Writes [bitmap] to Pictures/LipaBill. No storage permission needed on Android 10+. */
@RequiresApi(Build.VERSION_CODES.Q)
private fun saveToGallery(context: Context, bitmap: Bitmap): Boolean {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "lipabill-wrapped-${System.currentTimeMillis()}.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/LipaBill")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
    return try {
        val written = resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } ?: false
        if (!written) error("PNG not written")
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        true
    } catch (_: Exception) {
        resolver.delete(uri, null, null)
        false
    }
}

// ---------------------------------------------------------------------------------------------
// Cards

/** One story card. Also used by render tests. */
@Composable
internal fun WrapCard(
    kind: WrapCardKind,
    cards: WrapCards,
    showAmounts: Boolean,
    modifier: Modifier = Modifier,
    /** Swap payee names for "Person 1", "Shop 1" so the card can be posted. */
    hideNames: Boolean = false
) {
    val style = styleOf(kind)
    val money = Money(showAmounts)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(style.background)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) { style.motif(this) }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val eyebrowInk = if (kind == WrapCardKind.FLOW) Ink else style.ink
            Text(
                kind.title.takeUnless { kind == WrapCardKind.INTRO } ?: "LipaBill Wrapped · ${cards.periodLong()}",
                color = eyebrowInk.copy(alpha = 0.85f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            when (kind) {
                WrapCardKind.INTRO -> IntroBody(cards, style)
                WrapCardKind.NUMBERS -> NumbersBody(cards, style)
                WrapCardKind.FLOW -> FlowBody(cards, style, money)
                WrapCardKind.FEES -> FeesBody(cards, style, money)
                WrapCardKind.FULIZA -> FulizaBody(cards, style, money)
                WrapCardKind.PEOPLE -> RankedBody(
                    "Most of your send money went to", cards.topPeople, "transfer", style, money, if (hideNames) "Person" else null
                )
                WrapCardKind.SPOTS -> RankedBody(
                    "Where you paid most", cards.topSpots, "payment", style, money, if (hideNames) "Shop" else null
                )
                WrapCardKind.RHYTHM -> RhythmBody(cards, style, money, hideNames)
                WrapCardKind.PERSONALITY -> PersonalityBody(cards, style)
            }
            Text("LipaBill", color = style.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun Big(text: String, color: Color, size: TextUnit = 52.sp, modifier: Modifier = Modifier) =
    Text(text, color = color, fontSize = size, fontWeight = FontWeight.Black, lineHeight = size * 1.08f, modifier = modifier)

@Composable
private fun ColumnScope.IntroBody(c: WrapCards, s: WrapStyle) {
    Spacer(modifier = Modifier.weight(1f))
    val name = c.periodName()
    Big(if (name.endsWith("so far")) "Here's your $name." else "This was your $name with money.", s.ink, 40.sp)
    Text(
        if (c.partial) "Counting from ${c.to.minusDays(c.coveredDays - 1L).format(DayMonth)}, when LipaBill's records start. Swipe through."
        else "Swipe through to see how it went.",
        color = s.muted,
        fontSize = 15.sp
    )
}

private fun typeTiles(c: WrapCards): List<Pair<String, Int>> {
    fun n(vararg t: TransactionType) = t.sumOf { c.typeCounts[it] ?: 0 }
    return listOf(
        "Tills" to n(TransactionType.BUY_GOODS, TransactionType.POCHI),
        "Sent" to n(TransactionType.SENT),
        "Paybills" to n(TransactionType.PAYBILL),
        "Airtime" to n(TransactionType.AIRTIME),
        "Withdrawals" to n(TransactionType.WITHDRAW),
        "Received" to n(TransactionType.RECEIVED, TransactionType.DEPOSIT),
        "Charged" to c.chargedTxnCount,
        "Fuliza" to n(TransactionType.FULIZA)
    ).filter { it.second > 0 }
}

@Composable
private fun ColumnScope.NumbersBody(c: WrapCards, s: WrapStyle) {
    Big("%,d".format(c.txnCount), s.ink)
    Text(
        "transactions. That's ${String.format(Locale.US, "%.1f", c.perDay)} a day, on ${c.activeDays} of ${c.coveredDays} days.",
        color = s.muted,
        fontSize = 15.sp
    )
    Spacer(modifier = Modifier.weight(1f))
    typeTiles(c).chunked(2).forEach { pair ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            pair.forEach { (label, count) ->
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(label, color = s.muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text("$count", color = s.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
        }
    }
    c.busiestDay?.let { (day, count) ->
        Text("Busiest day: $count transactions on ${day.format(DayMonth)}.", color = s.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ColumnScope.FlowBody(c: WrapCards, s: WrapStyle, money: Money) {
    Spacer(modifier = Modifier.weight(1f))
    Text("Money out", color = s.muted, fontSize = 14.sp)
    Big(money.short(c.moneyOut), Lime, 46.sp)
    Text("Money in", color = s.muted, fontSize = 14.sp)
    Big(money.short(c.moneyIn), s.ink, 40.sp)
    val net = c.net
    Text(
        buildString {
            append(
                when {
                    net >= 1.0 -> "You kept ${money.full(net)}."
                    net <= -1.0 -> "${money.full(-net)} more went out than came in."
                    else -> "In and out came out even."
                }
            )
            c.outChange?.let {
                append(
                    when {
                        it < 0 -> " ${-it}% less out than ${c.priorName()}."
                        it > 0 -> " $it% more out than ${c.priorName()}."
                        else -> ""
                    }
                )
            }
            c.heaviestOutDay?.let { (day, amount) -> append(" Heaviest day: ${day.format(DayMonth)}, ${money.full(amount)} out.") }
        },
        color = s.muted,
        fontSize = 14.sp
    )
}

@Composable
private fun ColumnScope.FeesBody(c: WrapCards, s: WrapStyle, money: Money) {
    Spacer(modifier = Modifier.weight(1f))
    Big("Transaction charges took", s.ink, 30.sp)
    Big(money.full(c.fees), s.ink, 46.sp)
    Text(
        buildString {
            append("across ${c.chargedTxnCount} charged transactions.")
            c.feeShareOfOut?.let { append(" That's ${String.format(Locale.US, "%.1f", it * 100)}% of everything that went out.") }
            c.feesBefore?.let {
                val diff = c.fees - it
                when {
                    diff < -1.0 -> append(" ${money.full(-diff)} less than ${c.priorName()}.")
                    diff > 1.0 -> append(" ${money.full(diff)} more than ${c.priorName()}.")
                }
            }
        },
        color = s.muted,
        fontSize = 14.sp
    )
}

@Composable
private fun ColumnScope.FulizaBody(c: WrapCards, s: WrapStyle, money: Money) {
    val f = c.fuliza ?: return
    Spacer(modifier = Modifier.weight(1f))
    Big("You Fuliza'd", s.ink, 34.sp)
    Big("${f.count}×", s.ink, 60.sp)
    Text(
        "borrowing ${money.full(f.total)} in total. Average top-up: ${money.full(f.average)}.",
        color = s.ink,
        fontSize = 15.sp
    )
}

@Composable
private fun ColumnScope.RankedBody(
    lead: String,
    rows: List<CounterpartySpend>,
    countWord: String,
    s: WrapStyle,
    money: Money,
    /** When set, names become "<placeholder> 1", "<placeholder> 2". */
    placeholder: String?
) {
    val top = rows.firstOrNull() ?: return
    fun nameOf(i: Int, row: CounterpartySpend) = if (placeholder != null) "$placeholder ${i + 1}" else row.name
    Text(lead, color = s.muted, fontSize = 15.sp)
    Big(nameOf(0, top), s.accent, 34.sp, Modifier.recordingPrivacyCover(placeholder == null))
    Text("${top.count} $countWord${if (top.count == 1) "" else "s"} · ${money.full(top.total)}", color = s.muted, fontSize = 14.sp)
    Spacer(modifier = Modifier.weight(1f))
    val max = rows.maxOf { it.total }.coerceAtLeast(1.0)
    rows.forEachIndexed { i, row ->
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}", color = s.muted, fontSize = 13.sp, modifier = Modifier.width(22.dp))
                Text(
                    nameOf(i, row),
                    color = s.ink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).recordingPrivacyCover(placeholder == null)
                )
                Text(money.short(row.total), color = s.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Box(
                modifier = Modifier
                    .padding(start = 22.dp)
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(s.ink.copy(alpha = 0.2f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth((row.total / max).toFloat().coerceIn(0.04f, 1f))
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(s.ink)
                )
            }
        }
    }
}

private fun weekdayPlural(d: DayOfWeek) = d.getDisplayName(TextStyle.FULL, Locale.UK) + "s"

@Composable
private fun ColumnScope.RhythmBody(c: WrapCards, s: WrapStyle, money: Money, hideNames: Boolean) {
    val peak = c.bars.getOrNull(c.peakBar)
    val peakName = peak?.label?.let { if (it.startsWith("W")) "Week ${it.drop(1)}" else it.substringBefore(' ') }
    Big(if (peakName != null) "$peakName hit hardest." else "Your rhythm", s.ink, 32.sp)
    @Composable
    fun Tile(value: String, label: String, modifier: Modifier, namesPayee: Boolean = false) {
        Column(
            modifier = modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.18f))
                .padding(10.dp)
        ) {
            Text(value, color = s.ink, fontSize = 17.sp, fontWeight = FontWeight.Black)
            Text(label, color = s.muted, fontSize = 12.sp, lineHeight = 15.sp, modifier = Modifier.recordingPrivacyCover(namesPayee))
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        c.busiestWeekday?.let { Tile(weekdayPlural(it), "busiest day", Modifier.weight(1f)) }
        if (c.afterMidnight > 0) Tile("${c.afterMidnight}", "payments after midnight", Modifier.weight(1f))
        else Tile("${c.quietDays}", "days nothing went out", Modifier.weight(1f))
    }
    c.biggestPayment?.let {
        if (hideNames) Tile(money.full(it.amount), "biggest single payment", Modifier.fillMaxWidth())
        else Tile(money.full(it.amount), "biggest single payment · ${it.name}", Modifier.fillMaxWidth(), namesPayee = true)
    }
    Spacer(modifier = Modifier.weight(1f))
    Box(modifier = Modifier.fillMaxWidth().height(110.dp)) {
        val max = c.bars.maxOf { it.outflow }.coerceAtLeast(1.0)
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            c.bars.forEachIndexed { i, bar ->
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height((86.dp * (bar.outflow / max).toFloat()).coerceAtLeast(3.dp))
                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                            .background(if (i == c.peakBar) Color.White else Ink)
                    )
                    Text(
                        if (bar.label.startsWith("W")) bar.label else bar.label.take(1),
                        color = s.ink,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.PersonalityBody(c: WrapCards, s: WrapStyle) {
    Spacer(modifier = Modifier.weight(1f))
    val (title, body) = personalityText(c)
    Text("Your money personality", color = s.muted, fontSize = 14.sp)
    Big(title, s.ink, 44.sp)
    Text(body, color = s.muted, fontSize = 15.sp)
}

/** Title and one-liner for the card's money personality. */
@Composable
private fun personalityText(c: WrapCards): Pair<String, String> =
    when (c.personality) {
        WrapPersonality.FULIZA_FREQUENT ->
            stringResource(R.string.wrap_personality_fuliza_frequent) to
                stringResource(R.string.wrap_personality_fuliza_frequent_body, c.fuliza?.count ?: 0)
        WrapPersonality.FAMILY_ANCHOR ->
            stringResource(R.string.wrap_personality_family_anchor) to stringResource(R.string.wrap_personality_family_anchor_body)
        WrapPersonality.FEE_HEAVY ->
            stringResource(R.string.wrap_personality_fee_heavy) to stringResource(R.string.wrap_personality_fee_heavy_body)
        WrapPersonality.QUIET_OPERATOR ->
            stringResource(R.string.wrap_personality_quiet_operator) to stringResource(R.string.wrap_personality_quiet_operator_body)
        WrapPersonality.EVERYDAY_MOVER ->
            stringResource(R.string.wrap_personality_everyday_mover) to stringResource(R.string.wrap_personality_everyday_mover_body)
    }

/**
 * Own subclass so the manifest merger keeps this provider apart from the internal build's
 * OTA FileProvider (same class name would merge the two entries and fail the build).
 */
class WrappedFileProvider : FileProvider()
