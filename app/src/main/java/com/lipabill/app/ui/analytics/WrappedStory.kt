package com.lipabill.app.ui.analytics

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.lipabill.app.ui.LipaChoiceChip
import com.lipabill.app.ui.LipaPillButton
import com.lipabill.app.ui.theme.HomeType
import com.lipabill.app.ui.util.formatKsh
import com.lipabill.app.viewmodel.MonthlyWrapped
import java.io.File
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val WeekDay = DateTimeFormatter.ofPattern("d MMM", Locale.UK)

private fun monthName(wrapped: MonthlyWrapped): String =
    wrapped.month.month.getDisplayName(TextStyle.FULL, Locale.UK)

private fun previousMonthName(wrapped: MonthlyWrapped): String =
    wrapped.month.minusMonths(1).month.getDisplayName(TextStyle.FULL, Locale.UK)

/** Insights teaser: opens last month's story cards. */
@Composable
internal fun WrappedTeaser(wrapped: MonthlyWrapped, onOpen: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Brush.linearGradient(StoryGradients[0]))
            .padding(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("WRAPPED", style = HomeType.caption, color = StoryAccent, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        Text(
            "Your ${monthName(wrapped)}, wrapped",
            style = HomeType.section,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        Text("Top spot, quietest week, fees and more, as cards you can share.", style = HomeType.caption, color = StoryMuted)
        LipaPillButton(label = "Open", onClick = onOpen, filled = false)
    }
}

/** Deep app navy with one accent glow per card. */
private val StoryGradients = listOf(
    listOf(Color(0xFF1E3148), Color(0xFF2F4A6E)),
    listOf(Color(0xFF14233A), Color(0xFF3A3F7A)),
    listOf(Color(0xFF12302A), Color(0xFF2F6B57)),
    listOf(Color(0xFF2A1F3D), Color(0xFF5A3D7A)),
    listOf(Color(0xFF1B2D3F), Color(0xFF2E6A8E)),
    listOf(Color(0xFF33241A), Color(0xFF7A4E2D))
)
private val StoryAccent = Color(0xFFC5F04A)
private val StoryMuted = Color(0xFFD4DEEA)

private data class StoryPage(val eyebrow: String, val headline: String, val figure: String?, val detail: String?)

private fun pages(w: MonthlyWrapped, showAmounts: Boolean): List<StoryPage> {
    fun money(value: Double) = if (showAmounts) formatKsh(value) else "KSh ••••"
    val month = monthName(w)
    val before = previousMonthName(w)
    return buildList {
        add(StoryPage("LIPABILL WRAPPED", "$month ${w.month.year}", null, "Swipe through your month."))
        add(
            StoryPage(
                "WHAT WENT OUT",
                "You spent",
                money(w.spent),
                w.spendChange?.let {
                    when {
                        it < 0 -> "${-it}% less than $before. Nice."
                        it > 0 -> "$it% more than $before."
                        else -> "About the same as $before."
                    }
                }
            )
        )
        w.topMerchant?.let {
            add(StoryPage("YOUR TOP SPOT", it.name, "${it.visits} visits", "${money(it.total)} over the month"))
        }
        w.biggestCategory?.let {
            add(StoryPage("MOST OF IT WENT TO", it.label, "${w.biggestCategoryShare}%", "of everything you spent"))
        }
        w.quietestWeekStart?.let { start ->
            add(
                StoryPage(
                    "YOUR QUIETEST WEEK",
                    "${start.format(WeekDay)} – ${start.plusDays(6).format(WeekDay)}",
                    money(w.quietestWeekSpent),
                    "${w.noSpendDays} no-spend days in $month"
                )
            )
        }
        add(
            StoryPage(
                "FEES",
                "You paid in fees",
                money(w.fees),
                w.feesBefore?.let {
                    val diff = w.fees - it
                    when {
                        diff < -1.0 -> "${money(-diff)} less than $before."
                        diff > 1.0 -> "${money(diff)} more than $before."
                        else -> "Same as $before."
                    }
                }
            )
        )
    }
}

/** Full-screen story cards for last month, with a share button for the card on screen. */
@Composable
internal fun WrappedDialog(wrapped: MonthlyWrapped, onDismiss: () -> Unit) {
    var showAmounts by remember { mutableStateOf(false) }
    val stories = pages(wrapped, showAmounts)
    val pager = rememberPagerState(pageCount = { stories.size })
    val layers = remember { mutableStateMapOf<Int, GraphicsLayer>() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0B1320))
                .safeDrawingPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Story progress segments.
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                stories.indices.forEach { i ->
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
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close", tint = Color.White)
                }
            }
            HorizontalPager(
                state = pager,
                modifier = Modifier.weight(1f),
                pageSpacing = 16.dp
            ) { index ->
                val layer = rememberGraphicsLayer()
                layers[index] = layer
                StoryCard(
                    page = stories[index],
                    gradient = StoryGradients[index % StoryGradients.size],
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(9f / 14f)
                        .drawWithContent {
                            layer.record { this@drawWithContent.drawContent() }
                            drawLayer(layer)
                        }
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LipaChoiceChip(
                    label = if (showAmounts) "Amounts shown" else "Amounts hidden",
                    selected = showAmounts,
                    onClick = { showAmounts = !showAmounts }
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    val layer = layers[pager.currentPage] ?: return@IconButton
                    scope.launch {
                        val bitmap = layer.toImageBitmap().asAndroidBitmap()
                        val file = withContext(Dispatchers.IO) {
                            File(context.cacheDir, "wrapped").apply { mkdirs() }
                                .resolve("lipabill-wrapped.png")
                                .also { out -> out.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
                        }
                        shareImage(context, file)
                    }
                }) {
                    Icon(Icons.Outlined.Share, contentDescription = "Share this card", tint = Color.White)
                }
            }
        }
    }
}

private fun shareImage(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.wrapped", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("image/png")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, "Share your Wrapped card").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

@Composable
internal fun StoryCardPreview(eyebrow: String, headline: String, figure: String?, detail: String?, modifier: Modifier = Modifier) =
    StoryCard(StoryPage(eyebrow, headline, figure, detail), StoryGradients[2], modifier)

@Composable
private fun StoryCard(page: StoryPage, gradient: List<Color>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(gradient))
            .padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(page.eyebrow, color = StoryAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
        Spacer(modifier = Modifier.weight(1f))
        Text(page.headline, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, lineHeight = 36.sp)
        page.figure?.let {
            Text(it, color = StoryAccent, fontSize = 44.sp, fontWeight = FontWeight.Black, lineHeight = 50.sp)
        }
        page.detail?.let { Text(it, color = StoryMuted, fontSize = 17.sp, lineHeight = 24.sp) }
        Spacer(modifier = Modifier.weight(1f))
        Text("LipaBill", color = StoryMuted.copy(alpha = 0.8f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
