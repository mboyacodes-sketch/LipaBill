package com.lipabill.app.ui.tickets

import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Train
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lipabill.app.data.model.Ticket
import com.lipabill.app.data.model.firstNotesValue
import com.lipabill.app.data.model.notesValue
import com.lipabill.app.ui.privacy.recordingPrivacyCover
import com.lipabill.app.ui.theme.Accent
import com.lipabill.app.ui.theme.Expense
import com.lipabill.app.ui.theme.GeometricSansFamily
import com.lipabill.app.ui.theme.Ink
import com.lipabill.app.ui.theme.LocalLipaColors
import com.lipabill.app.ui.theme.Mute
import com.lipabill.app.ui.theme.OnAccent
import com.lipabill.app.ui.theme.SoftBlue
import com.lipabill.app.ui.util.formatTravelClock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val StubTabHeight = 20.dp
private val StubCorner = 26.dp
private val StubNotchRadius = 13.dp
private val StubBarcodeBlock = 100.dp
private val StubUsedFooter = 28.dp

/** Stub fill — same travel navy as the rest of LipaBill. */
val SgrTicketFill: Color
    @Composable @ReadOnlyComposable get() = Accent

/** Text / icons that sit on [SgrTicketFill]. */
val SgrOnTicket: Color
    @Composable @ReadOnlyComposable get() = OnAccent

/**
 * SGR / Madaraka boarding pass, matching the boarding-pass template:
 * huge origin code top-right with meta to its left, a dashed arc through a
 * train glyph, huge destination code bottom-left with meta to its right, then
 * a tabbed stub with side notches cut through, a passenger grid and a barcode.
 */
@Composable
fun SgrTicketStyleCard(
    ticket: Ticket,
    pdf417: ImageBitmap?,
    modifier: Modifier = Modifier,
    isUsed: Boolean = false,
    showViewBoardingQr: Boolean = false,
    onViewBoardingQr: (() -> Unit)? = null
) {
    val model = remember(ticket) { ticket.toSgrTicketUiModel() }
    val ticketFill = SgrTicketFill
    val onTicket = SgrOnTicket

    Column(
        modifier = modifier
            .fillMaxWidth()
            .fadeWhenUsed(isUsed)
    ) {
        SgrRouteHero(
            model = model,
            codeInk = if (isUsed) Mute else Ink,
            metaInk = if (isUsed) Mute else Ink,
            glyphColor = if (isUsed) Mute else Accent,
            dashColor = Mute.copy(alpha = if (isUsed) 0.35f else 0.6f),
            haloColor = LocalLipaColors.current.canvas,
            animateRoute = !isUsed
        )

        Spacer(modifier = Modifier.height(14.dp))

        SgrStubCard(
            model = model,
            pdf417 = pdf417,
            ticketFill = ticketFill,
            onTicket = onTicket,
            onTicketMute = onTicket.copy(alpha = 0.72f),
            isUsed = isUsed,
            showViewBoardingQr = showViewBoardingQr,
            onViewBoardingQr = onViewBoardingQr
        )
    }
}

@Composable
private fun SgrRouteHero(
    model: SgrTicketUiModel,
    codeInk: Color,
    metaInk: Color,
    glyphColor: Color,
    dashColor: Color,
    haloColor: Color,
    animateRoute: Boolean
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        // Scale the station codes with width so three letters fill ~60% like the template.
        val codeSize: TextUnit = (maxWidth.value * 0.25f).coerceIn(72f, 112f).sp
        Column(modifier = Modifier.fillMaxWidth()) {
            // Origin: meta block on the left of the code, both hugging the right edge.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SgrHeroMeta(
                    caption = "From",
                    city = model.fromCity,
                    date = model.heroDepartDate,
                    time = model.departTime,
                    ink = metaInk,
                    alignEnd = true,
                    modifier = Modifier.padding(top = 6.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                SgrHeroCode(model.fromCode, codeInk, codeSize)
            }

            SgrRouteArc(
                fromCity = model.fromCity,
                toCity = model.toCity,
                glyphColor = glyphColor,
                dashColor = dashColor,
                haloColor = haloColor,
                animate = animateRoute,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(104.dp)
            )

            // Destination: code hugging the left edge, meta block to its right.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SgrHeroCode(model.toCode, codeInk, codeSize)
                Spacer(modifier = Modifier.width(12.dp))
                SgrHeroMeta(
                    caption = "To",
                    city = model.toCity,
                    date = model.heroArriveDate,
                    time = model.arriveTime,
                    ink = metaInk,
                    alignEnd = false,
                    modifier = Modifier.padding(top = 18.dp)
                )
            }
        }
    }
}

@Composable
private fun SgrHeroCode(code: String, ink: Color, size: TextUnit) {
    Text(
        text = code,
        color = ink,
        fontSize = size,
        lineHeight = size,
        fontFamily = GeometricSansFamily,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.06f * size.value).sp,
        maxLines = 1,
        softWrap = false
    )
}

@Composable
private fun SgrHeroMeta(
    caption: String,
    city: String,
    date: String,
    time: String,
    ink: Color,
    alignEnd: Boolean,
    modifier: Modifier = Modifier
) {
    val align = if (alignEnd) TextAlign.End else TextAlign.Start
    Column(
        modifier = modifier,
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start
    ) {
        Text(
            text = caption.uppercase(),
            color = ink.copy(alpha = 0.55f),
            fontSize = 10.sp,
            fontFamily = GeometricSansFamily,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.2.sp,
            textAlign = align,
            maxLines = 1
        )
        Text(
            text = city,
            color = ink,
            fontSize = 12.sp,
            fontFamily = GeometricSansFamily,
            fontWeight = FontWeight.Bold,
            textAlign = align,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = date,
            color = ink,
            fontSize = 17.sp,
            fontFamily = GeometricSansFamily,
            fontWeight = FontWeight.SemiBold,
            textAlign = align,
            maxLines = 1
        )
        Text(
            text = time,
            color = ink.copy(alpha = 0.8f),
            fontSize = 13.sp,
            fontFamily = GeometricSansFamily,
            fontWeight = FontWeight.Medium,
            textAlign = align,
            maxLines = 1
        )
    }
}

/**
 * Dashed S-curve from the origin code down to the destination code. Direction reads at a
 * glance: a dot marks where the trip starts, chevrons point along the line, an arrowhead
 * lands on the destination, and the dashes drift towards it (static when the pass is used).
 */
@Composable
private fun SgrRouteArc(
    fromCity: String,
    toCity: String,
    glyphColor: Color,
    dashColor: Color,
    haloColor: Color,
    animate: Boolean,
    modifier: Modifier = Modifier
) {
    val train = rememberVectorPainter(Icons.Filled.Train)
    val density = LocalDensity.current
    val dashOn = with(density) { 6.dp.toPx() }
    val dashOff = with(density) { 5.dp.toPx() }
    val flow = if (animate) {
        rememberInfiniteTransition(label = "sgrRouteFlow").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 900, easing = LinearEasing)),
            label = "sgrRouteFlowPhase"
        )
    } else {
        null
    }
    val routeLabel = "Travel from $fromCity to $toCity"
    Spacer(
        modifier = modifier
            .semantics { contentDescription = routeLabel }
            .drawWithCache {
                // Route geometry depends only on size: measured once, not on every animation frame.
                val w = size.width
                val h = size.height
                val path = Path().apply {
                    moveTo(w * 0.80f, 6.dp.toPx())
                    cubicTo(w * 0.80f, h * 0.42f, w * 0.64f, h * 0.48f, w * 0.52f, h * 0.48f)
                    cubicTo(w * 0.36f, h * 0.48f, w * 0.24f, h * 0.58f, w * 0.24f, h - 2.dp.toPx())
                }
                val measure = PathMeasure().apply { setPath(path, false) }
                val length = measure.length
                val arrowLen = 11.dp.toPx()
                val dashed = Path().also { measure.getSegment(0f, length - arrowLen * 0.6f, it, true) }
                val dashWidth = 1.8.dp.toPx()

                fun unit(o: Offset): Offset {
                    val d = o.getDistance()
                    return if (d == 0f) Offset(0f, 1f) else o / d
                }

                // Chevrons pointing along the direction of travel: (centre, tip, two arm ends).
                val chevron = 5.dp.toPx()
                val chevrons = floatArrayOf(0.24f, 0.76f).map { fraction ->
                    val at = length * fraction
                    val p = measure.getPosition(at)
                    val t = unit(measure.getTangent(at))
                    val n = Offset(-t.y, t.x)
                    val back = p - t * (chevron * 0.6f)
                    listOf(p, p + t * (chevron * 0.6f), back + n * chevron, back - n * chevron)
                }
                val chevronStroke = 2.dp.toPx()

                val start = measure.getPosition(0f)
                val endPoint = measure.getPosition(length)
                val endDir = unit(measure.getTangent(length))
                val endNormal = Offset(-endDir.y, endDir.x)
                val base = endPoint - endDir * arrowLen
                val arrow = Path().apply {
                    moveTo(endPoint.x, endPoint.y)
                    lineTo((base + endNormal * (arrowLen * 0.55f)).x, (base + endNormal * (arrowLen * 0.55f)).y)
                    lineTo((base - endNormal * (arrowLen * 0.55f)).x, (base - endNormal * (arrowLen * 0.55f)).y)
                    close()
                }
                val mid = measure.getPosition(length * 0.5f)
                val glyph = 30.dp.toPx()
                val icon = 26.dp.toPx()
                val tint = ColorFilter.tint(glyphColor)

                onDrawBehind {
                    // Negative phase walks the dash pattern from origin towards destination.
                    val phase = -(flow?.value ?: 0f) * (dashOn + dashOff)
                    drawPath(
                        path = dashed,
                        color = dashColor,
                        style = Stroke(
                            width = dashWidth,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashOn, dashOff), phase)
                        )
                    )
                    for ((p, tip, armA, armB) in chevrons) {
                        drawCircle(haloColor, radius = chevron * 1.5f, center = p)
                        drawLine(glyphColor, armA, tip, chevronStroke, StrokeCap.Round)
                        drawLine(glyphColor, armB, tip, chevronStroke, StrokeCap.Round)
                    }
                    // Origin: ringed dot where the trip starts.
                    drawCircle(haloColor, radius = 7.dp.toPx(), center = start)
                    drawCircle(glyphColor, radius = 5.dp.toPx(), center = start, style = Stroke(2.dp.toPx()))
                    drawCircle(glyphColor, radius = 2.2.dp.toPx(), center = start)
                    // Destination: solid arrowhead on the end of the line.
                    drawPath(arrow, color = glyphColor)
                    // Train sits on the true midpoint of the route.
                    drawCircle(haloColor, radius = glyph / 2f, center = mid)
                    translate(left = mid.x - icon / 2f, top = mid.y - icon / 2f) {
                        with(train) { draw(Size(icon, icon), colorFilter = tint) }
                    }
                }
            }
    )
}

@Composable
private fun SgrStubCard(
    model: SgrTicketUiModel,
    pdf417: ImageBitmap?,
    ticketFill: Color,
    onTicket: Color,
    onTicketMute: Color,
    isUsed: Boolean,
    showViewBoardingQr: Boolean,
    onViewBoardingQr: (() -> Unit)?
) {
    val notchFromBottom = StubBarcodeBlock + (if (isUsed) StubUsedFooter else 0.dp)
    val shape = remember(notchFromBottom) {
        SgrStubShape(
            tabHeight = StubTabHeight,
            corner = StubCorner,
            notchRadius = StubNotchRadius,
            notchCenterFromBottom = notchFromBottom
        )
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ticketFill)
    ) {
        // Brand sits in the raised tab, like "IPSUM ✈ Flight" on the template.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "MADARAKA",
                color = onTicket,
                fontSize = 15.sp,
                fontFamily = GeometricSansFamily,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )
            Icon(
                imageVector = Icons.Filled.Train,
                contentDescription = null,
                tint = SoftBlue,
                modifier = Modifier
                    .padding(horizontal = 6.dp)
                    .size(19.dp)
            )
            Text(
                text = "SGR",
                color = onTicket,
                fontSize = 15.sp,
                fontFamily = GeometricSansFamily,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (isUsed) {
            Text(
                text = "Used",
                color = onTicketMute,
                fontSize = 11.sp,
                fontFamily = GeometricSansFamily,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp)
            )
        }

        // Two-column grid; both columns left-aligned like the template.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 20.dp, top = 14.dp, bottom = 14.dp)
        ) {
            Column(
                modifier = Modifier.weight(1.15f),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                SgrStubField("Name Passenger", model.passenger, onTicket, onTicketMute, cover = true)
                SgrStubField("Train", model.train, onTicket, onTicketMute, cover = model.train != "—")
                SgrStubField("Coach", model.coach, onTicket, onTicketMute, cover = model.coach != "—")
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                SgrStubField("Booking ref", model.bookingRef, onTicket, onTicketMute, cover = true)
                SgrStubField("Seat", model.seat, onTicket, onTicketMute, cover = model.seat != "—")
                SgrStubField("Class", model.travelClass, onTicket, onTicketMute)
            }
        }

        if (showViewBoardingQr && onViewBoardingQr != null) {
            TextButton(
                onClick = onViewBoardingQr,
                enabled = !isUsed,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "View boarding QR",
                    color = onTicket,
                    fontFamily = GeometricSansFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
            }
        }

        // Tear line between the notches cut by SgrStubShape.
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .padding(horizontal = StubNotchRadius + 8.dp)
        ) {
            drawLine(
                color = onTicket.copy(alpha = 0.35f),
                start = Offset(0f, 0f),
                end = Offset(size.width, 0f),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(StubBarcodeBlock)
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            if (pdf417 != null) {
                Image(
                    bitmap = pdf417,
                    contentDescription = "SGR booking barcode",
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .recordingPrivacyCover()
                        .graphicsLayer { alpha = if (isUsed) UsedPassAlpha else 1f }
                )
            } else {
                Text(
                    text = model.bookingRef,
                    color = onTicket,
                    fontSize = 16.sp,
                    fontFamily = GeometricSansFamily,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.recordingPrivacyCover()
                )
            }
        }

        if (isUsed) {
            Text(
                text = "Stub detached",
                color = Expense,
                fontSize = 11.sp,
                fontFamily = GeometricSansFamily,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(StubUsedFooter)
            )
        }
    }
}

@Composable
private fun SgrStubField(
    label: String,
    value: String,
    onTicket: Color,
    onTicketMute: Color,
    cover: Boolean = false
) {
    Column {
        Text(
            text = label,
            color = onTicketMute,
            fontSize = 12.sp,
            fontFamily = GeometricSansFamily,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            color = onTicket,
            fontSize = if (value.length > 10) 17.sp else 21.sp,
            fontFamily = GeometricSansFamily,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.recordingPrivacyCover(cover)
        )
    }
}

/**
 * Ticket stub outline: a raised centre tab for the brand, rounded corners, and
 * two side notches cut right through so whatever is behind shows (no stage color needed).
 */
private class SgrStubShape(
    private val tabHeight: Dp,
    private val corner: Dp,
    private val notchRadius: Dp,
    private val notchCenterFromBottom: Dp,
    private val tabWidthFraction: Float = 0.54f
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val t = with(density) { tabHeight.toPx() }
        val r = with(density) { corner.toPx() }
        val nr = with(density) { notchRadius.toPx() }
        val ny = size.height - with(density) { notchCenterFromBottom.toPx() }
        val f = t / 2f
        val w = size.width
        val h = size.height
        val tabL = (w - w * tabWidthFraction) / 2f
        val tabR = w - tabL

        val body = Path().apply {
            moveTo(0f, t + r)
            quadraticTo(0f, t, r, t)
            lineTo(tabL - f, t)
            quadraticTo(tabL, t, tabL, t - f)
            quadraticTo(tabL, 0f, tabL + f, 0f)
            lineTo(tabR - f, 0f)
            quadraticTo(tabR, 0f, tabR, f)
            quadraticTo(tabR, t, tabR + f, t)
            lineTo(w - r, t)
            quadraticTo(w, t, w, t + r)
            lineTo(w, h - r)
            quadraticTo(w, h, w - r, h)
            lineTo(r, h)
            quadraticTo(0f, h, 0f, h - r)
            close()
        }
        val notches = Path().apply {
            addOval(Rect(center = Offset(0f, ny), radius = nr))
            addOval(Rect(center = Offset(w, ny), radius = nr))
        }
        val cut = Path().apply { op(body, notches, PathOperation.Difference) }
        return Outline.Generic(cut)
    }
}

data class SgrTicketUiModel(
    val passenger: String,
    val departTime: String,
    val arriveTime: String,
    val heroDepartDate: String,
    val heroArriveDate: String,
    val fromCode: String,
    val toCode: String,
    val fromCity: String,
    val toCity: String,
    val bookingRef: String,
    val coach: String,
    val train: String,
    val seat: String,
    val travelClass: String
)

internal fun Ticket.toSgrTicketUiModel(): SgrTicketUiModel {
    val notes = notes.orEmpty()

    val origin = notes.notesValue("Origin") ?: venue?.substringBefore("→")?.trim()
    val destination = notes.notesValue("Destination") ?: venue?.substringAfter("→")?.trim()
    val fromParts = splitCityStation(origin)
    val toParts = splitCityStation(destination)

    val zoned = startsAtMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()) }
    val heroFmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

    val departTime = zoned?.let { formatTravelClock(it) }
        ?: notes.firstNotesValue("Departure time", "Time")
        ?: "—"

    val durationMinutes = estimateSgrDurationMinutes(fromParts.first, toParts.first)
    val arriveZoned = if (zoned != null && durationMinutes != null) {
        zoned.plusMinutes(durationMinutes.toLong())
    } else {
        null
    }
    val arriveTime = arriveZoned?.let { formatTravelClock(it) } ?: "—"
    val heroDepartDate = zoned?.format(heroFmt) ?: "—"
    val heroArriveDate = arriveZoned?.format(heroFmt) ?: heroDepartDate

    val coach = seatOrTier
        ?.split("·")
        ?.map { it.trim() }
        ?.firstOrNull { it.startsWith("Coach", ignoreCase = true) }
        ?.removePrefix("Coach")?.removePrefix("coach")?.trim()
        ?: notes.notesValue("Coach")
        ?: "—"

    val seat = seatOrTier
        ?.split("·")
        ?.map { it.trim() }
        ?.firstOrNull { it.startsWith("Seat", ignoreCase = true) }
        ?.removePrefix("Seat")?.removePrefix("seat")?.trim()
        ?: notes.notesValue("Seat")
        ?: "—"

    val train = Regex("""(?i)\b([EI]\d{1,2})\b""").find(title)?.value
        ?: Regex("""(?i)\b([EI]\d{1,2})\b""").find(notes)?.value
        ?: title.removePrefix("SGR").trim().takeIf { it.isNotBlank() && it.length <= 6 }
        ?: "—"

    val ref = orderId?.takeIf { it.isNotBlank() }
        ?: barcodeValue.removePrefix("REF:").removePrefix("ref:").trim()
            .takeIf { it.isNotBlank() }
        ?: "—"

    val passenger = notes.notesValue("Passenger") ?: "Traveler"
    val travelClass = notes.firstNotesValue("Class", "Cabin", "Tier") ?: "Standard"

    return SgrTicketUiModel(
        passenger = passenger,
        departTime = departTime,
        arriveTime = arriveTime,
        heroDepartDate = heroDepartDate,
        heroArriveDate = heroArriveDate,
        fromCode = stationCode(fromParts.first),
        toCode = stationCode(toParts.first),
        fromCity = fromParts.first,
        toCity = toParts.first,
        bookingRef = ref,
        coach = coach,
        train = train,
        seat = seat,
        travelClass = travelClass
    )
}

private fun stationCode(city: String): String = when {
    city.contains("Nairobi", true) -> "NBO"
    city.contains("Mombasa", true) -> "MSA"
    city.contains("Voi", true) -> "VOI"
    city.contains("Mtito", true) -> "MTA"
    city.contains("Athi", true) -> "ATH"
    city.contains("Mariakani", true) -> "MRK"
    city.contains("Miasenyi", true) -> "MSY"
    city == "—" || city.isBlank() -> "—"
    else -> city.filter { it.isLetter() }.take(3).uppercase(Locale.US).ifBlank { "SGR" }
}

private fun splitCityStation(raw: String?): Pair<String, String> {
    if (raw.isNullOrBlank()) return "—" to "—"
    val t = raw.trim()
    val city = when {
        t.contains("Nairobi", true) -> "Nairobi"
        t.contains("Mombasa", true) -> "Mombasa"
        t.contains("Voi", true) -> "Voi"
        t.contains("Mtito", true) -> "Mtito Andei"
        t.contains("Athi", true) -> "Athi River"
        else -> t.substringBefore(" Terminus").substringBefore(" Station").trim()
            .ifBlank { t }.take(18)
    }
    return city to t
}

private fun estimateSgrDurationMinutes(from: String, to: String): Int? {
    fun key(a: String, b: String) = setOf(a.lowercase(Locale.US), b.lowercase(Locale.US))
    return when (key(from, to)) {
        key("Nairobi", "Mombasa") -> 330
        key("Nairobi", "Voi") -> 210
        key("Mombasa", "Voi") -> 120
        else -> null
    }
}
