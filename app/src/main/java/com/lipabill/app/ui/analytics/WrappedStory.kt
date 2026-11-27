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
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.lipabill.app.ui.LipaPillButton
import com.lipabill.app.ui.theme.HomeType
import com.lipabill.app.ui.util.formatKsh
import com.lipabill.app.viewmodel.WrapCards
import com.lipabill.app.viewmodel.wrapShareText
import java.io.File
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
// Period and money wording

internal val WrapDayMonth: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM", Locale.UK)
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

internal fun WrapCards.periodLong(): String =
    if (isWholeMonth()) "${periodName()} ${from.year}" else periodName()

private const val MASK = "KSh ••••"

/** KSh figures, or the mask when amounts are hidden. */
internal class WrapMoney(private val show: Boolean) {
    fun full(v: Double) = if (show) formatKsh(v) else MASK

    /** KSh 950, KSh 17,610, KSh 176K, KSh 1.7M — as the reference writes them. */
    fun short(v: Double): String = if (!show) MASK else when {
        abs(v) >= 1_000_000 -> "KSh " + String.format(Locale.US, "%.1fM", v / 1_000_000).replace(".0M", "M")
        abs(v) >= 100_000 -> "KSh ${(v / 1000).roundToInt()}K"
        else -> formatKsh(v)
    }
}

// ---------------------------------------------------------------------------------------------
// Card (art + words)

/** Full-size layout of a story card; previews scale this down so shared PNGs stay sharp. */
private val CardWidth = 360.dp
private val CardHeight = 640.dp
private val CardCorner = RoundedCornerShape(26.dp)

/** One story card. Also used by render tests. [topInset] leaves room for on-screen chrome. */
@Composable
internal fun WrapCard(
    kind: WrapCardKind,
    cards: WrapCards,
    showAmounts: Boolean,
    modifier: Modifier = Modifier,
    /** Swap payee names for "Person 1", "Shop 1" so the card can be posted. */
    hideNames: Boolean = false,
    topInset: Dp = 0.dp
) {
    val personality = personalityText(cards)
    Box(modifier = modifier.clip(CardCorner)) {
        WrapCardArt(kind, Modifier.fillMaxSize())
        WrapCardContent(
            kind = kind,
            cards = cards,
            money = WrapMoney(showAmounts),
            hideNames = hideNames,
            periodLong = cards.periodLong(),
            personality = personality,
            modifier = Modifier.padding(top = topInset)
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Insights teaser

@Composable
internal fun WrappedTeaser(cards: WrapCards, onOpen: () -> Unit, eyebrow: String = "WRAPPED") {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .clickable(onClick = onOpen)
    ) {
        WrapCardArt(WrapCardKind.INTRO, Modifier.matchParentSize())
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(eyebrow, style = HomeType.caption, color = WrapPalette.Lime, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            WrapBig("Your ${cards.periodName()}, wrapped", Color.White, 24.sp)
            Text(
                "Money in vs out, your people, top spots and your money personality, as cards you can share.",
                style = HomeType.caption,
                color = Color.White.copy(alpha = 0.85f)
            )
            LipaPillButton(label = "Open", onClick = onOpen, filled = false)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Story player

/**
 * Full-screen story, like the reference: progress and close sit on the card, tap the right
 * side to go on and the left to go back (or swipe), ending on the share screen.
 */
@Composable
internal fun WrappedDialog(cards: WrapCards, onDismiss: () -> Unit) {
    val kinds = remember(cards) { wrapKinds(cards) }
    val hubPage = kinds.size
    val pager = rememberPagerState(pageCount = { kinds.size + 1 })
    val scope = rememberCoroutineScope()
    val current = kinds.getOrNull(pager.currentPage)
    val backdrop by animateColorAsState(
        if (current == null) WrapPalette.ForestDeep else baseOf(current),
        label = "wrapBackdrop"
    )
    fun go(delta: Int) {
        val target = (pager.currentPage + delta).coerceIn(0, hubPage)
        if (target != pager.currentPage) scope.launch { pager.animateScrollToPage(target) }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .background(backdrop.copy(alpha = 0.55f))
                .safeDrawingPadding()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), pageSpacing = 12.dp) { index ->
                StoryFrame(
                    pager = pager,
                    index = index,
                    total = hubPage + 1,
                    onClose = onDismiss,
                    onTap = if (index == hubPage) null else { forward -> go(if (forward) 1 else -1) },
                    footerInk = kinds.getOrNull(index)?.let(::inkOf) ?: Color.White
                ) { inset ->
                    if (index == hubPage) {
                        ShareHub(shareKinds(cards), cards, topInset = inset)
                    } else {
                        WrapCard(
                            kind = kinds[index],
                            cards = cards,
                            showAmounts = true,
                            hideNames = false,
                            topInset = inset,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

/** The rounded story card with progress, close and "Tap to continue" drawn on it. */
@Composable
private fun StoryFrame(
    pager: PagerState,
    index: Int,
    total: Int,
    onClose: () -> Unit,
    onTap: ((forward: Boolean) -> Unit)?,
    footerInk: Color,
    content: @Composable (topInset: Dp) -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val height = minOf(maxHeight, maxWidth * 16f / 9f)
        val width = height * 9f / 16f
        Box(
            modifier = Modifier
                .size(width, height)
                .clip(CardCorner)
                .then(
                    if (onTap == null) Modifier
                    else Modifier.pointerInput(index) {
                        detectTapGestures { offset -> onTap(offset.x > size.width * 0.3f) }
                    }
                )
        ) {
            content(46.dp)
            // Progress segments.
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                repeat(total) { i ->
                    Box(
                        Modifier.weight(1f).height(3.dp).clip(CircleShape)
                            .background(Color.White.copy(alpha = if (i <= pager.currentPage) 0.95f else 0.35f))
                    )
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 24.dp, end = 12.dp)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.28f))
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(16.dp))
            }
            if (onTap != null) {
                Text(
                    "Tap to continue",
                    color = footerInk.copy(alpha = 0.6f),
                    fontSize = 10.sp,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Share screen

/** Last story page: a carousel of five cards, the two privacy switches, share / save / copy. */
@Composable
internal fun ShareHub(
    kinds: List<WrapCardKind>,
    cards: WrapCards,
    showAmounts: Boolean? = null,
    hideNames: Boolean? = null,
    topInset: Dp = 0.dp
) {
    var hideAmountsState by remember { mutableStateOf(showAmounts?.not() ?: false) }
    var hideNamesState by remember { mutableStateOf(hideNames ?: true) }
    val carousel = rememberPagerState(pageCount = { kinds.size })
    val layers = remember { mutableStateMapOf<Int, GraphicsLayer>() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val personality = personalityText(cards).first
    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(Modifier.matchParentSize()) { shareRays() }
        Column(
            modifier = Modifier.fillMaxSize().grain(0.06f).padding(top = topInset, start = 18.dp, end = 18.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Your wrap · swipe for more cards", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val cardHeight = minOf(maxHeight, maxWidth * 0.62f * 16f / 9f)
                val cardWidth = cardHeight * 9f / 16f
                val scale = cardWidth / CardWidth
                HorizontalPager(
                    state = carousel,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = ((maxWidth - cardWidth) / 2).coerceAtLeast(0.dp)),
                    pageSpacing = 10.dp
                ) { i ->
                    val layer = rememberGraphicsLayer()
                    layers[i] = layer
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(cardWidth, cardHeight)) {
                            WrapCard(
                                kind = kinds[i],
                                cards = cards,
                                showAmounts = !hideAmountsState,
                                hideNames = hideNamesState,
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
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.align(Alignment.CenterHorizontally)) {
                kinds.indices.forEach { i ->
                    Box(
                        Modifier.size(width = if (i == carousel.currentPage) 14.dp else 6.dp, height = 6.dp).clip(CircleShape)
                            .background(Color.White.copy(alpha = if (i == carousel.currentPage) 1f else 0.4f))
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                WrapSwitch("Hide amounts", hideAmountsState) { hideAmountsState = it }
                Spacer(Modifier.weight(1f))
                Text(
                    "${carousel.currentPage + 1} of ${kinds.size} · ${kinds[carousel.currentPage].title.removePrefix("Your ")}",
                    color = Color.White, fontSize = 13.sp
                )
            }
            WrapSwitch("Hide names", hideNamesState) { hideNamesState = it }
            Text("Share the card you're on, or save it and post it to your status.", color = Color.White, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WrapPill("Share card", light = true) {
                    layers[carousel.currentPage]?.let { scope.launch { shareCard(context, it) } }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    WrapPill("Save card", light = false) {
                        layers[carousel.currentPage]?.let { layer ->
                            scope.launch {
                                val bitmap = layer.toImageBitmap().asAndroidBitmap()
                                val saved = withContext(Dispatchers.IO) { saveToGallery(context, bitmap) }
                                Toast.makeText(context, if (saved) "Saved to Pictures/LipaBill" else "Couldn't save the card", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
            WrapPill("Copy text", light = false) {
                val text = wrapShareText(cards, cards.periodLong(), personality, !hideAmountsState, hideNamesState)
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("LipaBill Wrap", text))
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@Composable
private fun WrapSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = WrapPalette.Lime.copy(alpha = 0.85f),
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color.Black.copy(alpha = 0.35f),
                uncheckedBorderColor = Color.Transparent
            )
        )
        Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** White pill (primary) or black pill, as on the reference share screen. */
@Composable
private fun WrapPill(label: String, light: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(if (light) Color.White else Color(0xFF111111))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (light) Color(0xFF111111) else Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ---------------------------------------------------------------------------------------------
// Sharing

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

/**
 * Own subclass so the manifest merger keeps this provider apart from the internal build's
 * OTA FileProvider (same class name would merge the two entries and fail the build).
 */
class WrappedFileProvider : FileProvider()
