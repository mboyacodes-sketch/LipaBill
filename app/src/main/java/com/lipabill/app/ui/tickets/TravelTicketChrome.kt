package com.lipabill.app.ui.tickets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lipabill.app.ui.privacy.recordingPrivacyCover
import com.lipabill.app.ui.theme.Canvas
import com.lipabill.app.ui.theme.CardWhite
import com.lipabill.app.ui.theme.Ink
import com.lipabill.app.ui.theme.LabelBlue
import com.lipabill.app.ui.theme.Mute
import com.lipabill.app.ui.theme.RouteBlue
import com.lipabill.app.ui.theme.SoftBlue
import java.util.Locale

@Composable
fun TravelDetailCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    cover: Boolean = false
) {
    val hide = cover && value.isNotBlank() && value != "—"
    Column(modifier = modifier.padding(end = 6.dp)) {
        Text(
            text = label,
            color = LabelBlue,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            color = Ink,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.recordingPrivacyCover(hide)
        )
    }
}

@Composable
fun TravelPassengerRow(name: String, subtitle: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(SoftBlue),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Person,
                contentDescription = null,
                tint = RouteBlue.copy(alpha = 0.7f),
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = name,
                color = Ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.recordingPrivacyCover(name != "Passenger")
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = Mute,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.recordingPrivacyCover()
                )
            }
        }
    }
}

@Composable
fun TravelTicketPerforation() {
    NotchTicketPerforation(
        notchColor = Canvas,
        dashColor = LabelBlue.copy(alpha = 0.55f)
    )
}

/** Airport / station codes with the animated plane (or train) corridor between them. */
@Composable
fun FlightRouteRow(
    from: String?,
    to: String?,
    label: String? = null,
    compact: Boolean = false,
    codeSizeSp: Int? = null
) {
    val codeSize = (codeSizeSp ?: if (compact) 16 else 32).sp
    val labelSize = if (compact) 9.sp else 11.sp
    val planeAlpha = if (compact) 0.4f else 0.65f
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        if (label != null) {
            Text(
                text = label,
                color = LabelBlue,
                fontSize = labelSize,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = from ?: "—",
                color = RouteBlue,
                fontSize = codeSize,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            AnimatedFlightToDestination(
                modifier = Modifier.weight(1f),
                tint = RouteBlue.copy(alpha = planeAlpha)
            )
            Text(
                text = to ?: "—",
                color = RouteBlue,
                fontSize = codeSize,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Side notches painted in [notchColor], with a dashed cut line between them. */
@Composable
fun NotchTicketPerforation(
    notchColor: Color,
    dashColor: Color,
    bandHeight: Dp = 20.dp,
    dashInset: Dp = 14.dp
) {
    val radius = bandHeight / 2
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(bandHeight)
            .background(CardWhite)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = -radius)
                .size(bandHeight)
                .clip(CircleShape)
                .background(notchColor)
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = radius)
                .size(bandHeight)
                .clip(CircleShape)
                .background(notchColor)
        )
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .align(Alignment.Center)
                .padding(horizontal = dashInset)
        ) {
            drawLine(
                color = dashColor,
                start = Offset(0f, 0f),
                end = Offset(size.width, 0f),
                strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
            )
        }
    }
}

/**
 * Punched holes and an optional scissors mark. The parent must draw with
 * offscreen compositing so the holes clear through to the stage behind the card.
 */
@Composable
fun PunchedTicketPerforation(
    ink: Color,
    showScissors: Boolean = true,
    scissorsDescription: String? = null
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (showScissors) 28.dp else 20.dp)
            .background(CardWhite),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .matchParentSize()
        ) {
            val radius = 10.dp.toPx()
            val centerY = size.height / 2f
            drawCircle(
                color = Color.Black,
                radius = radius,
                center = Offset(0f, centerY),
                blendMode = BlendMode.Clear
            )
            drawCircle(
                color = Color.Black,
                radius = radius,
                center = Offset(size.width, centerY),
                blendMode = BlendMode.Clear
            )
            drawLine(
                color = ink.copy(alpha = 0.55f),
                start = Offset(radius + 4.dp.toPx(), centerY),
                end = Offset(size.width - radius - 4.dp.toPx(), centerY),
                strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
            )
        }
        if (showScissors) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(CardWhite, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentCut,
                    contentDescription = scissorsDescription,
                    tint = ink,
                    modifier = Modifier
                        .size(16.dp)
                        .rotate(-45f)
                )
            }
        }
    }
}

private val TicketFrameCaption = Color(0xFF6B7280)

/** Bordered details panel with a caption sitting on the top edge. */
@Composable
fun TicketDetailsFrame(
    title: String,
    borderColor: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 18.dp),
            content = content
        )
        Text(
            text = title,
            color = TicketFrameCaption,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .background(CardWhite)
                .padding(horizontal = 10.dp)
        )
    }
}

/** Label over a value, used on event tickets and payment receipts. */
@Composable
fun TicketMetaField(
    label: String,
    value: String,
    labelColor: Color,
    modifier: Modifier = Modifier,
    alignEnd: Boolean = false,
    cover: Boolean = false,
    onClick: (() -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = modifier.then(
            if (onClick != null) {
                Modifier.clickable(role = Role.Button, onClick = onClick)
            } else {
                Modifier
            }
        ),
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start
    ) {
        Text(
            text = label,
            color = labelColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            color = Ink,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.recordingPrivacyCover(cover)
        )
        footer?.invoke()
    }
}

/** Map bare fare letters (K, Y, …) to a readable cabin; keep named cabins as-is. */
internal fun friendlyCabinLabel(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val t = raw.trim()
    when {
        t.contains("Business", true) -> return "Business"
        t.contains("First", true) -> return "First"
        t.contains("Premium", true) -> return "Premium Economy"
        t.contains("Economy", true) -> return "Economy"
    }
    if (t.length == 1 && t[0].isLetter()) {
        val economyCodes = setOf("Y", "B", "M", "H", "K", "Q", "V", "W", "S", "T", "L", "U", "E", "N", "O")
        val businessCodes = setOf("J", "C", "D", "I", "Z")
        return when (t.uppercase(Locale.US)) {
            in businessCodes -> "Business"
            in economyCodes -> "Economy"
            else -> "Class $t"
        }
    }
    return t.replace(Regex("""(?i)^(Class|Cabin)\s*"""), "").trim().ifBlank { null }
}
