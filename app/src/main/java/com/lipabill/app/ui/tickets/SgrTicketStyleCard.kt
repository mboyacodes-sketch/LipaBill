package com.lipabill.app.ui.tickets

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import com.lipabill.app.data.model.notesValue
import com.lipabill.app.ui.theme.CardWhite
import com.lipabill.app.ui.theme.Expense
import com.lipabill.app.ui.theme.GeometricSansFamily
import com.lipabill.app.ui.theme.Ink
import com.lipabill.app.ui.theme.Mute
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Near-black stage behind the SGR ticket. */
val SgrStage = Color(0xFF1A1A1A)

/** Mint CTA from the reference. */
val SgrMint = Color(0xFFA8E8B4)

private val SgrLabel = Color(0xFF757575)
private val SgrDash = Color(0xFFC5C5C5)
private val SgrGhostFill = Color(0xFFE8E6E1)
/** Soft periwinkle detail band — matches boarding-pass mock. */
private val SgrBand = Color(0xFFB5C4DF)

/**
 * SGR / Madaraka ticket — boarding-pass layout on a dark stage.
 * Same parsed fields; route → times → detail band → passenger → perforated barcode.
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
        SgrGhostTicketBehind(
            modifier = Modifier
                .fillMaxWidth(0.78f)
                .align(Alignment.TopCenter)
                .offset(y = (-8).dp)
                .height(14.dp)
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
        )

        SgrActiveTicketStack(
            model = model,
            pdf417 = pdf417,
            stageColor = stageColor,
            isUsed = isUsed,
            showViewBoardingQr = showViewBoardingQr,
            onViewBoardingQr = onViewBoardingQr,
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .padding(top = 6.dp)
        )
    }
}

@Composable
private fun SgrGhostTicketBehind(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .graphicsLayer { alpha = 0.35f }
            .shadow(3.dp, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp), clip = false)
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

    val corner = 28.dp
    val topShape = if (isUsed) {
        RoundedCornerShape(topStart = corner, topEnd = corner, bottomStart = 12.dp, bottomEnd = 12.dp)
    } else {
        RoundedCornerShape(topStart = corner, topEnd = corner, bottomStart = 0.dp, bottomEnd = 0.dp)
    }
    val stubShape = if (isUsed) {
        RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = corner, bottomEnd = corner)
    } else {
        RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomStart = corner, bottomEnd = corner)
    }

    val ink = if (isUsed) Mute else Ink

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = topLift)
                .shadow(if (isUsed) 8.dp else 14.dp, topShape, clip = false)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .clip(topShape)
                .background(CardWhite)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 18.dp)
            ) {
                if (isUsed) {
                    Text(
                        text = "Used",
                        color = Mute,
                        fontSize = 11.sp,
                        fontFamily = GeometricSansFamily,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // FROM ○ ── train ── ● TO  (single rail row)
                SgrFromToRail(muted = isUsed)

                // Duration + Direct badge centered under the rail
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (!model.durationLabel.isNullOrBlank()) {
                        Text(
                            text = model.durationLabel,
                            color = SgrLabel,
                            fontSize = 11.sp,
                            fontFamily = GeometricSansFamily,
                            fontWeight = FontWeight.Normal
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(ink)
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = model.serviceBadge,
                            color = CardWhite,
                            fontSize = 11.sp,
                            fontFamily = GeometricSansFamily,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Station codes + names
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = model.fromCode,
                            color = ink,
                            fontSize = 40.sp,
                            fontFamily = GeometricSansFamily,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = model.fromStation,
                            color = SgrLabel,
                            fontSize = 11.sp,
                            fontFamily = GeometricSansFamily,
                            fontWeight = FontWeight.Normal,
                            lineHeight = 14.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = model.toCode,
                            color = ink,
                            fontSize = 40.sp,
                            fontFamily = GeometricSansFamily,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp,
                            textAlign = TextAlign.End,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = model.toStation,
                            color = SgrLabel,
                            fontSize = 11.sp,
                            fontFamily = GeometricSansFamily,
                            fontWeight = FontWeight.Normal,
                            lineHeight = 14.sp,
                            textAlign = TextAlign.End,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Depart / arrive times + dates
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = model.departTime,
                            color = ink,
                            fontSize = 17.sp,
                            fontFamily = GeometricSansFamily,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = model.departDate,
                            color = SgrLabel,
                            fontSize = 11.sp,
                            fontFamily = GeometricSansFamily,
                            fontWeight = FontWeight.Normal
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = model.arriveTime,
                            color = ink,
                            fontSize = 17.sp,
                            fontFamily = GeometricSansFamily,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.End
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = model.arriveDate,
                            color = SgrLabel,
                            fontSize = 11.sp,
                            fontFamily = GeometricSansFamily,
                            fontWeight = FontWeight.Normal,
                            textAlign = TextAlign.End
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Soft blue logistics band — same 4-col placement as mock
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SgrBand.copy(alpha = if (isUsed) 0.55f else 1f))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    SgrBandCell("Depart time", model.departTime, Modifier.weight(1.15f), muted = isUsed)
                    SgrBandCell("Coach", model.coach, Modifier.weight(0.85f), muted = isUsed)
                    SgrBandCell("Train", model.train, Modifier.weight(0.95f), muted = isUsed)
                    SgrBandCell("Ref", model.bookingRef, Modifier.weight(1.05f), muted = isUsed)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Passenger | Seat | Class — same placement as mock
                Row(modifier = Modifier.fillMaxWidth()) {
                    SgrMetaCell("Passenger", model.passenger, Modifier.weight(1.5f), muted = isUsed)
                    SgrMetaCell(
                        "Seat",
                        model.seat,
                        Modifier.weight(0.85f),
                        muted = isUsed,
                        alignEnd = false
                    )
                    SgrMetaCell(
                        "Class",
                        model.travelClass,
                        Modifier.weight(1f),
                        muted = isUsed,
                        alignEnd = true
                    )
                }

                if (showViewBoardingQr && onViewBoardingQr != null) {
                    Spacer(modifier = Modifier.height(8.dp))
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
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                if (pdf417 != null) {
                    Image(
                        bitmap = pdf417,
                        contentDescription = "SGR booking barcode",
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
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
private fun SgrFromToRail(muted: Boolean) {
    val ink = if (muted) Mute else Ink
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.5.dp)
                .align(Alignment.Center)
                .padding(horizontal = 52.dp)
        ) {
            drawLine(
                color = SgrDash,
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
            )
        }

        // FROM + open circle (left)
        Row(
            modifier = Modifier.align(Alignment.CenterStart),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "FROM",
                color = SgrLabel,
                fontSize = 10.sp,
                fontFamily = GeometricSansFamily,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.8.sp
            )
            Canvas(modifier = Modifier.size(10.dp)) {
                drawCircle(
                    color = ink,
                    radius = size.minDimension / 2f - 1f,
                    style = Stroke(width = 1.8f)
                )
            }
        }

        // Train on the rail (center)
        Icon(
            imageVector = Icons.Outlined.Train,
            contentDescription = null,
            tint = ink,
            modifier = Modifier
                .align(Alignment.Center)
                .size(20.dp)
                .background(CardWhite, CircleShape)
                .padding(1.dp)
        )

        // Filled circle + TO (right)
        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(ink)
            )
            Text(
                text = "TO",
                color = SgrLabel,
                fontSize = 10.sp,
                fontFamily = GeometricSansFamily,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.8.sp
            )
        }
    }
}

@Composable
private fun SgrBandCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    muted: Boolean = false
) {
    Column(modifier = modifier.padding(end = 4.dp)) {
        Text(
            text = label,
            color = SgrLabel,
            fontSize = 10.sp,
            fontFamily = GeometricSansFamily,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            text = value,
            color = if (muted) Mute else Ink,
            fontSize = 13.sp,
            fontFamily = GeometricSansFamily,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SgrMetaCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    muted: Boolean = false,
    alignEnd: Boolean = false
) {
    Column(
        modifier = modifier.padding(end = if (alignEnd) 0.dp else 6.dp),
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start
    ) {
        Text(
            text = label,
            color = SgrLabel,
            fontSize = 11.sp,
            fontFamily = GeometricSansFamily,
            fontWeight = FontWeight.Normal
        )
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            text = value,
            color = if (muted) Mute else Ink,
            fontSize = 14.sp,
            fontFamily = GeometricSansFamily,
            fontWeight = FontWeight.Bold,
            textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
            maxLines = 2,
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
    val departDate: String,
    val arriveDate: String,
    val durationLabel: String?,
    val serviceBadge: String,
    val fromCode: String,
    val toCode: String,
    val fromCity: String,
    val fromStation: String,
    val toCity: String,
    val toStation: String,
    val bookingRef: String,
    val coach: String,
    val train: String,
    val seat: String,
    val travelClass: String,
    val headerWhen: String,
    val headerRoute: String
)

internal fun Ticket.toSgrTicketUiModel(): SgrTicketUiModel {
    val notes = notes.orEmpty()

    val origin = notes.notesValue("Origin") ?: venue?.substringBefore("→")?.trim()
    val destination = notes.notesValue("Destination") ?: venue?.substringAfter("→")?.trim()
    val fromParts = splitCityStation(origin)
    val toParts = splitCityStation(destination)

    val zoned = startsAtMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()) }
    val dateFmt = DateTimeFormatter.ofPattern("MMM d, EEE", Locale.ENGLISH)
    val timeFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

    val departTime = zoned?.format(timeFmt)
        ?: notes.notesValue("Departure time")
        ?: notes.notesValue("Time")
        ?: "—"

    val durationMinutes = estimateSgrDurationMinutes(fromParts.first, toParts.first)
    val arriveZoned = if (zoned != null && durationMinutes != null) {
        zoned.plusMinutes(durationMinutes.toLong())
    } else {
        null
    }
    val arriveTime = arriveZoned?.format(timeFmt) ?: "—"
    val departDate = zoned?.format(dateFmt) ?: "—"
    val arriveDate = arriveZoned?.format(dateFmt) ?: departDate

    val durationLabel = durationMinutes?.let { mins ->
        val h = mins / 60
        val m = mins % 60
        when {
            h == 0 -> "$m minutes"
            m == 0 -> if (h == 1) "1 hour" else "$h hours"
            else -> if (h == 1) "1 hour $m minutes" else "$h hours $m minutes"
        }
    }

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
    val travelClass = notes.notesValue("Class")
        ?: notes.notesValue("Cabin")
        ?: notes.notesValue("Tier")
        ?: "Standard"

    val serviceBadge = when {
        title.contains("Express", true) || notes.contains("Express", true) -> "Express"
        title.contains("Inter", true) -> "Inter-county"
        else -> "Direct"
    }

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
        departDate = departDate,
        arriveDate = arriveDate,
        durationLabel = durationLabel,
        serviceBadge = serviceBadge,
        fromCode = stationCode(fromParts.first),
        toCode = stationCode(toParts.first),
        fromCity = fromParts.first,
        fromStation = fromParts.second,
        toCity = toParts.first,
        toStation = toParts.second,
        bookingRef = ref,
        coach = coach,
        train = train,
        seat = seat,
        travelClass = travelClass,
        headerWhen = headerWhen,
        headerRoute = headerRoute
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
