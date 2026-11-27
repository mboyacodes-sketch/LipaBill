package com.lipabill.app.ui.analytics

import android.os.Build
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
import androidx.compose.ui.draw.blur
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
// Type and palette
//
// Backgrounds follow the M-PESA Wrap reference (grain, blur, plaster, dry brush, linen, torn
// paper, crumpled paper, leaf line-art, spray). Colours are LipaBill's own: brand navy and
// cream, with marigold as the signal colour and rose for warmth.

/** Heavy grotesque for headlines and figures. SIL OFL, see third_party/archivo_black. */
internal val WrapDisplay = FontFamily(Font(R.font.archivo_black))

internal object WrapPalette {
    val Midnight = Color(0xFF0C1838)
    val Navy = Color(0xFF1B3B6E)
    val Cobalt = Color(0xFF3567C9)
    val CobaltDeep = Color(0xFF1C3778)
    val Haze = Color(0xFFBFD6F5)
    val Indigo = Color(0xFF111A3F)
    val IndigoLine = Color(0xFF2A3A7E)
    val Marigold = Color(0xFFFFB23F)
    val MarigoldDeep = Color(0xFFF29A1F)
    val InkNavy = Color(0xFF0A1026)
    val Cream = Color(0xFFF4E6C4)
    val CreamDeep = Color(0xFFE9D3A2)
    val Rose = Color(0xFFE9577E)
    val Sky = Color(0xFF9DC0EE)
    val SkyDeep = Color(0xFF86AEE6)
    val Dusk = Color(0xFF0B0F2E)
    val DuskDeep = Color(0xFF060820)
    /** Text on light cards (cream, sky, marigold). */
    val Ink = Color(0xFF0E1A3A)

    // Roles used by layouts.
    val Accent = Marigold
    val SpotName = Marigold
    val BarInk = Ink
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

/** Text colour for a card: white on dark art, ink navy on cream / sky. */
internal fun inkOf(kind: WrapCardKind): Color = when (kind) {
    WrapCardKind.FULIZA, WrapCardKind.RHYTHM -> WrapPalette.Ink
    else -> Color.White
}

/** Main colour, used to tint the screen behind the card. */
internal fun baseOf(kind: WrapCardKind): Color = when (kind) {
    WrapCardKind.SUMMARY, WrapCardKind.INTRO -> WrapPalette.Midnight
    WrapCardKind.NUMBERS, WrapCardKind.PEOPLE -> WrapPalette.CobaltDeep
    WrapCardKind.FLOW -> Color(0xFF5A3A08)
    WrapCardKind.FEES -> WrapPalette.InkNavy
    WrapCardKind.FULIZA -> Color(0xFF5C4C2C)
    WrapCardKind.SPOTS -> WrapPalette.Indigo
    WrapCardKind.RHYTHM -> Color(0xFF2A4A78)
    WrapCardKind.PERSONALITY -> WrapPalette.DuskDeep
}

// ---------------------------------------------------------------------------------------------
// Textures — vector only and seeded, so a shared PNG matches the screen.

/** Coarse film grain: light and dark specks over the whole card, as in the reference. */
internal fun Modifier.grain(strength: Float = 1f, seed: Int = 7): Modifier =
    drawWithCache {
        val rnd = Random(seed)
        val count = (size.width * size.height / 34f).toInt().coerceAtMost(110_000)
        val light = ArrayList<Offset>(count / 2)
        val dark = ArrayList<Offset>(count / 2)
        repeat(count) {
            val p = Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height)
            if (rnd.nextBoolean()) light += p else dark += p
        }
        onDrawWithContent {
            drawContent()
            drawPoints(light, PointMode.Points, Color.White.copy(alpha = 0.09f * strength), strokeWidth = 2.2f)
            drawPoints(dark, PointMode.Points, Color.Black.copy(alpha = 0.13f * strength), strokeWidth = 2.2f)
        }
    }

/** Gaussian-ish offset, for spray and splatter. */
private fun Random.spread(r: Float): Float = (nextFloat() + nextFloat() + nextFloat() - 1.5f) / 1.5f * r

/** Rough plaster: dense fine specks in two tones. */
private fun DrawScope.plaster(area: Size, base: Color, seed: Int) {
    val rnd = Random(seed)
    val n = (area.width * area.height / 9f).toInt().coerceAtMost(140_000)
    val hi = ArrayList<Offset>(n / 2)
    val lo = ArrayList<Offset>(n / 2)
    repeat(n) {
        val p = Offset(rnd.nextFloat() * area.width, rnd.nextFloat() * area.height)
        if (rnd.nextBoolean()) hi += p else lo += p
    }
    drawPoints(hi, PointMode.Points, Color.White.copy(alpha = 0.10f), strokeWidth = 1.6f)
    drawPoints(lo, PointMode.Points, base.copy(alpha = 0.22f), strokeWidth = 1.6f)
}

/** Linen weave: fine jittered warp and weft threads. */
private fun DrawScope.linen(seed: Int) {
    val rnd = Random(seed)
    var y = 0f
    while (y < size.height) {
        drawLine(Color.Black.copy(alpha = 0.035f + rnd.nextFloat() * 0.03f), Offset(0f, y), Offset(size.width, y + rnd.spread(2f)), 1.2f)
        y += 3.2f
    }
    var x = 0f
    while (x < size.width) {
        drawLine(Color.White.copy(alpha = 0.05f + rnd.nextFloat() * 0.04f), Offset(x, 0f), Offset(x + rnd.spread(2f), size.height), 1.2f)
        x += 3.2f
    }
}

/** Crumpled paper: long creases, each a light ridge with a shadow beside it. */
private fun DrawScope.creases(seed: Int) {
    val rnd = Random(seed)
    repeat(22) {
        val a = Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height)
        var p = a
        val path = Path().apply { moveTo(a.x, a.y) }
        val dir = rnd.nextFloat() * 6.283f
        repeat(4) {
            val step = size.minDimension * (0.08f + rnd.nextFloat() * 0.14f)
            val turn = dir + rnd.spread(0.6f)
            p += Offset(cos(turn) * step, sin(turn) * step)
            path.lineTo(p.x, p.y)
        }
        drawPath(path, Color.White.copy(alpha = 0.30f), style = Stroke(2f))
        translateBy(2f) { drawPath(path, Color.Black.copy(alpha = 0.16f), style = Stroke(2f)) }
    }
    // Soft facets between the creases.
    repeat(12) {
        val f = Path().apply {
            moveTo(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height)
            repeat(2) { lineTo(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height) }
            close()
        }
        drawPath(f, (if (it % 2 == 0) Color.White else Color.Black).copy(alpha = 0.03f))
    }
}

private inline fun DrawScope.translateBy(d: Float, block: DrawScope.() -> Unit) {
    drawContext.transform.translate(d, d)
    block()
    drawContext.transform.translate(-d, -d)
}

// ---------------------------------------------------------------------------------------------
// Motifs

/** Soft shapes that get blurred (asterisk, glows). Drawn as gradients so they stay soft
 * where RenderEffect blur is unavailable (Android 11 and older). */
private fun DrawScope.softShapes(kind: WrapCardKind) {
    val w = size.width
    val h = size.height
    fun asterisk(center: Offset, length: Float, color: Color) {
        val thick = length * 0.26f
        listOf(12f, 72f, 132f).forEach { angle ->
            rotate(angle, center) {
                drawRoundRect(
                    brush = Brush.radialGradient(listOf(color, color.copy(alpha = color.alpha * 0.55f)), center, length * 0.6f),
                    topLeft = Offset(center.x - length / 2, center.y - thick / 2),
                    size = Size(length, thick),
                    cornerRadius = CornerRadius(thick / 2)
                )
            }
        }
    }
    fun glow(center: Offset, radius: Float, color: Color) =
        drawCircle(Brush.radialGradient(listOf(color, Color.Transparent), center, radius), radius, center)
    when (kind) {
        WrapCardKind.INTRO -> {
            asterisk(Offset(w * 0.32f, h * 0.27f), w * 0.95f, WrapPalette.Marigold.copy(alpha = 0.85f))
            asterisk(Offset(w * 0.88f, h * 0.73f), w * 0.26f, WrapPalette.Marigold.copy(alpha = 0.7f))
        }
        WrapCardKind.SUMMARY -> glow(Offset(w * 0.2f, h * 0.1f), w * 0.9f, WrapPalette.Cobalt.copy(alpha = 0.55f))
        WrapCardKind.NUMBERS -> {
            glow(Offset(w * 0.95f, h * 0.02f), w * 0.85f, WrapPalette.Haze.copy(alpha = 0.95f))
            glow(Offset(w * 0.25f, h * 0.55f), w * 0.7f, WrapPalette.Midnight.copy(alpha = 0.8f))
            asterisk(Offset(w * 0.30f, h * 0.70f), w * 0.48f, WrapPalette.Marigold.copy(alpha = 0.35f))
        }
        WrapCardKind.PEOPLE -> {
            glow(Offset(w * 0.1f, h * 0.05f), w * 0.9f, WrapPalette.Haze.copy(alpha = 0.55f))
            glow(Offset(w * 0.9f, h * 0.9f), w * 0.9f, WrapPalette.Midnight.copy(alpha = 0.6f))
        }
        else -> Unit
    }
}

/** Marigold plaster over an ink dry-brush stroke, with bristle streaks and splatter. */
private fun DrawScope.dryBrushFlow() {
    val w = size.width
    val h = size.height
    drawRect(WrapPalette.Marigold, size = Size(w, h * 0.72f))
    plaster(Size(w, h * 0.72f), WrapPalette.MarigoldDeep, 3)
    val rnd = Random(11)
    val edgeAt = { x: Float -> h * (0.66f - 0.16f * (x / w)) }
    val body = Path().apply {
        moveTo(0f, edgeAt(0f) + h * 0.04f)
        var x = 0f
        while (x <= w) {
            lineTo(x, edgeAt(x) + rnd.spread(h * 0.025f))
            x += w * 0.02f
        }
        lineTo(w, h)
        lineTo(0f, h)
        close()
    }
    drawPath(body, WrapPalette.InkNavy)
    // Bristle streaks dragged up-right out of the stroke.
    repeat(260) {
        val x0 = rnd.nextFloat() * w
        val y0 = edgeAt(x0) + h * 0.03f
        val len = h * (0.02f + rnd.nextFloat() * rnd.nextFloat() * 0.16f)
        val dx = len * (0.45f + rnd.nextFloat() * 0.25f)
        drawLine(
            WrapPalette.InkNavy.copy(alpha = 0.55f + rnd.nextFloat() * 0.45f),
            Offset(x0, y0), Offset(x0 + dx, y0 - len),
            strokeWidth = 1.2f + rnd.nextFloat() * 4.5f,
            cap = StrokeCap.Round
        )
    }
    // Splatter both ways across the edge.
    repeat(1400) {
        val x = rnd.nextFloat() * w
        val y = edgeAt(x) + rnd.spread(h * 0.10f)
        val onInk = y > edgeAt(x)
        drawCircle(
            (if (onInk) WrapPalette.Marigold else WrapPalette.InkNavy).copy(alpha = 0.35f + rnd.nextFloat() * 0.55f),
            0.6f + rnd.nextFloat() * 1.8f, Offset(x, y)
        )
    }
    plaster(Size(w, h), Color.Black, 9)
}

private fun DrawScope.quietCostArt() {
    val w = size.width
    val h = size.height
    val rnd = Random(23)
    // Starry static.
    repeat(1300) {
        drawCircle(Color.White.copy(alpha = rnd.nextFloat() * 0.6f), 0.5f + rnd.nextFloat() * 1.6f, Offset(rnd.nextFloat() * w, rnd.nextFloat() * h * 0.9f))
    }
    // Clusters of vertical scratch lines.
    repeat(9) {
        val cx = rnd.nextFloat() * w
        val top = rnd.nextFloat() * h * 0.7f
        repeat(4 + rnd.nextInt(6)) { k ->
            val x = cx + k * (3f + rnd.nextFloat() * 6f)
            val y = top + rnd.spread(h * 0.03f)
            drawLine(Color.White.copy(alpha = 0.35f + rnd.nextFloat() * 0.5f), Offset(x, y), Offset(x, y + h * (0.02f + rnd.nextFloat() * 0.10f)), 1.2f + rnd.nextFloat() * 1.4f)
        }
    }
    // Hand-drawn marigold loop, top left.
    val loop = Path().apply {
        moveTo(-w * 0.05f, h * 0.17f)
        cubicTo(w * 0.20f, h * 0.05f, w * 0.52f, h * 0.06f, w * 0.62f, h * 0.20f)
        cubicTo(w * 0.42f, h * 0.15f, w * 0.20f, h * 0.17f, w * 0.06f, h * 0.24f)
        cubicTo(w * 0.24f, h * 0.20f, w * 0.40f, h * 0.18f, w * 0.48f, h * 0.20f)
    }
    drawPath(loop, WrapPalette.Marigold, style = Stroke(width = 4.5f, cap = StrokeCap.Round))
    // Striped ribbon coin stack, top right.
    val cx = w * 0.84f
    val cy = h * 0.15f
    val r = w * 0.09f
    repeat(7) { i ->
        val o = Offset(cx + i * r * 0.08f, cy + i * r * 0.20f)
        val tl = o - Offset(r, r * 0.42f)
        val sz = Size(r * 2, r * 0.84f)
        drawOval(Brush.linearGradient(listOf(Color.White, Color(0xFF9AA4B8), Color.White), tl, tl + Offset(sz.width, sz.height)), tl, sz)
        drawOval(Color(0xFF1A2340), tl, sz, style = Stroke(2.2f))
    }
}

private fun DrawScope.fulizaArt() {
    val w = size.width
    val h = size.height
    linen(5)
    // Torn navy paper across the top, with the pale fibre edge of a real tear.
    val rnd = Random(5)
    val edge = mutableListOf<Offset>()
    var x = w
    while (x > -10f) {
        edge += Offset(x, h * (0.10f + rnd.nextFloat() * 0.035f) + (w - x) / w * h * 0.03f)
        x -= w * (0.015f + rnd.nextFloat() * 0.03f)
    }
    val torn = Path().apply {
        moveTo(0f, 0f)
        lineTo(w, 0f)
        edge.forEach { lineTo(it.x, it.y) }
        close()
    }
    val fibre = Path().apply {
        moveTo(edge.first().x, edge.first().y)
        edge.forEach { lineTo(it.x, it.y + 3f) }
    }
    drawPath(fibre, WrapPalette.Cream.copy(alpha = 0.95f), style = Stroke(6f))
    drawPath(torn, WrapPalette.Navy)
    plaster(Size(w, h * 0.14f), WrapPalette.Midnight, 21)
    // Big rose heart, left.
    val c = Offset(w * 0.20f, h * 0.30f)
    val s = w * 0.30f
    val heart = Path().apply {
        moveTo(c.x - s * 0.6f, c.y + s * 0.15f)
        cubicTo(c.x - s * 0.4f, c.y - s * 1.1f, c.x + s * 1.0f, c.y - s * 0.8f, c.x + s * 0.55f, c.y + s * 0.05f)
        cubicTo(c.x + s * 1.05f, c.y + s * 0.6f, c.x + s * 0.2f, c.y + s * 1.4f, c.x - s * 0.6f, c.y + s * 0.15f)
        close()
    }
    drawPath(heart, WrapPalette.Rose)
    // Small cobalt flower, right.
    val f = Offset(w * 0.84f, h * 0.52f)
    val pr = w * 0.06f
    repeat(8) { i ->
        val a = Math.toRadians(i * 45.0)
        drawCircle(WrapPalette.Cobalt, pr, f + Offset((cos(a) * pr).toFloat(), (sin(a) * pr).toFloat()))
    }
    drawCircle(WrapPalette.Cobalt, pr, f)
}

/** Ornamental leaf line-art (batik-style) on deep indigo. */
private fun DrawScope.leafLineArt() {
    val w = size.width
    val h = size.height
    val rnd = Random(31)
    val line = WrapPalette.IndigoLine
    repeat(22) {
        val c = Offset(rnd.nextFloat() * w * 1.2f - w * 0.1f, rnd.nextFloat() * h * 1.1f - h * 0.05f)
        val len = w * (0.30f + rnd.nextFloat() * 0.30f)
        rotate(rnd.nextFloat() * 360f, c) {
            val leaf = Path().apply {
                moveTo(c.x - len / 2, c.y)
                quadraticTo(c.x, c.y - len * 0.40f, c.x + len / 2, c.y)
                quadraticTo(c.x, c.y + len * 0.40f, c.x - len / 2, c.y)
                close()
            }
            drawPath(leaf, line.copy(alpha = 0.35f))
            drawPath(leaf, line, style = Stroke(5f))
            drawLine(line, Offset(c.x - len / 2, c.y), Offset(c.x + len / 2, c.y), 4f)
            repeat(6) { k ->
                val vx = c.x - len * 0.38f + k * len * 0.14f
                drawLine(line, Offset(vx, c.y), Offset(vx + len * 0.09f, c.y - len * 0.15f), 3f, StrokeCap.Round)
                drawLine(line, Offset(vx, c.y), Offset(vx + len * 0.09f, c.y + len * 0.15f), 3f, StrokeCap.Round)
            }
        }
    }
    // Small ornaments between the leaves.
    repeat(14) {
        val o = Offset(rnd.nextFloat() * w, rnd.nextFloat() * h)
        drawCircle(line, w * 0.02f, o, style = Stroke(3f))
        drawCircle(line, w * 0.006f, o)
    }
}

/** Stippled spray paint along [path]: specks only, dense in the middle, fading out. */
private fun DrawScope.spray(path: Path, width: Float, color: Color, seed: Int) {
    val measure = PathMeasure().apply { setPath(path, false) }
    val rnd = Random(seed)
    val n = (measure.length * 22f).toInt().coerceAtMost(120_000)
    val pts = ArrayList<Offset>(n)
    val faint = ArrayList<Offset>(n / 2)
    repeat(n) {
        val p = measure.getPosition(rnd.nextFloat() * measure.length)
        val ang = rnd.nextFloat() * 6.283f
        val r = rnd.spread(width)
        val q = p + Offset(cos(ang) * r, sin(ang) * r)
        if (kotlin.math.abs(r) < width * 0.45f) pts += q else faint += q
    }
    drawPath(path, color.copy(alpha = 0.06f), style = Stroke(width * 1.6f, cap = StrokeCap.Butt))
    drawPoints(pts, PointMode.Points, color.copy(alpha = 0.9f), strokeWidth = 2.6f)
    drawPoints(faint, PointMode.Points, color.copy(alpha = 0.5f), strokeWidth = 2.0f)
}

private fun DrawScope.personalitySpray() {
    val w = size.width
    val h = size.height
    val stroke = w * 0.24f
    val c = WrapPalette.Rose
    spray(Path().apply { moveTo(w * 0.28f, -h * 0.02f); quadraticTo(w * 0.40f, h * 0.30f, w * 0.52f, h * 0.45f) }, stroke, c, 1)
    spray(Path().apply { moveTo(w * 0.98f, h * 0.05f); quadraticTo(w * 0.72f, h * 0.30f, w * 0.52f, h * 0.45f) }, stroke, c, 2)
    spray(Path().apply { moveTo(w * 0.52f, h * 0.45f); quadraticTo(w * 0.62f, h * 0.70f, w * 0.80f, h * 1.05f) }, stroke, c, 3)
}

/** Light rays behind the share screen. */
internal fun DrawScope.shareRays() {
    drawRect(Brush.verticalGradient(listOf(WrapPalette.Navy, WrapPalette.Midnight)))
    val origin = Offset(size.width * 0.55f, size.height * 0.72f)
    val rnd = Random(17)
    repeat(10) { i ->
        val a = Math.toRadians((i * 36 + rnd.nextInt(16)).toDouble())
        val reach = size.maxDimension * 1.2f
        val tip = origin + Offset((cos(a) * reach).toFloat(), (sin(a) * reach).toFloat())
        val p = Path().apply {
            moveTo(origin.x, origin.y)
            lineTo(tip.x - 50f, tip.y)
            lineTo(tip.x + 50f, tip.y)
            close()
        }
        drawPath(p, Brush.radialGradient(listOf(WrapPalette.Marigold.copy(alpha = 0.45f), Color.Transparent), origin, reach * 0.6f))
    }
}

private fun backgroundOf(kind: WrapCardKind): Brush = when (kind) {
    WrapCardKind.SUMMARY, WrapCardKind.INTRO -> Brush.linearGradient(listOf(WrapPalette.Navy, WrapPalette.Midnight))
    WrapCardKind.NUMBERS -> Brush.verticalGradient(listOf(WrapPalette.Cobalt, WrapPalette.CobaltDeep, WrapPalette.Midnight))
    WrapCardKind.FLOW -> Brush.verticalGradient(listOf(WrapPalette.Marigold, WrapPalette.InkNavy))
    WrapCardKind.FEES -> Brush.verticalGradient(listOf(WrapPalette.InkNavy, Color(0xFF060A1A)))
    WrapCardKind.FULIZA -> Brush.verticalGradient(listOf(WrapPalette.Cream, WrapPalette.CreamDeep))
    WrapCardKind.PEOPLE -> Brush.verticalGradient(listOf(Color(0xFF5B86D6), WrapPalette.Cobalt, WrapPalette.CobaltDeep))
    WrapCardKind.SPOTS -> Brush.verticalGradient(listOf(WrapPalette.Indigo, Color(0xFF0A1030)))
    WrapCardKind.RHYTHM -> Brush.verticalGradient(listOf(WrapPalette.Sky, WrapPalette.SkyDeep))
    WrapCardKind.PERSONALITY -> Brush.verticalGradient(listOf(WrapPalette.Dusk, WrapPalette.DuskDeep))
}

private fun DrawScope.sharpMotif(kind: WrapCardKind) {
    val w = size.width
    val h = size.height
    when (kind) {
        WrapCardKind.SUMMARY -> {
            val c = Offset(w * 0.95f, h * 0.20f)
            val lobe = w * 0.075f
            listOf(Offset(-0.7f, 0f), Offset(0.7f, 0f), Offset(0f, -0.7f), Offset(0f, 0.7f)).forEach { d ->
                drawCircle(WrapPalette.Rose, lobe, c + Offset(d.x * lobe, d.y * lobe))
            }
            val f = Offset(w * 0.90f, h * 0.035f)
            repeat(6) { i ->
                val a = Math.toRadians(i * 60.0)
                drawCircle(WrapPalette.Marigold, w * 0.035f, f + Offset((cos(a) * w * 0.035f).toFloat(), (sin(a) * w * 0.035f).toFloat()))
            }
        }
        WrapCardKind.FLOW -> dryBrushFlow()
        WrapCardKind.FEES -> quietCostArt()
        WrapCardKind.FULIZA -> fulizaArt()
        WrapCardKind.SPOTS -> leafLineArt()
        WrapCardKind.RHYTHM -> creases(41)
        WrapCardKind.PERSONALITY -> personalitySpray()
        else -> Unit
    }
}

private fun hasSoftShapes(kind: WrapCardKind) =
    kind == WrapCardKind.INTRO || kind == WrapCardKind.SUMMARY || kind == WrapCardKind.NUMBERS || kind == WrapCardKind.PEOPLE

// ---------------------------------------------------------------------------------------------
// Card layouts

/** Card art only (background, blur, motif, grain). Words sit on top. */
@Composable
internal fun WrapCardArt(kind: WrapCardKind, modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(backgroundOf(kind))) {
        if (hasSoftShapes(kind)) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(36.dp) else Modifier)
            ) { softShapes(kind) }
        }
        Canvas(modifier = Modifier.fillMaxSize().grain(if (kind == WrapCardKind.FEES) 0.5f else 1f)) { sharpMotif(kind) }
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

/** One story card's words and figures over its art. */
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
        val eyebrowInk = when (kind) {
            WrapCardKind.FLOW -> WrapPalette.Ink
            WrapCardKind.FULIZA -> WrapPalette.Cream // sits on the torn navy paper
            else -> ink
        }
        WrapEyebrow(eyebrow, eyebrowInk)
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
            WrapCardKind.SPOTS -> RankedBody("Where you paid most", cards.topSpots, "payment", ink, WrapPalette.SpotName, money, if (hideNames) "Shop" else null)
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
    WrapBig(money.short(c.moneyOut), WrapPalette.Accent, 30.sp)
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
    WrapBig(money.short(c.moneyOut), WrapPalette.Accent, 46.sp)
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
            modifier.clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.28f)).padding(10.dp)
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
