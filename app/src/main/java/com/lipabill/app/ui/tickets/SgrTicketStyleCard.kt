package com.lipabill.app.ui.tickets

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Train
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lipabill.app.data.model.Ticket
import com.lipabill.app.ui.theme.CardWhite
import com.lipabill.app.ui.theme.Expense
import com.lipabill.app.ui.theme.GeometricSansFamily
import com.lipabill.app.ui.theme.Ink
import com.lipabill.app.ui.theme.Mute
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Near-black stage behind the SGR ticket (matches reference mock). */
val SgrStage = Color(0xFF0B0F0D)

/** Mint CTA from the reference. */
val SgrMint = Color(0xFFA8E8B4)

private val SgrLabel = Color(0xFF9CA3AF)
private val SgrDash = Color(0xFFD1D5DB)
private val SgrGhostFill = Color(0xFFE8E6E1)

/**
 * SGR / Madaraka booking ticket — white perforated card on a dark stage.
 * Stacked behind a smaller greyed “next” ticket peeking at the top.
 * Main card is ~70% width. When [isUsed], barcode stub tears off.
 */
@Composable
fun SgrTicketStyleCard(
    ticket: Ticket,
    pdf417: ImageBitmap?,
    modifier: Modifier = Modifier,
    stageColor: Color = SgrStage,
    isUsed: Boolean = false,
    showViewBoardingQr: Boolean = false,
    onViewBoardingQr: (() -> Unit)? = null
) {
    val model = remember(ticket) { ticket.toSgrTicketUiModel() }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter
    ) {
        // Floating ticket behind — blank edge only, ~5% visible above the active card
        SgrGhostTicketBehind(
            modifier = Modifier
                .fillMaxWidth(0.68f)
                .align(Alignment.TopCenter)
                .offset(y = (-8).dp)
                .height(14.dp)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
        )

        // Active ticket — 75% width
        SgrActiveTicketStack(
            model = model,
            pdf417 = pdf417,
            stageColor = stageColor,
            isUsed = isUsed,
            showViewBoardingQr = showViewBoardingQr,
            onViewBoardingQr = onViewBoardingQr,
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .padding(top = 6.dp)
        )
    }
}

/** Blank grey strip peeking ~5% above the active card — no copy. */
@Composable
private fun SgrGhostTicketBehind(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .graphicsLayer { alpha = 0.35f }
            .shadow(3.dp, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp), clip = false)
            .background(SgrGhostFill)
    )
}

@Composable
private fun SgrActiveTicketStack(
    model: SgrTicketUiModel,
    pdf417: ImageBitmap?,
    stageColor: Color,
    isUsed: Boolean,
    showViewBoardingQr: Boolean,
    onViewBoardingQr: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val tear = updateTransition(targetState = isUsed, label = "sgrTicketTear")
    val gap by tear.animateDp(
        transitionSpec = { tween(720, easing = FastOutSlowInEasing) },
        label = "gap"
    ) { used -> if (used) 10.dp else 0.dp }
    val stubDrop by tear.animateDp(
        transitionSpec = { tween(720, easing = FastOutSlowInEasing) },
        label = "stubDrop"
    ) { used -> if (used) 22.dp else 0.dp }
    val stubTilt by tear.animateFloat(
        transitionSpec = { tween(720, easing = FastOutSlowInEasing) },
        label = "stubTilt"
    ) { used -> if (used) 3.5f else 0f }
    val topLift by tear.animateDp(
        transitionSpec = { tween(720, easing = FastOutSlowInEasing) },
        label = "topLift"
    ) { used -> if (used) (-4).dp else 0.dp }
    val stubShadow by tear.animateDp(
        transitionSpec = { tween(720, easing = FastOutSlowInEasing) },
        label = "stubShadow"
    ) { used -> if (used) 12.dp else 4.dp }

    val corner = 20.dp
    val topShape = if (isUsed) {
        RoundedCornerShape(topStart = corner, topEnd = corner, bottomStart = 10.dp, bottomEnd = 10.dp)
    } else {
        RoundedCornerShape(topStart = corner, topEnd = corner, bottomStart = 0.dp, bottomEnd = 0.dp)
    }
    val stubShape = if (isUsed) {
        RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = corner, bottomEnd = corner)
    } else {
        RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomStart = corner, bottomEnd = corner)
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = topLift)
                .shadow(if (isUsed) 8.dp else 10.dp, topShape, clip = false)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .clip(topShape)
                .background(CardWhite)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                if (isUsed) {
                    Text(
                        text = "Used",
                        color = Mute,
                        fontSize = 11.sp,
                        fontFamily = GeometricSansFamily,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "This ticket has been marked as used",
                        color = Mute,
                        fontSize = 11.sp,
                        fontFamily = GeometricSansFamily
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                Text(
                    text = "Passenger",
                    color = SgrLabel,
                    fontSize = 11.sp,
                    fontFamily = GeometricSansFamily,
                    fontWeight = FontWeight.Normal
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = model.passenger,
                    color = if (isUsed) Mute else Ink,
                    fontSize = 16.sp,
                    fontFamily = GeometricSansFamily,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(16.dp))
                SgrTimelineRow(
                    departTime = model.departTime,
                    arriveTime = model.arriveTime,
                    duration = model.durationLabel,
                    muted = isUsed
                )

                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = model.fromCity,
                            color = if (isUsed) Mute else Ink,
                            fontSize = 14.sp,
                            fontFamily = GeometricSansFamily,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = model.fromStation,
                            color = SgrLabel,
                            fontSize = 10.sp,
                            fontFamily = GeometricSansFamily,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = model.toCity,
                            color = if (isUsed) Mute else Ink,
                            fontSize = 14.sp,
                            fontFamily = GeometricSansFamily,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.End,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = model.toStation,
                            color = SgrLabel,
                            fontSize = 10.sp,
                            fontFamily = GeometricSansFamily,
                            textAlign = TextAlign.End,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Booking Reference",
                    color = SgrLabel,
                    fontSize = 11.sp,
                    fontFamily = GeometricSansFamily
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = model.bookingRef,
                    color = if (isUsed) Mute else Ink,
                    fontSize = 17.sp,
                    fontFamily = GeometricSansFamily,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.4.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    SgrMetaCell("Train Car", model.coach, Modifier.weight(1f), muted = isUsed)
                    SgrMetaCell("Train", model.train, Modifier.weight(1f), muted = isUsed)
                    SgrMetaCell("Seat", model.seat, Modifier.weight(1f), muted = isUsed)
                }

                if (showViewBoardingQr && onViewBoardingQr != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    TextButton(
                        onClick = onViewBoardingQr,
                        enabled = !isUsed,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "View boarding QR",
                            color = if (isUsed) Mute else Ink,
                            fontFamily = GeometricSansFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            SgrTicketPerforation(notchColor = stageColor)
        }

        Spacer(modifier = Modifier.height(gap))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = stubDrop)
                .shadow(stubShadow, stubShape, clip = false)
                .graphicsLayer {
                    rotationZ = stubTilt
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .clip(stubShape)
                .background(CardWhite)
        ) {
            if (isUsed) {
                SgrTicketPerforation(notchColor = stageColor)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardWhite)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                if (pdf417 != null) {
                    Image(
                        bitmap = pdf417,
                        contentDescription = "SGR booking barcode",
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .graphicsLayer { alpha = if (isUsed) 0.45f else 1f }
                    )
                } else {
                    Text(
                        text = model.bookingRef,
                        color = if (isUsed) Mute else Ink,
                        fontSize = 14.sp,
                        fontFamily = GeometricSansFamily,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
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
                        .padding(bottom = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun SgrTimelineRow(
    departTime: String,
    arriveTime: String,
    duration: String?,
    muted: Boolean = false
) {
    val ink = if (muted) Mute else Ink
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = departTime,
                color = ink,
                fontSize = 13.sp,
                fontFamily = GeometricSansFamily,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            if (!duration.isNullOrBlank()) {
                Text(
                    text = duration,
                    color = ink,
                    fontSize = 11.sp,
                    fontFamily = GeometricSansFamily,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = arriveTime,
                color = ink,
                fontSize = 13.sp,
                fontFamily = GeometricSansFamily,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .align(Alignment.Center)
            ) {
                val mid = size.width * 0.48f
                drawLine(
                    color = ink,
                    start = Offset(10f, size.height / 2f),
                    end = Offset(mid, size.height / 2f),
                    strokeWidth = 3f
                )
                drawLine(
                    color = SgrDash,
                    start = Offset(mid, size.height / 2f),
                    end = Offset(size.width - 10f, size.height / 2f),
                    strokeWidth = 3f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(ink)
            )
            Canvas(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(12.dp)
            ) {
                drawCircle(
                    color = ink,
                    radius = size.minDimension / 2f - 1.5f,
                    style = Stroke(width = 2.5f)
                )
            }
            Icon(
                imageVector = Icons.Outlined.Train,
                contentDescription = null,
                tint = ink,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = (-8).dp)
                    .size(22.dp)
                    .background(CardWhite, CircleShape)
                    .padding(2.dp)
            )
        }
    }
}

@Composable
private fun SgrMetaCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    muted: Boolean = false
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            color = SgrLabel,
            fontSize = 12.sp,
            fontFamily = GeometricSansFamily
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            color = if (muted) Mute else Ink,
            fontSize = 14.sp,
            fontFamily = GeometricSansFamily,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SgrTicketPerforation(notchColor: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(22.dp)
            .background(CardWhite)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = (-11).dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(notchColor)
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = 11.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(notchColor)
        )
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .align(Alignment.Center)
                .padding(horizontal = 16.dp)
        ) {
            drawLine(
                color = SgrDash,
                start = Offset(0f, 0f),
                end = Offset(size.width, 0f),
                strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
            )
        }
    }
}

data class SgrTicketUiModel(
    val passenger: String,
    val departTime: String,
    val arriveTime: String,
    val durationLabel: String?,
    val fromCity: String,
    val fromStation: String,
    val toCity: String,
    val toStation: String,
    val bookingRef: String,
    val coach: String,
    val train: String,
    val seat: String,
    val headerWhen: String,
    val headerRoute: String
)

internal fun Ticket.toSgrTicketUiModel(): SgrTicketUiModel {
    val notes = notes.orEmpty()
    fun note(label: String): String? {
        val prefix = "$label:"
        return notes.split(" · ")
            .map { it.trim() }
            .firstOrNull { it.startsWith(prefix, ignoreCase = true) }
            ?.substringAfter(':')
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    val origin = note("Origin") ?: venue?.substringBefore("→")?.trim()
    val destination = note("Destination") ?: venue?.substringAfter("→")?.trim()
    val fromParts = splitCityStation(origin)
    val toParts = splitCityStation(destination)

    val zoned = startsAtMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()) }
    val departTime = zoned?.format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))
        ?: note("Departure time")
        ?: note("Time")
        ?: "—"

    val durationMinutes = estimateSgrDurationMinutes(fromParts.first, toParts.first)
    val arriveTime = when {
        zoned != null && durationMinutes != null ->
            zoned.plusMinutes(durationMinutes.toLong())
                .format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))
        else -> "—"
    }
    val durationLabel = durationMinutes?.let { mins ->
        val h = mins / 60
        val m = mins % 60
        if (m == 0) "${h}h" else "${h}h ${m}m"
    }

    val coach = seatOrTier
        ?.split("·")
        ?.map { it.trim() }
        ?.firstOrNull { it.startsWith("Coach", ignoreCase = true) }
        ?.removePrefix("Coach")?.removePrefix("coach")?.trim()
        ?: note("Coach")
        ?: "—"

    val seat = seatOrTier
        ?.split("·")
        ?.map { it.trim() }
        ?.firstOrNull { it.startsWith("Seat", ignoreCase = true) }
        ?.removePrefix("Seat")?.removePrefix("seat")?.trim()
        ?: note("Seat")
        ?: "—"

    val train = Regex("""(?i)\b([EI]\d{1,2})\b""").find(title)?.value
        ?: Regex("""(?i)\b([EI]\d{1,2})\b""").find(notes)?.value
        ?: title.removePrefix("SGR").trim().takeIf { it.isNotBlank() && it.length <= 6 }
        ?: "—"

    val ref = orderId?.takeIf { it.isNotBlank() }
        ?: barcodeValue.removePrefix("REF:").removePrefix("ref:").trim()
            .takeIf { it.isNotBlank() }
        ?: "—"

    val passenger = note("Passenger") ?: "Traveler"

    val headerWhen = zoned?.format(DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.ENGLISH))
        ?: listOfNotNull(
            zoned?.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)),
            departTime.takeIf { it != "—" }
        ).joinToString(", ").ifBlank { "Upcoming trip" }

    val headerRoute = listOf(fromParts.first, toParts.first)
        .filter { it.isNotBlank() && it != "—" }
        .joinToString("–")
        .ifBlank { "SGR" }

    return SgrTicketUiModel(
        passenger = passenger,
        departTime = departTime,
        arriveTime = arriveTime,
        durationLabel = durationLabel,
        fromCity = fromParts.first,
        fromStation = fromParts.second,
        toCity = toParts.first,
        toStation = toParts.second,
        bookingRef = ref,
        coach = coach,
        train = train,
        seat = seat,
        headerWhen = headerWhen,
        headerRoute = headerRoute
    )
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
