package com.lipabill.app.ui.tickets

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.lipabill.app.data.model.AppRegion
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/* ───────────────────────────── Where ───────────────────────────── */

/** Skylines we can draw. Detected from the venue / city / title text of a ticket. */
enum class EventSkyline(val label: String, internal val keywords: List<String>) {
    NAIROBI(
        "Nairobi",
        listOf(
            "nairobi", "kicc", "kasarani", "nyayo", "carnivore", "uhuru gardens", "uhuru park",
            "westlands", "karen", "ngong racecourse", "ngong road", "sarit", "two rivers",
            "moi international sports", "kenyatta international", "garden city", "village market",
            "kilimani", "lavington", "upper hill", "upperhill", "parklands", "thika road", "jkia",
            "kileleshwa", "gigiri", "alchemist", "k1 klubhouse", "the junction", "runda", "muthaiga",
            "ihub", "senteu", "kenyatta avenue", "moi avenue", "cbd nairobi", "rosslyn", "spring valley",
            // Talanta Sports City / Raila Odinga International Stadium (opens Jan 2027).
            "talanta", "raila odinga", "jamhuri grounds"
        )
    ),
    MOMBASA(
        "Mombasa",
        listOf("mombasa", "nyali", "bamburi", "haller", "fort jesus", "mama ngina", "likoni", "shanzu", "mtwapa")
    ),
    COAST("the coast", listOf("diani", "watamu", "malindi", "kilifi", "lamu", "zanzibar", "ukunda", "msambweni", "beach")),
    KISUMU("Kisumu", listOf("kisumu", "dunga", "lake victoria", "jomo kenyatta sports ground", "tom mboya labour college")),
    RIFT("the Rift Valley", listOf("nakuru", "naivasha", "eldoret", "nanyuki", "rift valley", "hell's gate", "elementaita")),
    KAMPALA("Kampala", listOf("kampala", "entebbe", "munyonyo", "kololo", "lugogo", "namboole")),
    KIGALI("Kigali", listOf("kigali", "bk arena", "amahoro")),
    DAR("Dar es Salaam", listOf("dar es salaam", "dar-es-salaam", "masaki", "oyster bay", "mlimani")),
    ADDIS("Addis Ababa", listOf("addis ababa", "addis abeba", "meskel square")),
    LAGOS("Lagos", listOf("lagos", "victoria island", "lekki", "ikoyi", "eko hotel", "eko atlantic")),
    JOHANNESBURG("Johannesburg", listOf("johannesburg", "joburg", "jozi", "sandton", "soweto", "fnb stadium", "ellis park")),
    CAPE_TOWN("Cape Town", listOf("cape town", "kaapstad", "green point", "v&a waterfront", "camps bay")),
    DUBAI("Dubai", listOf("dubai", "coca-cola arena", "expo city", "jumeirah", "abu dhabi")),
    LONDON("London", listOf("london", "wembley", "o2 arena", "brixton", "hyde park", "shoreditch")),
    PARIS("Paris", listOf("paris", "accor arena", "stade de france", "la defense")),
    NEW_YORK("New York", listOf("new york", "nyc", "brooklyn", "manhattan", "madison square", "barclays center", "queens")),
    STADIUM("the stadium", listOf("stadium", "arena", "grounds", "sports centre", "sports center", "stade")),
    CITY("the city", emptyList());

    companion object {
        /**
         * The skyline a ticket should show: a city named in its text, otherwise the main city
         * of the region set in the app. Venue-only tickets ("iHUB", "Jenga Jungle") are local,
         * so every pass gets a real regional skyline instead of a generic one.
         */
        fun forTicket(home: AppRegion, vararg text: String?): EventSkyline {
            val found = detect(*text)
            return if (found == STADIUM || found == CITY) homeCity(home) else found
        }

        fun homeCity(region: AppRegion): EventSkyline = when (region) {
            AppRegion.KENYA -> NAIROBI
            AppRegion.UGANDA -> KAMPALA
            AppRegion.TANZANIA -> DAR
            AppRegion.RWANDA -> KIGALI
        }

        /** First city whose keyword appears in [text]; specific cities win over the stadium fallback. */
        fun detect(vararg text: String?): EventSkyline {
            val hay = text.filterNotNull().joinToString(" ").lowercase(Locale.US)
            if (hay.isBlank()) return CITY
            return entries.firstOrNull { sky ->
                sky != STADIUM && sky.keywords.any { hay.contains(it) }
            } ?: if (STADIUM.keywords.any { hay.contains(it) }) STADIUM else CITY
        }
    }
}

/* ───────────────────────────── When ───────────────────────────── */

/** Sky state for an hour of the day (0–24, fractional). Interpolated between keyframes. */
internal data class SkyLight(
    val hour: Float,
    val skyTop: Color,
    val skyBottom: Color,
    val silhouette: Color,
    /** 0 = windows dark, 1 = fully lit. */
    val windows: Float,
    val stars: Float,
    val label: String
) {
    /** Daylight sun arc 0..1 across the sky, or null when the sun is down. */
    val sunProgress: Float?
        get() = ((hour - 6f) / 12.8f).takeIf { it in 0f..1f }

    /** Darkness used for stage lights and the moon. */
    val night: Float get() = windows
}

private class SkyKey(val hour: Float, val top: Long, val bottom: Long, val sil: Long, val win: Float, val stars: Float)

private val skyKeys = listOf(
    SkyKey(0f, 0xFF070A1A, 0xFF1A1F3D, 0xFF05060C, 1f, 1f),
    SkyKey(4.8f, 0xFF070A1A, 0xFF1A1F3D, 0xFF05060C, 1f, 1f),
    SkyKey(6f, 0xFF2B3A6B, 0xFFF2A07B, 0xFF1E1A2E, 0.55f, 0.25f),
    SkyKey(7.5f, 0xFF5B8FD6, 0xFFF7D6A8, 0xFF2E3448, 0.1f, 0f),
    SkyKey(12f, 0xFF3A7BD5, 0xFFA9D2F5, 0xFF34405A, 0f, 0f),
    SkyKey(16.5f, 0xFF4A86D0, 0xFFF5D7A1, 0xFF33384D, 0f, 0f),
    SkyKey(18f, 0xFF6A4C93, 0xFFFF8C42, 0xFF2A1A2A, 0.4f, 0f),
    SkyKey(19f, 0xFF2E2A5C, 0xFFD9677A, 0xFF140F1E, 0.8f, 0.3f),
    SkyKey(20.5f, 0xFF0C1230, 0xFF2A2F6A, 0xFF07081A, 1f, 0.8f),
    SkyKey(24f, 0xFF070A1A, 0xFF1A1F3D, 0xFF05060C, 1f, 1f)
)

/** Undated passes are drawn at a typical show time. */
internal const val DefaultEventHour = 21f

internal fun skyAt(hour: Float): SkyLight {
    val hr = ((hour % 24f) + 24f) % 24f
    val i = skyKeys.indexOfLast { it.hour <= hr }.coerceIn(0, skyKeys.size - 2)
    val a = skyKeys[i]
    val b = skyKeys[i + 1]
    val t = ((hr - a.hour) / (b.hour - a.hour)).coerceIn(0f, 1f)
    val label = when {
        hr < 5f || hr >= 20.5f -> "night"
        hr < 7f -> "dawn"
        hr < 16.5f -> "daytime"
        hr < 18.6f -> "golden hour"
        else -> "dusk"
    }
    return SkyLight(
        hour = hr,
        skyTop = lerp(Color(a.top), Color(b.top), t),
        skyBottom = lerp(Color(a.bottom), Color(b.bottom), t),
        silhouette = lerp(Color(a.sil), Color(b.sil), t),
        windows = a.win + (b.win - a.win) * t,
        stars = a.stars + (b.stars - a.stars) * t,
        label = label
    )
}

internal fun eventHour(startsAtMillis: Long?, zone: ZoneId = ZoneId.systemDefault()): Float {
    if (startsAtMillis == null) return DefaultEventHour
    val t = Instant.ofEpochMilli(startsAtMillis).atZone(zone).toLocalTime()
    // Date-only passes are stored at midnight: draw them as a show night, not 00:00.
    if (t.hour == 0 && t.minute == 0) return DefaultEventHour
    return t.hour + t.minute / 60f
}

/* ───────────────────────────── Drawing ───────────────────────────── */

/**
 * Banner art: the event city's skyline under a sky for the event's start time.
 * [stageGlows] colour the searchlights that come up after dark.
 */
@Composable
fun EventSkylineArt(
    skyline: EventSkyline,
    startsAtMillis: Long?,
    seed: String,
    stageGlows: List<Color>,
    modifier: Modifier = Modifier
) {
    val light = remember(startsAtMillis) { skyAt(eventHour(startsAtMillis)) }
    val hash = remember(seed) { abs(seed.hashCode()) }
    val description = "${skyline.label.replaceFirstChar { it.titlecase(Locale.US) }} skyline, ${light.label}"
    Spacer(
        modifier = modifier
            .semantics { contentDescription = description }
            .drawWithCache {
                // Building geometry depends only on size: build it once, not on every draw.
                val city = buildSkyline(skyline, hash, size.width, size.height)
                onDrawBehind { drawEventSkyline(skyline, light, hash, stageGlows, city) }
            }
    )
}

/** City / country lockup above the skyline; null for the generic scenes. */
internal val EventSkyline.lockup: Pair<String, String>?
    get() = when (this) {
        EventSkyline.NAIROBI -> "NAIROBI" to "KENYA"
        EventSkyline.MOMBASA -> "MOMBASA" to "KENYA"
        EventSkyline.COAST -> "THE COAST" to "KENYA"
        EventSkyline.KISUMU -> "KISUMU" to "KENYA"
        EventSkyline.RIFT -> "RIFT VALLEY" to "KENYA"
        EventSkyline.KAMPALA -> "KAMPALA" to "UGANDA"
        EventSkyline.KIGALI -> "KIGALI" to "RWANDA"
        EventSkyline.DAR -> "DAR ES SALAAM" to "TANZANIA"
        EventSkyline.ADDIS -> "ADDIS ABABA" to "ETHIOPIA"
        EventSkyline.LAGOS -> "LAGOS" to "NIGERIA"
        EventSkyline.JOHANNESBURG -> "JOHANNESBURG" to "SOUTH AFRICA"
        EventSkyline.CAPE_TOWN -> "CAPE TOWN" to "SOUTH AFRICA"
        EventSkyline.DUBAI -> "DUBAI" to "UAE"
        EventSkyline.LONDON -> "LONDON" to "UNITED KINGDOM"
        EventSkyline.PARIS -> "PARIS" to "FRANCE"
        EventSkyline.NEW_YORK -> "NEW YORK" to "USA"
        EventSkyline.STADIUM, EventSkyline.CITY -> null
    }

/** Paper, ink and fill for the line-art scene at this time of day. */
internal data class SkylineInks(
    val paperTop: Color,
    val paperBottom: Color,
    val fill: Color,
    val ink: Color,
    val dark: Float
)

internal fun skylineInks(light: SkyLight): SkylineInks {
    // 0 in daylight, 1 at night; eased so dusk tips over cleanly.
    val dark = (light.windows * light.windows * (3f - 2f * light.windows)).coerceIn(0f, 1f)
    val tint = maxOf(0.14f, 0.9f * dark)
    val paperTop = lerp(Color(0xFFF7F7F8), light.skyTop, tint)
    val paperBottom = lerp(Color(0xFFEDEEF0), light.skyBottom, maxOf(0.2f, 0.9f * dark))
    val fill = if (dark > 0.5f) lerp(paperTop, Color.Black, 0.25f) else lerp(paperBottom, Color.White, 0.75f)
    val lum = 0.2126f * paperBottom.red + 0.7152f * paperBottom.green + 0.0722f * paperBottom.blue
    val ink = if (lum > 0.42f && dark < 0.5f) Color(0xFF45484E) else Color(0xFFD9DEEC)
    return SkylineInks(paperTop, paperBottom, fill, ink, dark)
}

/** Line-art skyline on paper, lit for the hour; plain DrawScope so it can render off-screen. */
/** Skyline geometry for a banner size; the lockup takes the top, so the city scales under it. */
internal fun buildSkyline(skyline: EventSkyline, hash: Int, width: Float, height: Float): Skyline =
    Skyline(width, height, if (skyline.lockup != null) 0.76f else 0.88f).apply { build(skyline, hash) }

/** Whole banner scene; plain DrawScope so it can also be rendered off-screen. */
internal fun DrawScope.drawEventSkyline(
    skyline: EventSkyline,
    light: SkyLight,
    hash: Int,
    stageGlows: List<Color>,
    prebuilt: Skyline? = null
) {
    val inks = skylineInks(light)
    val unit = size.height
    // Paper with a soft bright centre, like a printed poster.
    drawRect(Brush.verticalGradient(listOf(inks.paperTop, inks.paperBottom)))
    drawCircle(
        Brush.radialGradient(
            listOf(Color.White.copy(alpha = 0.55f * (1f - inks.dark)), Color.Transparent),
            center = Offset(size.width / 2f, size.height * 0.45f),
            radius = size.width * 0.55f
        ),
        radius = size.width * 0.55f,
        center = Offset(size.width / 2f, size.height * 0.45f)
    )
    drawSkyBodies(light, inks, hash)
    if (inks.dark > 0.5f) drawSearchlights(light, stageGlows)

    val lockup = skyline.lockup
    val city = prebuilt ?: buildSkyline(skyline, hash, size.width, size.height)
    val ghostInk = inks.ink.copy(alpha = 0.3f)
    // Distant landforms: pale wash with a faint edge.
    city.farFills.forEach {
        drawPath(it, lerp(inks.paperBottom, inks.ink, 0.1f))
        drawPath(it, ghostInk, style = Stroke(width = unit * 0.003f))
    }
    drawPath(city.ghosts, ghostInk, style = Stroke(width = unit * 0.0028f))
    city.water?.let { drawWaterLines(it, city.vScale, inks) }
    val detail = Stroke(width = unit * 0.0022f)
    city.fills.forEach { drawPath(it, inks.fill) }
    city.clipped.forEach { (clip, lines) ->
        clipPath(clip) { drawPath(lines, inks.ink.copy(alpha = 0.75f), style = detail) }
    }
    drawPath(city.windowFrames, inks.ink.copy(alpha = 0.7f * (1f - inks.dark) + 0.25f), style = Stroke(width = unit * 0.0018f))
    drawWindows(city.cells, light, hash)
    drawPath(city.lines, inks.ink.copy(alpha = 0.85f), style = Stroke(width = unit * 0.0026f))
    drawPath(city.outlines, inks.ink, style = Stroke(width = unit * 0.0034f))
    city.heavy.forEach { (p, wFrac) ->
        drawPath(p, inks.ink, style = Stroke(width = wFrac * unit * 0.55f, cap = StrokeCap.Round))
    }
    // Ground line.
    drawLine(
        inks.ink,
        Offset(size.width * 0.03f, size.height - unit * 0.004f),
        Offset(size.width * 0.97f, size.height - unit * 0.004f),
        strokeWidth = unit * 0.006f,
        cap = StrokeCap.Round
    )
    lockup?.let { (name, country) -> drawLockup(name, country, inks.ink) }
}

/** "NAIROBI" in heavy condensed caps between rules, country underneath. */
private fun DrawScope.drawLockup(name: String, country: String, ink: Color) {
    val canvas = drawContext.canvas.nativeCanvas
    val argb = android.graphics.Color.argb(
        (ink.alpha * 255).toInt(), (ink.red * 255).toInt(), (ink.green * 255).toInt(), (ink.blue * 255).toInt()
    )
    val big = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = argb
        textSize = size.height * 0.13f
        typeface = android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD)
        textAlign = android.graphics.Paint.Align.CENTER
        letterSpacing = 0.04f
    }
    val small = android.graphics.Paint(big).apply {
        textSize = size.height * 0.052f
        letterSpacing = 0.12f
    }
    val cx = size.width / 2f
    val nameW = big.measureText(name)
    val top = size.height * 0.07f
    // Rules always clear the country with a visible stroke on each side.
    val ruleW = maxOf(nameW * 1.08f, size.width * 0.2f, small.measureText(country) + size.width * 0.14f)
    val stroke = size.height * 0.006f
    drawLine(ink, Offset(cx - ruleW / 2f, top), Offset(cx + ruleW / 2f, top), stroke)
    val baseline = top + size.height * 0.15f
    canvas.drawText(name, cx, baseline, big)
    val countryY = baseline + size.height * 0.075f
    canvas.drawText(country, cx, countryY, small)
    val cw = small.measureText(country)
    val ry = countryY - size.height * 0.018f
    drawLine(ink, Offset(cx - ruleW / 2f, ry), Offset(cx - cw / 2f - size.width * 0.012f, ry), stroke)
    drawLine(ink, Offset(cx + cw / 2f + size.width * 0.012f, ry), Offset(cx + ruleW / 2f, ry), stroke)
}

/** Sun by day, crescent and stars by night; kept to the sides so the lockup stays clear. */
private fun DrawScope.drawSkyBodies(light: SkyLight, inks: SkylineInks, hash: Int) {
    val w = size.width
    val h = size.height
    if (light.stars > 0.01f) {
        for (i in 0 until 46) {
            val x = ((hash / (i + 1) + i * 7919) % 1000) / 1000f * w
            val y = ((hash / (i + 3) + i * 104729) % 1000) / 1000f * h * 0.6f
            drawCircle(Color.White.copy(alpha = light.stars * (0.3f + (i % 3) * 0.2f)), if (i % 7 == 0) 1.6f else 1f, Offset(x, y))
        }
    }
    val sun = light.sunProgress
    if (sun != null) {
        val x = if (sun < 0.5f) w * (0.08f + 0.3f * sun) else w * (0.77f + 0.3f * (sun - 0.5f))
        val y = h * (0.42f - 0.22f * sin(PI * sun).toFloat())
        val low = 1f - sin(PI * sun).toFloat()
        val core = lerp(Color(0xFFFFE9B8), Color(0xFFFF9A55), low.coerceIn(0f, 1f))
        drawCircle(
            Brush.radialGradient(listOf(core.copy(alpha = 0.45f), Color.Transparent), Offset(x, y), h * 0.3f),
            h * 0.3f,
            Offset(x, y)
        )
        drawCircle(core, h * 0.055f, Offset(x, y))
        drawCircle(inks.ink.copy(alpha = 0.35f), h * 0.055f, Offset(x, y), style = Stroke(h * 0.003f))
    } else if (light.night > 0.5f) {
        val nightT = (((light.hour + 24f - 19f) % 24f) / 11f).coerceIn(0f, 1f)
        val c = Offset(if (nightT < 0.5f) w * 0.12f else w * 0.86f, h * 0.2f)
        val r = h * 0.055f
        drawCircle(Brush.radialGradient(listOf(Color(0x44DDE6FF), Color.Transparent), c, r * 4f), r * 4f, c)
        val disc = Path().apply { addOval(Rect(c, r)) }
        val bite = Path().apply { addOval(Rect(c + Offset(r * 0.45f, -r * 0.2f), r * 0.88f)) }
        drawPath(Path().apply { op(disc, bite, PathOperation.Difference) }, Color(0xFFF1F3FF))
    }
}

private fun DrawScope.drawWaterLines(top: Float, vScale: Float, inks: SkylineInks) {
    val y = size.height - top * size.height * vScale
    drawRect(
        lerp(inks.paperBottom, inks.ink, 0.06f),
        topLeft = Offset(0f, y),
        size = Size(size.width, size.height - y)
    )
    var i = 0
    var yy = y + size.height * 0.02f
    while (yy < size.height - 2f) {
        val x0 = size.width * ((i * 37 % 100) / 100f) * 0.75f
        drawLine(inks.ink.copy(alpha = 0.35f), Offset(x0, yy), Offset(x0 + size.width * 0.16f, yy), strokeWidth = size.height * 0.0022f)
        yy += size.height * 0.022f
        i++
    }
}

private fun DrawScope.drawSearchlights(light: SkyLight, glows: List<Color>) {
    val evening = light.hour >= 18.3f || light.hour < 3f
    if (!evening || glows.isEmpty()) return
    val w = size.width
    val h = size.height
    listOf(-0.42f, -0.14f, 0.18f, 0.46f).forEachIndexed { i, a ->
        val base = Offset(w * (0.2f + 0.2f * i), h)
        val len = h * 1.25f
        val p = Path().apply {
            moveTo(base.x, base.y)
            lineTo(base.x + len * sin(a - 0.06f), base.y - len * cos(a - 0.06f))
            lineTo(base.x + len * sin(a + 0.06f), base.y - len * cos(a + 0.06f))
            close()
        }
        drawPath(p, Brush.radialGradient(listOf(glows[i % glows.size].copy(alpha = 0.3f * light.night), Color.Transparent), base, len))
    }
}

private fun DrawScope.drawWindows(cells: List<Rect>, light: SkyLight, hash: Int) {
    if (light.windows < 0.05f) return
    val warm = Color(0xFFFFD27A)
    val cool = Color(0xFFBFD8FF)
    var n = hash
    for (r in cells) {
        n = n * 1103515245 + 12345
        if ((n ushr 16) % 100 >= 42) continue
        val c = if ((n ushr 8) % 5 == 0) cool else warm
        val insetX = r.width * 0.22f
        val insetY = r.height * 0.24f
        drawRect(
            c.copy(alpha = light.windows * 0.85f),
            topLeft = Offset(r.left + insetX, r.top + insetY),
            size = Size(r.width - insetX * 2f, r.height - insetY * 2f)
        )
    }
}

/* ───────────────────────────── Shapes ───────────────────────────── */

/**
 * Line-art city builder in banner-relative units: x in 0..1 of the width, heights in 0..1 of
 * the banner height measured up from the bottom edge. Every building is a filled silhouette
 * plus architectural linework (floors, mullions, ribs, lattices), with faint outlines of
 * the buildings behind.
 */
internal class Skyline(private val w: Float, private val h: Float, val vScale: Float = 1f) {
    /** One path per shape: overlapping shapes in a single path cancel out (winding). */
    val fills = mutableListOf<Path>()
    /** Distant landforms (mountains, ridges) in the hazy backdrop tone. */
    val farFills = mutableListOf<Path>()
    /** Outline-only buildings far behind the skyline. */
    val ghosts = Path()
    /** Facade linework that may run past a shape's edge (clipped to it when drawn). */
    val clipped = mutableListOf<Pair<Path, Path>>()
    /** Free linework: frames, clock faces, cables, spokes. */
    val lines = Path()
    /** Shape edges. */
    val outlines = Path()
    /** Thick strokes (palms, spires, masts) with width as a fraction of banner height. */
    val heavy = mutableListOf<Pair<Path, Float>>()
    /** Window cells that light up after dark. */
    val cells = mutableListOf<Rect>()
    /** Punched-window frames (small rectangles) on masonry towers. */
    val windowFrames = Path()
    /** Height of a water line from the bottom, when the scene has one. */
    var water: Float? = null

    private var toFar = false

    private fun x(f: Float) = f * w
    private fun y(f: Float) = h - f * h * vScale
    /** Height units per one width unit (banner is wider than tall). */
    private val aspect get() = w / (h * vScale)

    fun far(block: Skyline.() -> Unit) {
        toFar = true
        block()
        toFar = false
    }

    private fun shape(p: Path, outline: Boolean = true): Path {
        if (toFar) {
            farFills += p
        } else {
            fills += p
            if (outline) outlines.addPath(p)
        }
        return p
    }

    private fun rectPath(x0: Float, b: Float, width: Float, height: Float) =
        Path().apply { addRect(Rect(x(x0), y(b + height), x(x0 + width), y(b))) }

    fun polyShape(vararg p: Float): Path {
        val path = Path()
        path.moveTo(x(p[0]), y(p[1]))
        var i = 2
        while (i < p.size) {
            path.lineTo(x(p[i]), y(p[i + 1]))
            i += 2
        }
        path.close()
        return shape(path)
    }

    fun line(x1: Float, h1: Float, x2: Float, h2: Float) {
        lines.moveTo(x(x1), y(h1))
        lines.lineTo(x(x2), y(h2))
    }

    fun heavyLine(widthFrac: Float, vararg p: Float) {
        val path = Path()
        path.moveTo(x(p[0]), y(p[1]))
        var i = 2
        while (i < p.size) {
            path.lineTo(x(p[i]), y(p[i + 1]))
            i += 2
        }
        heavy += path to widthFrac
    }

    fun spire(cx: Float, from: Float, to: Float, widthFrac: Float = 0.007f) = heavyLine(widthFrac, cx, from, cx, to)

    /** Circle outline with radius in width units, centred at a height. */
    fun ring(cx: Float, cy: Float, r: Float, into: Path = lines) {
        val rr = x(r) - x(0f)
        into.addOval(Rect(x(cx) - rr, y(cy) - rr, x(cx) + rr, y(cy) + rr))
    }

    fun disc(cx: Float, cy: Float, rx: Float, ry: Float): Path =
        shape(Path().apply { addOval(Rect(x(cx - rx), y(cy + ry), x(cx + rx), y(cy - ry))) })

    // ── Facade patterns ──────────────────────────────────────────────

    private fun hatch(clip: Path, build: Path.() -> Unit) {
        clipped += clip to Path().apply(build)
    }

    /** Floor lines every [step] (height units) across a span, clipped to [clip]. */
    fun floors(clip: Path, x0: Float, x1: Float, b: Float, top: Float, step: Float = 0.03f) = hatch(clip) {
        var hh = b + step
        while (hh < top) {
            moveTo(x(x0), y(hh)); lineTo(x(x1), y(hh)); hh += step
        }
    }

    fun mullions(clip: Path, x0: Float, x1: Float, b: Float, top: Float, step: Float) = hatch(clip) {
        var xx = x0 + step
        while (xx < x1) {
            moveTo(x(xx), y(b)); lineTo(x(xx), y(top)); xx += step
        }
    }

    /** Criss-cross lattice (Gherkin, Eiffel, faceted towers). */
    fun lattice(clip: Path, x0: Float, x1: Float, b: Float, top: Float, step: Float) = hatch(clip) {
        val span = (top - b) / aspect
        var xx = x0 - span
        while (xx < x1) {
            moveTo(x(xx), y(b)); lineTo(x(xx + span), y(top))
            moveTo(x(xx + span), y(b)); lineTo(x(xx), y(top))
            xx += step
        }
    }

    private fun windowCells(x0: Float, width: Float, b: Float, height: Float, floorStep: Float, cols: Int) {
        val rows = (height / floorStep).toInt()
        if (rows < 1 || cols < 1) return
        val cw = width / cols
        for (r in 0 until rows) for (c in 0 until cols) {
            cells += Rect(
                x(x0 + c * cw),
                y(b + (r + 1) * floorStep),
                x(x0 + (c + 1) * cw),
                y(b + r * floorStep)
            )
        }
    }

    // ── Building types ───────────────────────────────────────────────

    /** Punched-window tower: floor lines + column lines + window cells. */
    fun tower(x0: Float, width: Float, height: Float, b: Float = 0f, floorStep: Float = 0.032f, lit: Boolean = true): Path {
        val p = shape(rectPath(x0, b, width, height))
        val cols = maxOf(1, (width / 0.011f).toInt())
        val cw = width / cols
        val rows = (height / floorStep).toInt()
        if (cw * w >= 5f && floorStep * h * vScale >= 5f) {
            // Punched windows: a small frame per bay, a margin of wall round the grid.
            for (r in 0 until rows) for (c in 0 until cols) {
                val l = x(x0 + c * cw + cw * 0.22f)
                val rr = x(x0 + (c + 1) * cw - cw * 0.22f)
                val t = y(b + (r + 1) * floorStep - floorStep * 0.2f)
                val bt = y(b + r * floorStep + floorStep * 0.25f)
                if (rr - l > 1.5f && bt - t > 1.5f) windowFrames.addRect(Rect(l, t, rr, bt))
            }
        } else {
            floors(p, x0, x0 + width, b, b + height, floorStep)
            mullions(p, x0, x0 + width, b, b + height, cw)
        }
        if (lit) windowCells(x0, width, b, height, floorStep, cols)
        return p
    }

    /** Round-canopy street tree. */
    fun tree(x0: Float, height: Float) {
        heavyLine(0.007f, x0, 0f, x0, height * 0.55f)
        disc(x0, height * 0.75f, height * 0.28f / aspect * 1.4f, height * 0.25f)
    }

    /** Scalloped hedge line along the ground between two points. */
    fun shrubs(x0: Float, x1: Float, height: Float = 0.035f) {
        var xx = x0
        var i = 0
        while (xx < x1) {
            val r = height * (0.7f + (i % 3) * 0.2f)
            disc(xx, 0f, r / aspect * 1.6f, r)
            xx += r / aspect * 2.2f
            i++
        }
    }

    /** Curtain-wall glass tower: dense mullions, banding every few floors. */
    fun glass(x0: Float, width: Float, height: Float, b: Float = 0f): Path {
        val p = shape(rectPath(x0, b, width, height))
        mullions(p, x0, x0 + width, b, b + height, 0.0055f)
        floors(p, x0, x0 + width, b, b + height, 0.075f)
        windowCells(x0, width, b, height, 0.075f, maxOf(1, (width / 0.016f).toInt()))
        return p
    }

    /** Cylinder: vertical lines bunch towards the edges to read as curved. */
    fun cylinder(x0: Float, width: Float, height: Float, b: Float = 0f, floorStep: Float = 0.03f): Path {
        val p = shape(rectPath(x0, b, width, height))
        floors(p, x0, x0 + width, b, b + height, floorStep)
        hatch(p) {
            val n = 9
            for (k in 1 until n) {
                val xx = x0 + width * (1f - cos(PI * k / n).toFloat()) / 2f
                moveTo(x(xx), y(b)); lineTo(x(xx), y(b + height))
            }
        }
        windowCells(x0 + width * 0.15f, width * 0.7f, b, height, floorStep, 3)
        return p
    }

    /** Arbitrary outline with floor lines. */
    fun slab(vararg p: Float, floorStep: Float = 0.03f): Path {
        val path = polyShape(*p)
        var minX = 1f; var maxX = 0f; var minH = 1f; var maxH = 0f
        var i = 0
        while (i < p.size) {
            minX = minOf(minX, p[i]); maxX = maxOf(maxX, p[i])
            minH = minOf(minH, p[i + 1]); maxH = maxOf(maxH, p[i + 1])
            i += 2
        }
        floors(path, minX, maxX, minH, maxH, floorStep)
        return path
    }

    fun dome(cx: Float, b: Float, rx: Float, ry: Float, ribs: Int = 6): Path {
        val p = Path().apply {
            moveTo(x(cx - rx), y(b))
            arcTo(Rect(x(cx - rx), y(b + ry), x(cx + rx), y(b - ry)), 180f, 180f, false)
            close()
        }
        shape(p)
        hatch(p) {
            for (i in 1 until ribs) {
                val xx = cx - rx + 2f * rx * i / ribs
                moveTo(x(xx), y(b)); quadraticTo(x(xx + (cx - xx) * 0.35f), y(b + ry * 0.85f), x(cx), y(b + ry))
            }
            moveTo(x(cx - rx), y(b + ry * 0.35f)); lineTo(x(cx + rx), y(b + ry * 0.35f))
        }
        return p
    }

    /** Ribbed cone / tent (KICC amphitheatre, makuti roofs). */
    fun tent(cx: Float, b: Float, width: Float, height: Float, ribs: Int = 8): Path {
        val p = polyShape(cx - width / 2f, b, cx, b + height, cx + width / 2f, b)
        hatch(p) {
            for (i in 0..ribs) {
                val bx = cx - width / 2f + width * i / ribs
                moveTo(x(cx), y(b + height)); lineTo(x(bx), y(b))
            }
            for (t in listOf(0.3f, 0.6f)) {
                moveTo(x(cx - width / 2f * (1 - t)), y(b + height * t)); lineTo(x(cx + width / 2f * (1 - t)), y(b + height * t))
            }
        }
        return p
    }

    fun minaret(cx: Float, height: Float, b: Float = 0f, width: Float = 0.009f) {
        tower(cx - width / 2f, width, height, b, floorStep = 0.06f, lit = false)
        polyShape(cx - width * 0.9f, b + height, cx + width * 0.9f, b + height, cx + width * 0.9f, b + height + 0.012f, cx - width * 0.9f, b + height + 0.012f)
        polyShape(cx - width / 2f, b + height + 0.012f, cx, b + height + 0.07f, cx + width / 2f, b + height + 0.012f)
    }

    fun palm(x0: Float, height: Float, lean: Float = 0.02f) {
        val top = x0 + lean
        // Trunk: slight curve, thicker at the base.
        heavy += Path().apply {
            moveTo(x(x0), y(0f)); quadraticTo(x(x0 + lean * 0.15f), y(height * 0.55f), x(top), y(height))
        } to 0.009f
        heavy += Path().apply { moveTo(x(x0), y(0f)); lineTo(x(x0 + lean * 0.05f), y(height * 0.25f)) } to 0.013f
        // Fronds arch up and out, then droop; two short ones stand up.
        val spread = height * 0.55f / aspect
        for (d in listOf(-1f, -0.7f, -0.4f, 0.4f, 0.7f, 1f)) {
            val ex = top + d * spread
            val eh = height - height * 0.22f * abs(d)
            heavy += Path().apply {
                moveTo(x(top), y(height))
                quadraticTo(x(top + d * spread * 0.55f), y(height + height * 0.16f * (1.2f - abs(d))), x(ex), y(eh))
            } to 0.006f
        }
        for (d in listOf(-0.15f, 0.15f)) {
            heavy += Path().apply {
                moveTo(x(top), y(height))
                quadraticTo(x(top + d * spread), y(height + height * 0.18f), x(top + d * spread * 1.6f), y(height + height * 0.16f))
            } to 0.005f
        }
    }

    fun acacia(x0: Float, ground: Float, height: Float) {
        heavyLine(0.008f, x0, ground, x0 + 0.003f, ground + height * 0.7f, x0 - 0.01f, ground + height)
        heavyLine(0.006f, x0 + 0.003f, ground + height * 0.7f, x0 + 0.018f, ground + height)
        disc(x0 + 0.004f, ground + height + 0.012f, 0.045f, 0.016f)
    }

    /** Ferris wheel with spokes and capsules; radius in width units. */
    fun wheel(cx: Float, cy: Float, r: Float, spokes: Int = 16) {
        ring(cx, cy, r, lines)
        ring(cx, cy, r * 0.97f, lines)
        val rh = r * aspect
        for (i in 0 until spokes) {
            val a = 2 * PI * i / spokes
            val ex = cx + r * cos(a).toFloat()
            val ey = cy + rh * sin(a).toFloat()
            line(cx, cy, ex, ey)
            ring(ex, ey, r * 0.05f, lines)
        }
        heavyLine(0.008f, cx - r * 0.45f, 0f, cx, cy, cx + r * 0.45f, 0f)
    }

    /** Cable-stayed bridge: deck, pylon and fan of stays. */
    fun cableBridge(x0: Float, x1: Float, deck: Float, pylonX: Float, pylonTop: Float) {
        heavyLine(0.012f, x0, deck, x1, deck)
        heavyLine(0.012f, pylonX, 0f, pylonX, pylonTop)
        for (i in 1..7) {
            val t = i / 7f
            line(pylonX, pylonTop - 0.015f * i, pylonX - (pylonX - x0) * t, deck)
            line(pylonX, pylonTop - 0.015f * i, pylonX + (x1 - pylonX) * t, deck)
        }
    }

    fun hills(vararg p: Float) {
        val path = Path()
        path.moveTo(x(p[0]), h)
        path.lineTo(x(p[0]), y(p[1]))
        var i = 2
        while (i < p.size) {
            val px = p[i - 2]; val ph = p[i - 1]; val nx = p[i]; val nh = p[i + 1]
            path.quadraticTo(x((px + nx) / 2f), y(maxOf(ph, nh) + 0.04f), x(nx), y(nh))
            i += 2
        }
        path.lineTo(x(p[p.size - 2]), h)
        path.close()
        shape(path, outline = !toFar)
    }

    fun ghost(x0: Float, width: Float, height: Float) {
        ghosts.addRect(Rect(x(x0), y(height), x(x0 + width), h))
    }

    fun ghostPoly(vararg p: Float) {
        ghosts.moveTo(x(p[0]), y(p[1]))
        var i = 2
        while (i < p.size) { ghosts.lineTo(x(p[i]), y(p[i + 1])); i += 2 }
    }

    /** Faint background buildings, seeded so each event differs a little. */
    fun ghostRow(seed: Int, maxH: Float = 0.5f) {
        var x0 = 0.01f
        var i = 0
        while (x0 < 0.98f) {
            val bw = 0.04f + ((seed shr (i % 20)) + i * 13) % 5 * 0.01f
            val bh = maxH * (0.45f + ((seed shr ((i * 3) % 20)) + i * 29) % 9 * 0.06f)
            ghost(x0, bw, bh)
            if (i % 3 == 1) ghostPoly(x0 + bw * 0.3f, bh, x0 + bw * 0.3f, bh + 0.04f, x0 + bw * 0.7f, bh + 0.04f, x0 + bw * 0.7f, bh)
            x0 += bw + 0.02f + (i % 3) * 0.01f
            i++
        }
    }

    fun build(sky: EventSkyline, seed: Int) {
        when (sky) {
            EventSkyline.NAIROBI -> nairobi(seed)
            EventSkyline.MOMBASA -> mombasa(seed)
            EventSkyline.COAST -> coast()
            EventSkyline.KISUMU -> kisumu(seed)
            EventSkyline.RIFT -> rift()
            EventSkyline.KAMPALA -> kampala()
            EventSkyline.KIGALI -> kigali()
            EventSkyline.DAR -> dar(seed)
            EventSkyline.ADDIS -> addis()
            EventSkyline.LAGOS -> lagos(seed)
            EventSkyline.JOHANNESBURG -> johannesburg(seed)
            EventSkyline.CAPE_TOWN -> capeTown()
            EventSkyline.DUBAI -> dubai(seed)
            EventSkyline.LONDON -> london(seed)
            EventSkyline.PARIS -> paris(seed)
            EventSkyline.NEW_YORK -> newYork(seed)
            EventSkyline.STADIUM -> stadium(seed)
            EventSkyline.CITY -> generic(seed)
        }
        if (water == null) {
            // Street-level life along the ground, like a printed skyline.
            shrubs(0.0f, 0.06f); shrubs(0.47f, 0.52f); shrubs(0.9f, 0.99f)
        }
    }

    // ── Cities ───────────────────────────────────────────────────────

    private fun nairobi(seed: Int) {
        ghostRow(seed, 0.62f)
        ghost(0.08f, 0.06f, 0.56f); ghost(0.14f, 0.05f, 0.44f); ghost(0.36f, 0.05f, 0.5f)
        ghost(0.59f, 0.05f, 0.62f); ghostPoly(0.59f, 0.62f, 0.615f, 0.66f, 0.64f, 0.62f)
        ghost(0.8f, 0.04f, 0.68f); ghost(0.9f, 0.06f, 0.5f); ghost(0.86f, 0.05f, 0.4f)
        palm(0.02f, 0.2f, 0.01f); palm(0.055f, 0.15f, -0.012f)
        // Kenya National Archives: portico, columns, pediment.
        val archives = shape(rectPath(0.075f, 0f, 0.1f, 0.1f))
        mullions(archives, 0.075f, 0.175f, 0f, 0.085f, 0.0125f)
        line(0.075f, 0.085f, 0.175f, 0.085f)
        polyShape(0.072f, 0.1f, 0.125f, 0.135f, 0.178f, 0.1f)
        // Faceted tower (zig-zag glass facets).
        val prism = polyShape(
            0.19f, 0f, 0.19f, 0.12f, 0.204f, 0.2f, 0.19f, 0.28f, 0.204f, 0.36f, 0.19f, 0.44f, 0.198f, 0.5f,
            0.252f, 0.5f, 0.26f, 0.44f, 0.246f, 0.36f, 0.26f, 0.28f, 0.246f, 0.2f, 0.26f, 0.12f, 0.26f, 0f
        )
        floors(prism, 0.19f, 0.26f, 0f, 0.5f, 0.025f)
        windowCells(0.205f, 0.04f, 0f, 0.48f, 0.025f, 3)
        for (hh in listOf(0.12f, 0.2f, 0.28f, 0.36f, 0.44f)) line(0.19f, hh, 0.26f, hh)
        line(0.19f, 0.12f, 0.246f, 0.2f); line(0.26f, 0.12f, 0.204f, 0.2f)
        line(0.19f, 0.28f, 0.246f, 0.36f); line(0.26f, 0.28f, 0.204f, 0.36f)
        // Glass tower with crown, spire and a pod (UAP Old Mutual style).
        glass(0.272f, 0.06f, 0.5f)
        polyShape(0.272f, 0.5f, 0.302f, 0.57f, 0.332f, 0.5f)
        spire(0.302f, 0.57f, 0.76f, 0.005f)
        disc(0.302f, 0.68f, 0.008f, 0.03f)
        // City Hall clock tower.
        tower(0.342f, 0.024f, 0.4f, floorStep = 0.05f, lit = false)
        ring(0.354f, 0.365f, 0.008f)
        polyShape(0.34f, 0.4f, 0.354f, 0.45f, 0.368f, 0.4f)
        // Times Tower: shaft, upper tier, pyramid roof and mast, podium in front.
        tower(0.385f, 0.09f, 0.58f)
        tower(0.4f, 0.06f, 0.06f, b = 0.58f, floorStep = 0.02f, lit = false)
        polyShape(0.4f, 0.64f, 0.43f, 0.72f, 0.46f, 0.64f)
        line(0.415f, 0.64f, 0.43f, 0.72f); line(0.445f, 0.64f, 0.43f, 0.72f)
        spire(0.43f, 0.72f, 0.82f, 0.005f)
        tower(0.37f, 0.12f, 0.16f, floorStep = 0.04f)
        // KICC: ribbed cylinder, flared crown, helipad ring and mast.
        cylinder(0.5f, 0.05f, 0.52f, floorStep = 0.026f)
        val crown = polyShape(0.496f, 0.52f, 0.554f, 0.52f, 0.566f, 0.56f, 0.484f, 0.56f)
        mullions(crown, 0.484f, 0.566f, 0.52f, 0.56f, 0.008f)
        polyShape(0.484f, 0.56f, 0.566f, 0.56f, 0.56f, 0.575f, 0.49f, 0.575f)
        disc(0.525f, 0.585f, 0.046f, 0.01f)
        spire(0.525f, 0.595f, 0.65f, 0.004f)
        // KICC amphitheatre: ribbed cone.
        tent(0.585f, 0f, 0.14f, 0.21f, ribs = 10)
        // Britam Tower: tapering prism with a sloped top.
        val britam = polyShape(0.645f, 0f, 0.645f, 0.6f, 0.672f, 0.8f, 0.7f, 0.66f, 0.7f, 0f)
        floors(britam, 0.645f, 0.7f, 0f, 0.8f, 0.022f)
        windowCells(0.648f, 0.049f, 0f, 0.58f, 0.022f, 4)
        line(0.672f, 0f, 0.672f, 0.8f)
        // Tower with an open crown frame.
        tower(0.712f, 0.05f, 0.46f)
        line(0.712f, 0.46f, 0.712f, 0.51f); line(0.762f, 0.46f, 0.762f, 0.51f); line(0.712f, 0.51f, 0.762f, 0.51f)
        // Hilton: cylinder.
        cylinder(0.774f, 0.046f, 0.38f, floorStep = 0.028f)
        // Stepped glass tower.
        glass(0.83f, 0.056f, 0.54f)
        glass(0.842f, 0.032f, 0.06f, b = 0.54f)
        // Jamia Mosque: domes and minarets.
        tower(0.89f, 0.05f, 0.1f, floorStep = 0.05f, lit = false)
        dome(0.915f, 0.1f, 0.02f, 0.05f, ribs = 5)
        minaret(0.893f, 0.24f); minaret(0.937f, 0.24f)
        // Nyayo Monument.
        polyShape(0.962f, 0f, 0.968f, 0.4f, 0.974f, 0f)
        disc(0.968f, 0.42f, 0.006f, 0.02f)
        palm(0.985f, 0.17f, -0.01f)
        // Street level: low blocks, a matatu stage canopy, trees.
        tower(0.2f, 0.07f, 0.07f, floorStep = 0.035f)
        polyShape(0.6f, 0.06f, 0.6f, 0.08f, 0.64f, 0.09f, 0.64f, 0.06f)
        tower(0.755f, 0.07f, 0.09f, floorStep = 0.045f)
        tree(0.495f, 0.12f); tree(0.63f, 0.1f); tree(0.82f, 0.11f)
    }

    private fun mombasa(seed: Int) {
        ghostRow(seed, 0.38f)
        palm(0.02f, 0.24f, 0.012f)
        // Old Town: Swahili houses with arched doors and balconies.
        for ((x0, bh) in listOf(0.04f to 0.14f, 0.09f to 0.18f, 0.14f to 0.12f)) {
            val house = shape(rectPath(x0, 0f, 0.05f, bh))
            floors(house, x0, x0 + 0.05f, 0f, bh, 0.06f)
            ring(x0 + 0.025f, 0.03f, 0.008f)
            line(x0, bh - 0.02f, x0 + 0.05f, bh - 0.02f)
        }
        minaret(0.2f, 0.32f, width = 0.011f)
        // The Tusks: two crossed pairs over Moi Avenue, with inner edge lines.
        fun tusk(baseX: Float, tipX: Float, tipH: Float, base: Float) {
            val dir = if (tipX > baseX) 1f else -1f
            val p = Path().apply {
                moveTo(x(baseX - base / 2f), y(0f))
                quadraticTo(x(baseX - base / 2f), y(tipH * 0.95f), x(tipX), y(tipH))
                quadraticTo(x(baseX + base / 2f + dir * 0.01f), y(tipH * 0.8f), x(baseX + base / 2f), y(0f))
                close()
            }
            shape(p)
            hatch(p) {
                moveTo(x(baseX), y(0f)); quadraticTo(x(baseX + dir * 0.004f), y(tipH * 0.88f), x(tipX), y(tipH))
            }
        }
        tusk(0.25f, 0.36f, 0.52f, 0.032f); tusk(0.4f, 0.29f, 0.54f, 0.032f)
        tusk(0.45f, 0.56f, 0.52f, 0.032f); tusk(0.6f, 0.49f, 0.54f, 0.032f)
        tower(0.62f, 0.05f, 0.3f); tower(0.665f, 0.04f, 0.22f)
        // Fort Jesus: angled bastions, wall, crenellations.
        val wall = shape(rectPath(0.72f, 0f, 0.2f, 0.11f))
        hatch(wall) {
            var xx = 0.725f
            while (xx < 0.92f) { moveTo(x(xx), y(0.03f)); lineTo(x(xx + 0.012f), y(0.03f)); xx += 0.02f }
        }
        val b1 = polyShape(0.7f, 0f, 0.705f, 0.15f, 0.76f, 0.15f, 0.765f, 0f)
        floors(b1, 0.7f, 0.765f, 0f, 0.15f, 0.04f)
        val b2 = polyShape(0.88f, 0f, 0.885f, 0.15f, 0.94f, 0.15f, 0.945f, 0f)
        floors(b2, 0.88f, 0.945f, 0f, 0.15f, 0.04f)
        var cx = 0.775f
        while (cx < 0.875f) { polyShape(cx, 0.11f, cx, 0.13f, cx + 0.01f, 0.13f, cx + 0.01f, 0.11f); cx += 0.02f }
        palm(0.965f, 0.3f, -0.02f)
    }

    private fun coast() {
        water = 0.2f
        palm(0.05f, 0.62f, 0.05f); palm(0.12f, 0.5f, -0.03f); palm(0.9f, 0.58f, -0.05f); palm(0.96f, 0.44f, 0.02f)
        // Makuti-roofed bandas on the shore.
        for (x0 in listOf(0.2f, 0.29f)) {
            tower(x0, 0.06f, 0.16f, floorStep = 0.08f, lit = true)
            tent(x0 + 0.03f, 0.16f, 0.085f, 0.1f, ribs = 6)
        }
        // Dhow with a lateen sail and its seams.
        polyShape(0.52f, 0.2f, 0.64f, 0.2f, 0.62f, 0.145f, 0.54f, 0.145f)
        val sail = polyShape(0.55f, 0.21f, 0.585f, 0.52f, 0.64f, 0.23f)
        hatch(sail) {
            for (i in 1..4) { moveTo(x(0.55f + 0.009f * i), y(0.21f + 0.078f * i)); lineTo(x(0.64f - 0.004f * i), y(0.23f + 0.01f * i)) }
        }
        spire(0.585f, 0.2f, 0.54f, 0.005f)
        // Far islet.
        far { hills(0.7f, 0.2f, 0.8f, 0.27f, 0.88f, 0.2f) }
    }

    private fun kisumu(seed: Int) {
        water = 0.16f
        ghostRow(seed, 0.42f)
        tower(0.02f, 0.06f, 0.28f); glass(0.08f, 0.05f, 0.4f); tower(0.135f, 0.07f, 0.3f)
        glass(0.21f, 0.055f, 0.46f); tower(0.27f, 0.08f, 0.24f)
        // Port warehouse with gable roof.
        val shed = shape(rectPath(0.37f, 0.16f, 0.12f, 0.08f))
        mullions(shed, 0.37f, 0.49f, 0.16f, 0.24f, 0.015f)
        polyShape(0.365f, 0.24f, 0.43f, 0.29f, 0.495f, 0.24f)
        // Fishing boats with masts.
        for (bx in listOf(0.6f, 0.76f)) {
            polyShape(bx, 0.16f, bx + 0.1f, 0.16f, bx + 0.085f, 0.12f, bx + 0.015f, 0.12f)
            spire(bx + 0.05f, 0.16f, 0.36f, 0.004f)
            polyShape(bx + 0.052f, 0.34f, bx + 0.09f, 0.2f, bx + 0.052f, 0.2f)
        }
        far { hills(0.5f, 0.2f, 0.75f, 0.3f, 1f, 0.24f) }
    }

    private fun rift() {
        // Mt Longonot's crater cone and the escarpment, far back.
        far {
            hills(0f, 0.42f, 0.2f, 0.5f, 0.4f, 0.46f)
            polyShape(0.45f, 0f, 0.45f, 0.4f, 0.58f, 0.66f, 0.62f, 0.63f, 0.66f, 0.66f, 0.8f, 0.4f, 0.8f, 0f)
        }
        water = 0.1f
        hills(0f, 0.3f, 0.25f, 0.42f, 0.5f, 0.24f, 0.75f, 0.36f, 1f, 0.26f)
        acacia(0.14f, 0.4f, 0.13f); acacia(0.66f, 0.32f, 0.12f); acacia(0.88f, 0.33f, 0.1f)
    }

    private fun kampala() {
        far { hills(0f, 0.36f, 0.3f, 0.48f, 0.6f, 0.4f, 1f, 0.46f) }
        hills(0f, 0.18f, 0.35f, 0.3f, 0.6f, 0.2f, 0.85f, 0.34f, 1f, 0.26f)
        // Downtown towers on Nakasero hill.
        tower(0.1f, 0.05f, 0.26f, b = 0.2f); glass(0.16f, 0.045f, 0.34f, b = 0.22f); tower(0.21f, 0.05f, 0.24f, b = 0.24f)
        glass(0.27f, 0.04f, 0.28f, b = 0.26f)
        // Rubaga Cathedral: twin towers and a dome.
        tower(0.47f, 0.012f, 0.12f, b = 0.24f, lit = false); tower(0.51f, 0.012f, 0.12f, b = 0.24f, lit = false)
        dome(0.496f, 0.3f, 0.02f, 0.04f, ribs = 4)
        // Gaddafi National Mosque: dome, slender minaret.
        tower(0.76f, 0.1f, 0.06f, b = 0.3f, floorStep = 0.03f, lit = false)
        dome(0.81f, 0.36f, 0.045f, 0.09f, ribs = 7)
        minaret(0.9f, 0.48f, b = 0.28f, width = 0.012f)
    }

    private fun kigali() {
        far { hills(0f, 0.4f, 0.35f, 0.52f, 0.7f, 0.42f, 1f, 0.5f) }
        hills(0f, 0.22f, 0.35f, 0.3f, 0.7f, 0.24f, 1f, 0.3f)
        // Kigali City Tower.
        glass(0.14f, 0.05f, 0.42f, b = 0.24f)
        polyShape(0.14f, 0.66f, 0.165f, 0.7f, 0.19f, 0.66f)
        tower(0.2f, 0.05f, 0.24f, b = 0.26f)
        // Convention Centre: stacked beehive dome with banding.
        val kcc = Path().apply {
            moveTo(x(0.4f), y(0.24f))
            cubicTo(x(0.4f), y(0.62f), x(0.6f), y(0.62f), x(0.6f), y(0.24f))
            close()
        }
        shape(kcc)
        floors(kcc, 0.4f, 0.6f, 0.24f, 0.62f, 0.04f)
        mullions(kcc, 0.4f, 0.6f, 0.24f, 0.62f, 0.02f)
        spire(0.5f, 0.53f, 0.6f, 0.005f)
        // Radisson / hotel blocks.
        tower(0.68f, 0.07f, 0.2f, b = 0.24f); glass(0.76f, 0.05f, 0.28f, b = 0.26f)
    }

    private fun dar(seed: Int) {
        water = 0.14f
        ghostRow(seed, 0.5f)
        tower(0.02f, 0.06f, 0.3f)
        // PSPF twin towers.
        glass(0.1f, 0.05f, 0.62f); glass(0.165f, 0.05f, 0.62f)
        polyShape(0.1f, 0.62f, 0.125f, 0.67f, 0.15f, 0.62f); polyShape(0.165f, 0.62f, 0.19f, 0.67f, 0.215f, 0.62f)
        tower(0.23f, 0.06f, 0.4f)
        // Azania Front Lutheran church: gable and belfry spire.
        val church = shape(rectPath(0.31f, 0.14f, 0.08f, 0.1f))
        mullions(church, 0.31f, 0.39f, 0.14f, 0.24f, 0.016f)
        polyShape(0.305f, 0.24f, 0.35f, 0.3f, 0.395f, 0.24f)
        tower(0.38f, 0.02f, 0.2f, b = 0.14f, lit = false)
        polyShape(0.378f, 0.34f, 0.39f, 0.44f, 0.402f, 0.34f)
        // Harbour cranes with jibs and trusses.
        for (cx in listOf(0.58f, 0.76f)) {
            heavyLine(0.01f, cx, 0.14f, cx, 0.62f)
            heavyLine(0.008f, cx - 0.06f, 0.6f, cx + 0.14f, 0.6f)
            line(cx, 0.68f, cx - 0.06f, 0.6f); line(cx, 0.68f, cx + 0.14f, 0.6f)
            line(cx + 0.11f, 0.6f, cx + 0.11f, 0.4f)
            var hh = 0.18f
            while (hh < 0.6f) { line(cx - 0.006f, hh, cx + 0.006f, hh + 0.04f); hh += 0.04f }
        }
    }

    private fun addis() {
        far { hills(0f, 0.5f, 0.3f, 0.66f, 0.65f, 0.56f, 1f, 0.62f) }
        tower(0.04f, 0.06f, 0.3f); tower(0.11f, 0.05f, 0.4f)
        // Holy Trinity Cathedral: domes and twin towers.
        tower(0.18f, 0.1f, 0.14f, lit = false)
        dome(0.23f, 0.14f, 0.03f, 0.08f)
        minaret(0.185f, 0.2f); minaret(0.275f, 0.2f)
        glass(0.31f, 0.05f, 0.42f)
        // Commercial Bank of Ethiopia HQ: tall tapering glass tower.
        val cbe = polyShape(0.42f, 0f, 0.425f, 0.72f, 0.455f, 0.78f, 0.48f, 0.72f, 0.485f, 0f)
        floors(cbe, 0.42f, 0.485f, 0f, 0.78f, 0.024f)
        windowCells(0.428f, 0.05f, 0f, 0.7f, 0.024f, 4)
        mullions(cbe, 0.42f, 0.485f, 0f, 0.78f, 0.008f)
        tower(0.5f, 0.06f, 0.36f); glass(0.58f, 0.05f, 0.46f); tower(0.65f, 0.07f, 0.3f)
        tower(0.74f, 0.05f, 0.4f); glass(0.8f, 0.06f, 0.34f); tower(0.88f, 0.08f, 0.26f)
    }

    private fun lagos(seed: Int) {
        water = 0.12f
        ghostRow(seed, 0.6f)
        tower(0.0f, 0.05f, 0.4f); glass(0.05f, 0.05f, 0.58f); tower(0.11f, 0.06f, 0.46f)
        // NECOM House: tall tower with a mast.
        tower(0.18f, 0.05f, 0.7f); spire(0.205f, 0.7f, 0.86f, 0.005f)
        glass(0.24f, 0.06f, 0.5f); tower(0.31f, 0.05f, 0.4f)
        // Civic Centre: low wide glass block.
        glass(0.37f, 0.1f, 0.2f)
        // Lekki-Ikoyi Link Bridge: cable-stayed with a single pylon.
        cableBridge(0.48f, 1f, 0.18f, 0.74f, 0.66f)
    }

    private fun johannesburg(seed: Int) {
        ghostRow(seed, 0.55f)
        tower(0f, 0.06f, 0.32f); glass(0.07f, 0.05f, 0.46f)
        // Hillbrow Tower: shaft, stacked pod rings, mast.
        tower(0.17f, 0.016f, 0.62f, floorStep = 0.06f, lit = false)
        for (hh in listOf(0.52f, 0.56f, 0.6f)) polyShape(0.158f, hh, 0.198f, hh, 0.194f, hh + 0.025f, 0.162f, hh + 0.025f)
        spire(0.178f, 0.64f, 0.84f, 0.005f)
        tower(0.22f, 0.06f, 0.4f)
        // Ponte City: big cylinder with its crown sign band.
        cylinder(0.3f, 0.1f, 0.54f, floorStep = 0.022f)
        polyShape(0.296f, 0.54f, 0.404f, 0.54f, 0.404f, 0.58f, 0.296f, 0.58f)
        // Carlton Centre: tall slab.
        tower(0.43f, 0.07f, 0.66f, floorStep = 0.022f)
        glass(0.51f, 0.05f, 0.44f)
        // Nelson Mandela Bridge.
        cableBridge(0.58f, 0.98f, 0.12f, 0.72f, 0.42f)
        tower(0.86f, 0.06f, 0.34f)
    }

    private fun capeTown() {
        far {
            polyShape(
                0f, 0f, 0f, 0.36f, 0.1f, 0.52f, 0.16f, 0.62f, 0.2f, 0.66f, 0.66f, 0.68f, 0.72f, 0.6f,
                0.82f, 0.46f, 0.9f, 0.52f, 0.95f, 0.42f, 1f, 0.3f, 1f, 0f
            )
        }
        tower(0.04f, 0.05f, 0.2f); glass(0.1f, 0.05f, 0.3f)
        // Portside Tower.
        glass(0.17f, 0.05f, 0.44f)
        polyShape(0.17f, 0.44f, 0.195f, 0.48f, 0.22f, 0.44f)
        // V&A clock tower.
        tower(0.27f, 0.025f, 0.22f, floorStep = 0.05f, lit = false)
        ring(0.2825f, 0.19f, 0.008f)
        polyShape(0.266f, 0.22f, 0.2825f, 0.27f, 0.299f, 0.22f)
        // Cape Town Stadium: bowl with a mesh skin.
        val bowl = Path().apply {
            moveTo(x(0.35f), y(0f)); lineTo(x(0.36f), y(0.2f))
            quadraticTo(x(0.5f), y(0.27f), x(0.64f), y(0.2f)); lineTo(x(0.65f), y(0f)); close()
        }
        shape(bowl)
        lattice(bowl, 0.35f, 0.65f, 0f, 0.27f, 0.02f)
        // Wheel at the Waterfront.
        wheel(0.78f, 0.24f, 0.07f)
        tower(0.9f, 0.06f, 0.24f)
    }

    private fun dubai(seed: Int) {
        ghostRow(seed, 0.55f)
        tower(0f, 0.05f, 0.34f); glass(0.06f, 0.05f, 0.44f)
        // Cayan Tower: twisting facade.
        val cayan = polyShape(0.13f, 0f, 0.13f, 0.56f, 0.175f, 0.58f, 0.175f, 0f)
        lattice(cayan, 0.13f, 0.175f, 0f, 0.58f, 0.03f)
        // Burj Khalifa: setbacks and needle.
        val tiers = listOf(Triple(0.27f, 0.11f, 0.3f), Triple(0.282f, 0.086f, 0.44f), Triple(0.294f, 0.062f, 0.58f), Triple(0.306f, 0.038f, 0.7f), Triple(0.315f, 0.02f, 0.8f))
        for ((x0, bw, bh) in tiers) {
            val t = shape(rectPath(x0, 0f, bw, bh))
            mullions(t, x0, x0 + bw, 0f, bh, 0.006f)
        }
        polyShape(0.315f, 0.8f, 0.325f, 0.98f, 0.335f, 0.8f)
        windowCells(0.282f, 0.086f, 0f, 0.44f, 0.04f, 6)
        // Emirates Towers: twin triangular tops.
        for ((x0, bh) in listOf(0.43f to 0.6f, 0.5f to 0.52f)) {
            val tw = polyShape(x0, 0f, x0, bh, x0 + 0.05f, bh + 0.07f, x0 + 0.05f, 0f)
            floors(tw, x0, x0 + 0.05f, 0f, bh + 0.07f, 0.026f)
            windowCells(x0, 0.05f, 0f, bh, 0.026f, 4)
        }
        // Dubai Frame.
        polyShape(0.6f, 0f, 0.6f, 0.4f, 0.69f, 0.4f, 0.69f, 0f, 0.675f, 0f, 0.675f, 0.37f, 0.615f, 0.37f, 0.615f, 0f)
        // Burj Al Arab: sail with ribs and mast.
        val sail = Path().apply {
            moveTo(x(0.76f), y(0f))
            quadraticTo(x(0.74f), y(0.5f), x(0.79f), y(0.6f))
            lineTo(x(0.82f), y(0.58f)); lineTo(x(0.82f), y(0f)); close()
        }
        shape(sail)
        hatch(sail) {
            for (i in 1..6) { moveTo(x(0.75f + i * 0.004f), y(i * 0.085f)); lineTo(x(0.82f), y(i * 0.085f - 0.02f)) }
        }
        spire(0.82f, 0f, 0.7f, 0.006f)
        tower(0.86f, 0.06f, 0.3f); glass(0.93f, 0.06f, 0.38f)
    }

    private fun london(seed: Int) {
        ghostRow(seed, 0.4f)
        // Elizabeth Tower and the Palace of Westminster.
        val palace = shape(rectPath(0f, 0f, 0.13f, 0.16f))
        mullions(palace, 0f, 0.13f, 0f, 0.16f, 0.008f)
        var px = 0.004f
        while (px < 0.13f) { polyShape(px, 0.16f, px + 0.004f, 0.19f, px + 0.008f, 0.16f); px += 0.02f }
        tower(0.13f, 0.03f, 0.5f, floorStep = 0.04f, lit = false)
        polyShape(0.126f, 0.5f, 0.164f, 0.5f, 0.164f, 0.56f, 0.126f, 0.56f)
        ring(0.145f, 0.53f, 0.011f)
        polyShape(0.126f, 0.56f, 0.145f, 0.68f, 0.164f, 0.56f)
        spire(0.145f, 0.68f, 0.73f, 0.004f)
        // St Paul's Cathedral: drum, dome, lantern.
        tower(0.2f, 0.09f, 0.14f, floorStep = 0.035f, lit = false)
        val drum = shape(rectPath(0.225f, 0.14f, 0.04f, 0.06f))
        mullions(drum, 0.225f, 0.265f, 0.14f, 0.2f, 0.006f)
        dome(0.245f, 0.2f, 0.028f, 0.09f)
        polyShape(0.241f, 0.29f, 0.249f, 0.29f, 0.245f, 0.34f)
        // The Shard: faceted needle.
        val shard = polyShape(0.33f, 0f, 0.37f, 0.86f, 0.385f, 0.8f, 0.42f, 0f)
        floors(shard, 0.33f, 0.42f, 0f, 0.86f, 0.03f)
        windowCells(0.35f, 0.05f, 0f, 0.5f, 0.03f, 4)
        line(0.37f, 0.86f, 0.36f, 0f); line(0.385f, 0.8f, 0.39f, 0f)
        // The Gherkin: bullet with diagonal lattice.
        val gherkin = Path().apply {
            moveTo(x(0.45f), y(0f))
            cubicTo(x(0.43f), y(0.4f), x(0.46f), y(0.58f), x(0.475f), y(0.6f))
            cubicTo(x(0.49f), y(0.58f), x(0.52f), y(0.4f), x(0.5f), y(0f)); close()
        }
        shape(gherkin)
        lattice(gherkin, 0.43f, 0.52f, 0f, 0.6f, 0.014f)
        // Tower Bridge: two gothic towers and high walkways.
        for (tx in listOf(0.56f, 0.66f)) {
            tower(tx, 0.03f, 0.36f, floorStep = 0.06f, lit = false)
            polyShape(tx - 0.003f, 0.36f, tx + 0.015f, 0.44f, tx + 0.033f, 0.36f)
        }
        polyShape(0.59f, 0.3f, 0.66f, 0.3f, 0.66f, 0.33f, 0.59f, 0.33f)
        heavyLine(0.008f, 0.52f, 0.1f, 0.72f, 0.1f)
        line(0.52f, 0.2f, 0.56f, 0.1f); line(0.72f, 0.2f, 0.69f, 0.1f)
        // London Eye.
        wheel(0.84f, 0.32f, 0.075f, spokes = 18)
    }

    private fun paris(seed: Int) {
        ghostRow(seed, 0.3f)
        // Sacré-Cœur on its hill.
        far { hills(0f, 0.22f, 0.15f, 0.3f, 0.3f, 0.22f) }
        dome(0.15f, 0.3f, 0.025f, 0.07f); dome(0.12f, 0.3f, 0.012f, 0.04f); dome(0.18f, 0.3f, 0.012f, 0.04f)
        // Haussmann rows: mansard roofs, dormers, balconies.
        for ((x0, bw) in listOf(0f to 0.09f, 0.09f to 0.08f, 0.62f to 0.09f, 0.71f to 0.1f, 0.81f to 0.09f, 0.9f to 0.1f)) {
            val blk = shape(rectPath(x0, 0f, bw, 0.18f))
            floors(blk, x0, x0 + bw, 0f, 0.18f, 0.04f)
            mullions(blk, x0, x0 + bw, 0f, 0.18f, 0.012f)
            windowCells(x0, bw, 0f, 0.16f, 0.04f, (bw / 0.012f).toInt())
            polyShape(x0, 0.18f, x0 + 0.01f, 0.23f, x0 + bw - 0.01f, 0.23f, x0 + bw, 0.18f)
        }
        // Eiffel Tower: splayed legs with an arch, platforms, lattice, antenna.
        val legs = Path().apply {
            moveTo(x(0.3f), y(0f)); lineTo(x(0.37f), y(0.3f)); lineTo(x(0.43f), y(0.3f)); lineTo(x(0.5f), y(0f))
            lineTo(x(0.46f), y(0f)); quadraticTo(x(0.4f), y(0.2f), x(0.34f), y(0f)); close()
        }
        shape(legs)
        lattice(legs, 0.3f, 0.5f, 0f, 0.3f, 0.016f)
        polyShape(0.355f, 0.3f, 0.445f, 0.3f, 0.445f, 0.33f, 0.355f, 0.33f)
        val upper = polyShape(0.37f, 0.33f, 0.396f, 0.78f, 0.404f, 0.78f, 0.43f, 0.33f)
        lattice(upper, 0.37f, 0.43f, 0.33f, 0.78f, 0.012f)
        polyShape(0.383f, 0.56f, 0.417f, 0.56f, 0.415f, 0.58f, 0.385f, 0.58f)
        spire(0.4f, 0.78f, 0.88f, 0.004f)
        // Arc de Triomphe.
        val arc = Path().apply {
            moveTo(x(0.52f), y(0f)); lineTo(x(0.52f), y(0.2f)); lineTo(x(0.6f), y(0.2f)); lineTo(x(0.6f), y(0f))
            lineTo(x(0.575f), y(0f)); lineTo(x(0.575f), y(0.09f)); quadraticTo(x(0.56f), y(0.13f), x(0.545f), y(0.09f))
            lineTo(x(0.545f), y(0f)); close()
        }
        shape(arc)
        line(0.52f, 0.17f, 0.6f, 0.17f)
    }

    private fun newYork(seed: Int) {
        water = 0.1f
        ghostRow(seed, 0.6f)
        // Brooklyn Bridge: gothic towers, cables.
        for (tx in listOf(0.03f, 0.2f)) {
            val t = polyShape(tx, 0.1f, tx, 0.34f, tx + 0.03f, 0.34f, tx + 0.03f, 0.1f)
            hatch(t) {
                moveTo(x(tx + 0.008f), y(0.2f)); lineTo(x(tx + 0.008f), y(0.3f))
                moveTo(x(tx + 0.022f), y(0.2f)); lineTo(x(tx + 0.022f), y(0.3f))
            }
        }
        heavyLine(0.008f, 0f, 0.14f, 0.28f, 0.14f)
        lines.moveTo(x(0.045f), y(0.34f)); lines.quadraticTo(x(0.13f), y(0.12f), x(0.215f), y(0.34f))
        var cx = 0.06f
        while (cx < 0.2f) { line(cx, 0.14f, cx, 0.14f + 0.2f * abs(cx - 0.13f) / 0.085f * 0.9f); cx += 0.012f }
        tower(0.3f, 0.05f, 0.46f); glass(0.35f, 0.04f, 0.54f)
        // Empire State Building: setbacks, mast.
        tower(0.4f, 0.1f, 0.42f)
        tower(0.415f, 0.07f, 0.12f, b = 0.42f)
        tower(0.43f, 0.04f, 0.08f, b = 0.54f, floorStep = 0.02f)
        polyShape(0.44f, 0.62f, 0.45f, 0.66f, 0.46f, 0.62f)
        spire(0.45f, 0.66f, 0.82f, 0.005f)
        // Chrysler Building: stacked sunburst arches and needle.
        tower(0.53f, 0.05f, 0.48f)
        for ((i, bh) in listOf(0.48f, 0.53f, 0.57f).withIndex()) {
            val inset = i * 0.007f
            val a = polyShape(0.53f + inset, bh, 0.555f, bh + 0.07f, 0.58f - inset, bh)
            hatch(a) { moveTo(x(0.53f + inset + 0.005f), y(bh)); quadraticTo(x(0.555f), y(bh + 0.05f), x(0.575f - inset), y(bh)) }
        }
        spire(0.555f, 0.62f, 0.76f, 0.004f)
        // One World Trade Center: chamfered taper and spire.
        val wtc = polyShape(0.66f, 0f, 0.66f, 0.18f, 0.68f, 0.74f, 0.72f, 0.74f, 0.74f, 0.18f, 0.74f, 0f)
        line(0.66f, 0.18f, 0.72f, 0.74f); line(0.74f, 0.18f, 0.68f, 0.74f)
        floors(wtc, 0.66f, 0.74f, 0.18f, 0.74f, 0.04f)
        windowCells(0.672f, 0.056f, 0f, 0.6f, 0.04f, 4)
        spire(0.7f, 0.74f, 0.94f, 0.004f)
        tower(0.77f, 0.06f, 0.5f); glass(0.84f, 0.05f, 0.42f); tower(0.9f, 0.08f, 0.36f)
    }

    private fun stadium(seed: Int) {
        ghostRow(seed, 0.3f)
        // Bowl, roof truss and stands.
        val bowl = Path().apply {
            moveTo(x(0.12f), y(0f)); lineTo(x(0.14f), y(0.3f))
            quadraticTo(x(0.5f), y(0.42f), x(0.86f), y(0.3f)); lineTo(x(0.88f), y(0f)); close()
        }
        shape(bowl)
        floors(bowl, 0.12f, 0.88f, 0f, 0.42f, 0.05f)
        lattice(bowl, 0.12f, 0.88f, 0.26f, 0.42f, 0.03f)
        windowCells(0.18f, 0.64f, 0.04f, 0.16f, 0.04f, 24)
        // Floodlight masts with lamp banks.
        for (mx in listOf(0.2f, 0.8f)) {
            heavyLine(0.008f, mx, 0.3f, mx, 0.72f)
            val lamp = polyShape(mx - 0.035f, 0.72f, mx + 0.035f, 0.72f, mx + 0.035f, 0.79f, mx - 0.035f, 0.79f)
            floors(lamp, mx - 0.035f, mx + 0.035f, 0.72f, 0.79f, 0.023f)
            mullions(lamp, mx - 0.035f, mx + 0.035f, 0.72f, 0.79f, 0.0117f)
        }
        tower(0f, 0.08f, 0.2f); tower(0.9f, 0.1f, 0.22f)
    }

    private fun generic(seed: Int) {
        ghostRow(seed, 0.6f)
        var x0 = 0f
        var i = 0
        while (x0 < 1f) {
            val bw = 0.045f + ((seed shr (i % 24)) + i * 13) % 6 * 0.01f
            val bh = 0.24f + ((seed shr ((i * 3) % 24)) + i * 29) % 10 * 0.045f
            if (i % 2 == 0) tower(x0, bw, bh) else glass(x0, bw, bh)
            if (i % 5 == 2) spire(x0 + bw / 2f, bh, bh + 0.1f, 0.004f)
            if (i % 4 == 3) polyShape(x0, bh, x0 + bw / 2f, bh + 0.05f, x0 + bw, bh)
            x0 += bw + 0.006f
            i++
        }
    }
}
