package com.lipabill.app.ui.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lipabill.app.R
import com.lipabill.app.data.model.TransactionType
import com.lipabill.app.ui.privacy.recordingPrivacyCover
import com.lipabill.app.viewmodel.CounterpartySpend
import com.lipabill.app.viewmodel.WrapCards
import com.lipabill.app.viewmodel.WrapPersonality
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// ---------------------------------------------------------------------------------------------
// Type and palette (from the M-PESA Wrap reference)

/** Heavy grotesque for headlines and figures. SIL OFL, see third_party/archivo_black. */
internal val WrapDisplay = FontFamily(Font(R.font.archivo_black))

internal object WrapPalette {
    val Forest = Color(0xFF2E7A4B)
    val ForestDeep = Color(0xFF0E3A23)
    val Moss = Color(0xFF4E9467)
    val MossDeep = Color(0xFF285F3E)
    val Jungle = Color(0xFF10291B)
    val JungleLeaf = Color(0xFF1F4A31)
    val Lime = Color(0xFFB4EF45)
    val SpotGreen = Color(0xFF8BDB5A)
    val Night = Color(0xFF0A0A0A)
    val Sun = Color(0xFFF3CC47)
    val Coral = Color(0xFFE8614B)
    val FlowerGreen = Color(0xFF47B94A)
    val TornGreen = Color(0xFF1E5A35)
    val Salmon = Color(0xFFED7C64)
    val SalmonDeep = Color(0xFFE06A53)
    val BarInk = Color(0xFF132419)
    val Plum = Color(0xFF1C0B27)
    val PlumDeep = Color(0xFF10061A)
    val Ink = Color(0xFF14130F)
}

internal enum class WrapCardKind(val title: String) {
    SUMMARY("Summary"),
    INTRO("Intro"),
    NUMBERS("By the numbers"),
    FLOW("The flow"),
    FEES("The quiet cost"),
    FULIZA("Fuliza"),
    PEOPLE("Your people"),
    SPOTS("Your top spots"),
    RHYTHM("Your rhythm"),
    PERSONALITY("Your money personality")
}

/** Story cards worth showing for [c], in story order. Empty ones are skipped. */
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

/** The five cards on the share screen, like the reference: summary first. */
internal fun shareKinds(c: WrapCards): List<WrapCardKind> = buildList {
    add(WrapCardKind.SUMMARY)
    add(WrapCardKind.NUMBERS)
    if (c.topSpots.isNotEmpty()) add(WrapCardKind.SPOTS)
    if (c.bars.isNotEmpty()) add(WrapCardKind.RHYTHM)
    add(WrapCardKind.PERSONALITY)
}

/** Text colour for a card: white on dark art, near-black on yellow / coral. */
internal fun inkOf(kind: WrapCardKind): Color = when (kind) {
    WrapCardKind.FULIZA, WrapCardKind.RHYTHM -> WrapPalette.Ink
    else -> Color.White
}

/** Main colour, used to tint the screen behind the card. */
internal fun baseOf(kind: WrapCardKind): Color = when (kind) {
    WrapCardKind.SUMMARY, WrapCardKind.INTRO, WrapCardKind.NUMBERS -> WrapPalette.ForestDeep
    WrapCardKind.FLOW, WrapCardKind.FEES -> WrapPalette.Night
    WrapCardKind.FULIZA -> Color(0xFF6B5A12)
    WrapCardKind.PEOPLE -> WrapPalette.MossDeep
    WrapCardKind.SPOTS -> WrapPalette.Jungle
    WrapCardKind.RHYTHM -> Color(0xFF6E2A1E)
    WrapCardKind.PERSONALITY -> WrapPalette.PlumDeep
}

// ---------------------------------------------------------------------------------------------
// Texture and motifs — vector only, seeded so a shared PNG matches the screen.

/** Film grain over the whole card. */
internal fun Modifier.grain(alpha: Float = 0.10f, light: Boolean = true, seed: Int = 7): Modifier =
    drawWithCache {
        val rnd = Random(seed)
        val count = (size.width * size.height / 55f).toInt().coerceAtMost(60_000)
        val dots = List(count) { Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height) }
        val color = (if (light) Color.White else Color.Black).copy(alpha = alpha)
        onDrawWithContent {
            drawContent()
            drawPoints(dots, PointMode.Points, color, strokeWidth = 1.6f)
        }
    }

private fun DrawScope.glowAsterisk(center: Offset, length: Float, alpha: Float) {
    val thick = length * 0.24f
    listOf(10f, 70f, 130f).forEach { angle ->
        rotate(angle, center) {
            drawRoundRect(
                brush = Brush.radialGradient(
                    listOf(WrapPalette.Lime.copy(alpha = alpha), WrapPalette.Lime.copy(alpha = alpha * 0.25f), Color.Transparent),
                    center, length * 0.62f
                ),
                topLeft = Offset(center.x - length / 2, center.y - thick / 2),
                size = Size(length, thick),
                cornerRadius = CornerRadius(thick / 2)
            )
        }
    }
    drawCircle(Brush.radialGradient(listOf(WrapPalette.Lime.copy(alpha = alpha * 0.9f), Color.Transparent), center, length * 0.28f), length * 0.28f, center)
}

private fun DrawScope.tornFlowEdge() {
    val w = size.width
    val h = size.height
    drawRect(WrapPalette.Lime, size = Size(w, h * 0.66f))
    val rnd = Random(11)
    val edge = Path().apply {
        moveTo(0f, h * 0.70f)
        var x = 0f
        while (x < w) {
            val step = w * (0.025f + rnd.nextFloat() * 0.07f)
            val base = h * (0.64f - 0.14f * (x / w))
            val tall = h * (0.02f + rnd.nextFloat() * rnd.nextFloat() * 0.16f)
            // Brush spikes lean right, like a stroke dragged up-right.
            lineTo(x + step * 0.85f, base - tall)
            lineTo(x + step, base + h * 0.015f * rnd.nextFloat())
            x += step
        }
        lineTo(w, h)
        lineTo(0f, h)
        close()
    }
    drawPath(edge, WrapPalette.Night)
    // Spray specks of lime falling into the black.
    repeat(900) {
        val px = rnd.nextFloat() * w
        val py = h * (0.55f + rnd.nextFloat() * 0.2f)
        drawCircle(WrapPalette.Lime.copy(alpha = 0.35f + rnd.nextFloat() * 0.5f), 0.8f + rnd.nextFloat() * 1.6f, Offset(px, py))
    }
}

private fun DrawScope.quietCostArt() {
    val w = size.width
    val h = size.height
    val rnd = Random(23)
    // Static and glitch lines.
    repeat(900) {
        drawCircle(Color.White.copy(alpha = rnd.nextFloat() * 0.5f), 0.6f + rnd.nextFloat() * 1.4f, Offset(rnd.nextFloat() * w, rnd.nextFloat() * h * 0.85f))
    }
    repeat(26) {
        val x = rnd.nextFloat() * w
        val top = rnd.nextFloat() * h * 0.7f
        drawLine(Color.White.copy(alpha = 0.25f + rnd.nextFloat() * 0.5f), Offset(x, top), Offset(x, top + h * (0.03f + rnd.nextFloat() * 0.12f)), 1.5f + rnd.nextFloat() * 2f)
    }
    // Lime scribble swoosh, top left.
    listOf(0f, 14f).forEach { dy ->
        val p = Path().apply {
            moveTo(-w * 0.05f, h * 0.15f + dy)
            cubicTo(w * 0.25f, h * 0.06f + dy, w * 0.55f, h * 0.10f + dy, w * 0.60f, h * 0.18f + dy)
            cubicTo(w * 0.45f, h * 0.24f + dy, w * 0.15f, h * 0.20f + dy, w * 0.02f, h * 0.17f + dy)
        }
        drawPath(p, WrapPalette.Lime, style = Stroke(width = 4f, cap = StrokeCap.Round))
    }
    // Coin stack, top right.
    val cx = w * 0.80f
    val cy = h * 0.16f
    val r = w * 0.085f
    repeat(6) { i ->
        val o = Offset(cx + i * r * 0.10f, cy + i * r * 0.22f)
        drawOval(
            Brush.linearGradient(listOf(Color(0xFFF4F4F4), Color(0xFF8C8C8C), Color(0xFFE6E6E6)), o - Offset(r, r * 0.45f), o + Offset(r, r * 0.45f)),
            topLeft = o - Offset(r, r * 0.45f),
            size = Size(r * 2, r * 0.9f)
        )
        drawOval(Color(0xFF3A3A3A), o - Offset(r, r * 0.45f), Size(r * 2, r * 0.9f), style = Stroke(1.5f))
    }
}

private fun DrawScope.fulizaArt() {
    val w = size.width
    val h = size.height
    // Torn dark-green paper across the top.
    val rnd = Random(5)
    val torn = Path().apply {
        moveTo(w * 0.25f, 0f)
        lineTo(w, 0f)
        lineTo(w, h * 0.16f)
        var x = w
        while (x > w * 0.25f) {
            x -= w * (0.02f + rnd.nextFloat() * 0.03f)
            lineTo(x, h * (0.10f + rnd.nextFloat() * 0.06f) - (w - x) / w * h * 0.06f)
        }
        close()
    }
    drawPath(torn, WrapPalette.TornGreen)
    // Coral four-lobed flower.
    val c = Offset(w * 0.28f, h * 0.24f)
    val lobe = w * 0.16f
    listOf(Offset(-1f, -0.2f), Offset(0.9f, -0.5f), Offset(1f, 0.6f), Offset(-0.5f, 0.9f)).forEach { d ->
        drawCircle(WrapPalette.Coral, lobe, c + Offset(d.x * lobe * 0.75f, d.y * lobe * 0.75f))
    }
    drawCircle(WrapPalette.Coral, lobe, c)
    // Small green flower.
    val f = Offset(w * 0.78f, h * 0.48f)
    val pr = w * 0.055f
    repeat(8) { i ->
        val a = Math.toRadians(i * 45.0)
        drawCircle(WrapPalette.FlowerGreen, pr, f + Offset((cos(a) * pr).toFloat(), (sin(a) * pr).toFloat()))
    }
    drawCircle(WrapPalette.FlowerGreen, pr, f)
}

private fun DrawScope.leafPattern() {
    val w = size.width
    val h = size.height
    val rnd = Random(31)
    repeat(34) {
        val c = Offset(rnd.nextFloat() * w * 1.1f - w * 0.05f, rnd.nextFloat() * h * 1.1f - h * 0.05f)
        val len = w * (0.18f + rnd.nextFloat() * 0.22f)
        rotate(rnd.nextFloat() * 360f, c) {
            val leaf = Path().apply {
                moveTo(c.x - len / 2, c.y)
                quadraticTo(c.x, c.y - len * 0.42f, c.x + len / 2, c.y)
                quadraticTo(c.x, c.y + len * 0.42f, c.x - len / 2, c.y)
                close()
            }
            drawPath(leaf, WrapPalette.JungleLeaf.copy(alpha = 0.85f))
            drawLine(WrapPalette.Jungle.copy(alpha = 0.9f), Offset(c.x - len / 2, c.y), Offset(c.x + len / 2, c.y), 2f)
            repeat(5) { k ->
                val x = c.x - len * 0.35f + k * len * 0.17f
                drawLine(WrapPalette.Jungle.copy(alpha = 0.7f), Offset(x, c.y), Offset(x + len * 0.08f, c.y - len * 0.16f), 1.4f)
                drawLine(WrapPalette.Jungle.copy(alpha = 0.7f), Offset(x, c.y), Offset(x + len * 0.08f, c.y + len * 0.16f), 1.4f)
            }
        }
    }
}

private fun DrawScope.crumpledPaper() {
    val rnd = Random(41)
    repeat(14) {
        val p = Path().apply {
            moveTo(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height)
            repeat(2) { lineTo(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height) }
            close()
        }
        drawPath(p, (if (it % 2 == 0) Color.White else Color.Black).copy(alpha = 0.018f))
    }
    repeat(18) {
        val a = Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height)
        drawLine(Color.White.copy(alpha = 0.07f), a, a + Offset(rnd.nextFloat() * 160f - 80f, rnd.nextFloat() * 160f - 80f), 1.2f)
    }
}

/** Spray-paint stroke: a soft core plus scattered specks along [path]. */
private fun DrawScope.spray(path: Path, width: Float, seed: Int) {
    drawPath(path, WrapPalette.Lime.copy(alpha = 0.28f), style = Stroke(width * 0.7f, cap = StrokeCap.Butt))
    drawPath(path, WrapPalette.Lime.copy(alpha = 0.35f), style = Stroke(width * 0.35f, cap = StrokeCap.Butt))
    val measure = PathMeasure().apply { setPath(path, false) }
    val rnd = Random(seed)
    val n = (measure.length * 1.4f).toInt()
    repeat(n) {
        val d = rnd.nextFloat() * measure.length
        val p = measure.getPosition(d)
        val spread = width * (rnd.nextFloat() + rnd.nextFloat() - 1f)
        val ang = rnd.nextFloat() * 6.283f
        drawCircle(
            WrapPalette.Lime.copy(alpha = 0.25f + rnd.nextFloat() * 0.6f),
            0.8f + rnd.nextFloat() * 1.8f,
            p + Offset(cos(ang) * spread, sin(ang) * spread)
        )
    }
}

private fun DrawScope.personalitySpray() {
    val w = size.width
    val h = size.height
    val stroke = w * 0.16f
    spray(Path().apply { moveTo(w * 0.30f, -h * 0.02f); quadraticTo(w * 0.42f, h * 0.30f, w * 0.52f, h * 0.44f) }, stroke, 1)
    spray(Path().apply { moveTo(w * 0.96f, h * 0.06f); quadraticTo(w * 0.70f, h * 0.30f, w * 0.52f, h * 0.44f) }, stroke, 2)
    spray(Path().apply { moveTo(w * 0.52f, h * 0.44f); quadraticTo(w * 0.80f, h * 0.62f, w * 1.06f, h * 0.60f) }, stroke, 3)
}

/** Light rays behind the share screen. */
internal fun DrawScope.shareRays() {
    drawRect(Brush.verticalGradient(listOf(WrapPalette.Forest, WrapPalette.ForestDeep)))
    val origin = Offset(size.width * 0.55f, size.height * 0.72f)
    val rnd = Random(17)
    repeat(9) { i ->
        val a = Math.toRadians((i * 40 + rnd.nextInt(18)).toDouble())
        val reach = size.maxDimension * 1.2f
        val tip = origin + Offset((cos(a) * reach).toFloat(), (sin(a) * reach).toFloat())
        val p = Path().apply {
            moveTo(origin.x, origin.y)
            lineTo(tip.x - 40f, tip.y)
            lineTo(tip.x + 40f, tip.y)
            close()
        }
        drawPath(p, Brush.radialGradient(listOf(WrapPalette.Lime.copy(alpha = 0.45f), Color.Transparent), origin, reach * 0.6f))
    }
}

private fun backgroundOf(kind: WrapCardKind): Brush = when (kind) {
    WrapCardKind.SUMMARY, WrapCardKind.INTRO -> Brush.linearGradient(listOf(Color(0xFF3F8A58), WrapPalette.ForestDeep))
    WrapCardKind.NUMBERS -> Brush.verticalGradient(listOf(Color(0xFF6FA882), WrapPalette.Forest, WrapPalette.ForestDeep))
    WrapCardKind.FLOW, WrapCardKind.FEES -> Brush.verticalGradient(listOf(WrapPalette.Night, WrapPalette.Night))
    WrapCardKind.FULIZA -> Brush.verticalGradient(listOf(WrapPalette.Sun, Color(0xFFEDC23A)))
    WrapCardKind.PEOPLE -> Brush.verticalGradient(listOf(Color(0xFF6BA07D), WrapPalette.Moss, WrapPalette.MossDeep))
    WrapCardKind.SPOTS -> Brush.verticalGradient(listOf(WrapPalette.Jungle, Color(0xFF0B1F14)))
    WrapCardKind.RHYTHM -> Brush.verticalGradient(listOf(WrapPalette.Salmon, WrapPalette.SalmonDeep))
    WrapCardKind.PERSONALITY -> Brush.verticalGradient(listOf(WrapPalette.Plum, WrapPalette.PlumDeep))
}

private fun DrawScope.motif(kind: WrapCardKind) {
    val w = size.width
    val h = size.height
    when (kind) {
        WrapCardKind.INTRO -> {
            glowAsterisk(Offset(w * 0.30f, h * 0.24f), w * 0.82f, 0.55f)
            glowAsterisk(Offset(w * 0.86f, h * 0.70f), w * 0.22f, 0.45f)
        }
        WrapCardKind.NUMBERS -> glowAsterisk(Offset(w * 0.30f, h * 0.66f), w * 0.45f, 0.30f)
        WrapCardKind.SUMMARY -> {
            val c = Offset(w * 0.95f, h * 0.20f)
            val lobe = w * 0.075f
            listOf(Offset(-0.7f, 0f), Offset(0.7f, 0f), Offset(0f, -0.7f), Offset(0f, 0.7f)).forEach { d ->
                drawCircle(WrapPalette.Coral, lobe, c + Offset(d.x * lobe, d.y * lobe))
            }
            val f = Offset(w * 0.90f, h * 0.035f)
            repeat(6) { i ->
                val a = Math.toRadians(i * 60.0)
                drawCircle(WrapPalette.Sun, w * 0.035f, f + Offset((cos(a) * w * 0.035f).toFloat(), (sin(a) * w * 0.035f).toFloat()))
            }
        }
        WrapCardKind.FLOW -> tornFlowEdge()
        WrapCardKind.FEES -> quietCostArt()
        WrapCardKind.FULIZA -> fulizaArt()
        WrapCardKind.PEOPLE -> Unit
        WrapCardKind.SPOTS -> leafPattern()
        WrapCardKind.RHYTHM -> crumpledPaper()
        WrapCardKind.PERSONALITY -> personalitySpray()
    }
}

// ---------------------------------------------------------------------------------------------
// Card layouts

/** Card art only (background, texture, motif). Chrome and text sit on top. */
@Composable
internal fun WrapCardArt(kind: WrapCardKind, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(backgroundOf(kind))
            .grain(alpha = if (kind == WrapCardKind.FLOW || kind == WrapCardKind.FEES) 0.05f else 0.09f)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) { motif(kind) }
    }
}

@Composable
internal fun WrapBig(text: String, color: Color, size: TextUnit, modifier: Modifier = Modifier) =
    Text(
        text,
        color = color,
        fontSize = size,
        fontFamily = WrapDisplay,
        lineHeight = size * 1.04f,
        letterSpacing = (-0.5).sp,
        modifier = modifier
    )

@Composable
private fun Body(text: String, color: Color, size: TextUnit = 14.sp, weight: FontWeight = FontWeight.Normal, modifier: Modifier = Modifier) =
    Text(text, color = color, fontSize = size, fontWeight = weight, lineHeight = size * 1.35f, modifier = modifier)

/** Eyebrow at the top-left of every card. */
@Composable
internal fun WrapEyebrow(text: String, color: Color) =
    Text(text, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)

/**
 * One story card's words and figures over its art. [topInset] leaves room for the story
 * progress bar when the card is on screen; shared images use 0.
 */
@Composable
internal fun WrapCardContent(
    kind: WrapCardKind,
    cards: WrapCards,
    money: WrapMoney,
    hideNames: Boolean,
    periodLong: String,
    personality: Pair<String, String>,
    modifier: Modifier = Modifier
) {
    val ink = inkOf(kind)
    val muted = ink.copy(alpha = 0.82f)
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 22.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val eyebrow = when (kind) {
            WrapCardKind.INTRO, WrapCardKind.SUMMARY -> "LipaBill Wrap · $periodLong"
            else -> kind.title
        }
        WrapEyebrow(eyebrow, if (kind == WrapCardKind.FLOW) WrapPalette.Ink else ink)
        when (kind) {
            WrapCardKind.SUMMARY -> SummaryBody(cards, money, hideNames, personality.first)
            WrapCardKind.INTRO -> {
                Spacer(Modifier.weight(1f))
                val name = cards.periodName()
                WrapBig(if (name.endsWith("so far")) "Here's your $name." else "This was your $name with money.", ink, 40.sp)
                Body(
                    if (cards.partial) "Counting from when LipaBill's records start. Tap through to see how it went."
                    else "Tap through to see how it went.",
                    muted
                )
            }
            WrapCardKind.NUMBERS -> NumbersBody(cards, ink, muted)
            WrapCardKind.FLOW -> FlowBody(cards, money, muted)
            WrapCardKind.FEES -> {
                Spacer(Modifier.weight(1f))
                WrapBig("Transaction charges took", ink, 30.sp)
                WrapBig(money.full(cards.fees), ink, 44.sp)
                Body(
                    buildString {
                        append("across ${cards.chargedTxnCount} charged transactions.")
                        cards.feeShareOfOut?.let { append(" That's ${String.format(Locale.US, "%.1f", it * 100)}% of everything that left your accounts.") }
                    },
                    muted
                )
            }
            WrapCardKind.FULIZA -> {
                val f = cards.fuliza
                Spacer(Modifier.weight(1f))
                WrapBig("You Fuliza'd", ink, 30.sp)
                WrapBig("${f?.count ?: 0}×", ink, 56.sp)
                if (f != null) Body("borrowing ${money.full(f.total)} in total. Average top-up: ${money.full(f.average)}.", ink)
            }
            WrapCardKind.PEOPLE -> RankedBody("Most of your send money went to", cards.topPeople, "transfer", ink, ink, money, if (hideNames) "Person" else null)
            WrapCardKind.SPOTS -> RankedBody("Where you paid most", cards.topSpots, "payment", ink, WrapPalette.SpotGreen, money, if (hideNames) "Shop" else null)
            WrapCardKind.RHYTHM -> RhythmBody(cards, money, hideNames)
            WrapCardKind.PERSONALITY -> {
                Spacer(Modifier.weight(1f))
                WrapBig(personality.first, ink, 44.sp)
                Body(personality.second, muted)
            }
        }
    }
}

@Composable
private fun ColumnScope.SummaryBody(c: WrapCards, money: WrapMoney, hideNames: Boolean, personalityTitle: String) {
    Spacer(Modifier.height(6.dp))
    Body("Money out", Color.White.copy(alpha = 0.8f), 12.sp)
    WrapBig(money.short(c.moneyOut), Color.White, 30.sp)
    Body("Money in", Color.White.copy(alpha = 0.8f), 12.sp)
    WrapBig(money.short(c.moneyIn), Color.White, 26.sp)
    Spacer(Modifier.height(6.dp))
    @Composable
    fun Line(label: String, value: String, coverName: Boolean = false) {
        Column {
            Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Body(label, Color.White.copy(alpha = 0.85f), 12.sp, modifier = Modifier.weight(1f))
                Text(
                    value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.recordingPrivacyCover(coverName)
                )
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.18f)))
        }
    }
    Line("Transactions", "%,d".format(c.txnCount))
    Line("Charges paid", money.full(c.fees))
    c.fuliza?.let { Line("Fuliza", "${it.count}×") }
    c.topSpots.firstOrNull()?.let { Line("Top spot", if (hideNames) "Shop 1" else it.name, !hideNames) }
    c.topPeople.firstOrNull()?.let { Line("Top person", if (hideNames) "Person 1" else it.name, !hideNames) }
    Spacer(Modifier.weight(1f))
    Body("My money personality", Color.White.copy(alpha = 0.7f), 11.sp)
    WrapBig(personalityTitle, Color.White, 22.sp)
}

private fun typeTiles(c: WrapCards): List<Pair<String, Int>> {
    fun n(vararg t: TransactionType) = t.sumOf { c.typeCounts[it] ?: 0 }
    return listOf(
        "Tills" to n(TransactionType.BUY_GOODS, TransactionType.POCHI),
        "Charges" to c.chargedTxnCount,
        "Sent" to n(TransactionType.SENT),
        "Airtime" to n(TransactionType.AIRTIME),
        "Withdrawals" to n(TransactionType.WITHDRAW),
        "Paybills" to n(TransactionType.PAYBILL),
        "Received" to n(TransactionType.RECEIVED, TransactionType.DEPOSIT),
        "Fuliza" to n(TransactionType.FULIZA)
    ).filter { it.second > 0 }
}

@Composable
private fun ColumnScope.NumbersBody(c: WrapCards, ink: Color, muted: Color) {
    WrapBig("%,d".format(c.txnCount), ink, 50.sp)
    Body(
        "transactions. That's ${String.format(Locale.US, "%.1f", c.perDay)} a day, on ${c.activeDays} of ${c.coveredDays} days.",
        ink
    )
    Spacer(Modifier.weight(1f))
    typeTiles(c).chunked(2).forEach { pair ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            pair.forEach { (label, count) ->
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.14f))
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Body(label, muted, 13.sp, modifier = Modifier.weight(1f))
                    Text("%,d".format(count), color = ink, fontSize = 15.sp, fontFamily = WrapDisplay)
                }
            }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
    c.busiestDay?.let { (day, count) ->
        Body("Busiest day: $count transactions on ${day.format(WrapDayMonth)}.", ink, 13.sp, FontWeight.SemiBold)
    }
}

@Composable
private fun ColumnScope.FlowBody(c: WrapCards, money: WrapMoney, muted: Color) {
    Spacer(Modifier.weight(1f))
    Body("Money out", Color.White, 14.sp)
    WrapBig(money.short(c.moneyOut), WrapPalette.Lime, 46.sp)
    Body("Money in", Color.White, 14.sp)
    WrapBig(money.short(c.moneyIn), Color.White, 46.sp)
    Body(
        buildString {
            c.heaviestOutDay?.let { (day, amount) -> append("Your heaviest day was ${day.format(WrapDayMonth)}: ${money.full(amount)} out.") }
            val net = c.net
            if (isNotEmpty()) append(" ")
            append(
                when {
                    net >= 1.0 -> "You kept ${money.full(net)}."
                    net <= -1.0 -> "${money.full(-net)} more went out than came in."
                    else -> "In and out came out even."
                }
            )
        },
        muted
    )
}

@Composable
private fun ColumnScope.RankedBody(
    lead: String,
    rows: List<CounterpartySpend>,
    countWord: String,
    ink: Color,
    accent: Color,
    money: WrapMoney,
    placeholder: String?
) {
    val top = rows.firstOrNull() ?: return
    fun nameOf(i: Int, row: CounterpartySpend) = if (placeholder != null) "$placeholder ${i + 1}" else row.name
    Body(lead, ink, 14.sp)
    WrapBig(nameOf(0, top), accent, 32.sp, Modifier.recordingPrivacyCover(placeholder == null))
    Body("${top.count} $countWord${if (top.count == 1) "" else "s"} · ${money.full(top.total)}", ink.copy(alpha = 0.85f), 13.sp)
    Spacer(Modifier.weight(1f))
    val max = rows.maxOf { it.total }.coerceAtLeast(1.0)
    rows.forEachIndexed { i, row ->
        Column(verticalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.padding(vertical = 3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}", color = ink.copy(alpha = 0.8f), fontSize = 13.sp, fontFamily = WrapDisplay, modifier = Modifier.width(24.dp))
                Text(
                    nameOf(i, row), color = ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).recordingPrivacyCover(placeholder == null)
                )
                Text(money.short(row.total), color = ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Box(
                Modifier.padding(start = 24.dp).fillMaxWidth().height(5.dp).clip(CircleShape)
                    .background(ink.copy(alpha = 0.22f))
            ) {
                Box(
                    Modifier.fillMaxWidth((row.total / max).toFloat().coerceIn(0.04f, 1f)).fillMaxHeight()
                        .clip(CircleShape).background(ink)
                )
            }
        }
    }
}

private fun weekdayPlural(d: DayOfWeek) = d.getDisplayName(TextStyle.FULL, Locale.UK) + "s"

@Composable
private fun ColumnScope.RhythmBody(c: WrapCards, money: WrapMoney, hideNames: Boolean) {
    val ink = WrapPalette.Ink
    val peak = c.bars.getOrNull(c.peakBar)
    val peakName = peak?.label?.let { if (it.startsWith("W")) "Week ${it.drop(1)}" else it.substringBefore(' ') }
    WrapBig(if (peakName != null) "$peakName hit hardest." else "Your rhythm.", ink, 30.sp)
    @Composable
    fun Tile(value: String, label: String, modifier: Modifier, cover: Boolean = false) {
        Column(
            modifier.clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.22f)).padding(10.dp)
        ) {
            Text(value, color = ink, fontSize = 17.sp, fontFamily = WrapDisplay)
            Body(label, ink.copy(alpha = 0.75f), 11.sp, modifier = Modifier.recordingPrivacyCover(cover))
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        c.busiestWeekday?.let { Tile(weekdayPlural(it), "busiest day", Modifier.weight(1f)) }
        if (c.afterMidnight > 0) Tile("${c.afterMidnight}", "payments after midnight", Modifier.weight(1f))
        else Tile("${c.quietDays}", "days nothing went out", Modifier.weight(1f))
    }
    c.biggestPayment?.let {
        Tile(
            money.full(it.amount),
            if (hideNames) "biggest single payment" else "biggest single payment · ${it.name}",
            Modifier.fillMaxWidth(),
            cover = !hideNames
        )
    }
    Spacer(Modifier.weight(1f))
    val max = c.bars.maxOf { it.outflow }.coerceAtLeast(1.0)
    Row(
        modifier = Modifier.fillMaxWidth().height(118.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        c.bars.forEachIndexed { i, bar ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.fillMaxWidth()
                        .height((96.dp * (bar.outflow / max).toFloat()).coerceAtLeast(4.dp))
                        .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                        .background(if (i == c.peakBar) Color.White else WrapPalette.BarInk)
                )
                Text(
                    if (bar.label.startsWith("W")) bar.label else bar.label.take(1),
                    color = ink, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1
                )
            }
        }
    }
}

/** Title and one-liner for the card's money personality. */
@Composable
internal fun personalityText(c: WrapCards): Pair<String, String> =
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
