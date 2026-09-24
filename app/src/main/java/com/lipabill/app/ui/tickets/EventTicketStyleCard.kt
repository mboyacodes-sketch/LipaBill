package com.lipabill.app.ui.tickets

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lipabill.app.data.model.Ticket
import com.lipabill.app.ui.theme.Accent
import com.lipabill.app.ui.theme.CardWhite
import com.lipabill.app.ui.theme.Expense
import com.lipabill.app.ui.theme.Hairline
import com.lipabill.app.ui.theme.Ink
import com.lipabill.app.ui.theme.Mute
import com.lipabill.app.ui.util.formatEventWhen
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val EventDetailBorder = Color(0xFFE8E8E8)
private val EventLabelGrey = Color(0xFF9CA3AF)

/** Solid stage behind the event ticket — brand blue (distinct from SGR’s near-black). */
val EventStage = Accent

/**
 * Event ticket — white perforated card on a solid brand-blue stage.
 * Layout matches the SGR solid-stage pattern; color keeps event tickets distinct.
 * When [isUsed], the stub tears off at the perforation and drops slightly below.
 */
@Composable
fun EventTicketStyleCard(
    ticket: Ticket,
    qrBitmap: ImageBitmap?,
    onChangeDate: (() -> Unit)?,
    modifier: Modifier = Modifier,
    stageColor: Color = EventStage,
    isUsed: Boolean = false
) {
    val model = remember(ticket) { ticket.toEventTicketUiModel() }

    val tear = updateTransition(targetState = isUsed, label = "eventTicketTear")
    val gap by tear.animateDp(
        transitionSpec = { tween(720, easing = FastOutSlowInEasing) },
        label = "gap"
    ) { used -> if (used) 14.dp else 0.dp }
    val stubDrop by tear.animateDp(
        transitionSpec = { tween(720, easing = FastOutSlowInEasing) },
        label = "stubDrop"
    ) { used -> if (used) 28.dp else 0.dp }
    val stubTilt by tear.animateFloat(
        transitionSpec = { tween(720, easing = FastOutSlowInEasing) },
        label = "stubTilt"
    ) { used -> if (used) 3.5f else 0f }
    val topLift by tear.animateDp(
        transitionSpec = { tween(720, easing = FastOutSlowInEasing) },
        label = "topLift"
    ) { used -> if (used) (-6).dp else 0.dp }
    val stubShadow by tear.animateDp(
        transitionSpec = { tween(720, easing = FastOutSlowInEasing) },
        label = "stubShadow"
    ) { used -> if (used) 14.dp else 4.dp }

    val topShape = if (isUsed) {
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 12.dp, bottomEnd = 12.dp)
    } else {
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 0.dp, bottomEnd = 0.dp)
    }
    val stubShape = if (isUsed) {
        RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
    } else {
        RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(stageColor)
            .padding(horizontal = 16.dp, vertical = 28.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // ── Top piece (details) ──────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = topLift)
                    .shadow(if (isUsed) 10.dp else 12.dp, topShape, clip = false)
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .clip(topShape)
                    .background(CardWhite)
            ) {
                EventCongratsHeader(isUsed = isUsed)
                Spacer(modifier = Modifier.height(12.dp))
                EventDetailsBox(model = model)
                if (onChangeDate != null && !isUsed) {
                    TextButton(
                        onClick = onChangeDate,
                        modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                    ) {
                        Icon(Icons.Outlined.Event, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (ticket.startsAtMillis != null) "Change date" else "Set date",
                            fontSize = 13.sp
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.height(16.dp))
                }
                EventTicketPerforation(showScissors = !isUsed)
            }

            Spacer(modifier = Modifier.height(gap))

            // ── QR stub (falls away when used) ───────────────────────
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
                    EventTicketPerforation(showScissors = false)
                }
                EventQrStub(qrBitmap = qrBitmap, model = model, isUsed = isUsed)
            }
        }
    }
}

@Composable
private fun EventCongratsHeader(isUsed: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .border(2.dp, if (isUsed) Mute else Accent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = if (isUsed) Mute else Accent,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (isUsed) "Used" else "Congrats",
                color = if (isUsed) Mute else Accent,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (isUsed) {
                    "This ticket has been marked as used"
                } else {
                    "Your ticket has been successfully booked"
                },
                color = Mute,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EventDetailsBox(model: EventTicketUiModel) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .border(BorderStroke(1.dp, EventDetailBorder), RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                EventField(
                    label = "Event",
                    value = model.eventTitle,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(12.dp))
                EventField(
                    label = "Seat No.",
                    value = model.seat ?: "—",
                    modifier = Modifier.width(72.dp),
                    alignEnd = true
                )
            }

            if (!model.venue.isNullOrBlank() || !model.tier.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    if (!model.venue.isNullOrBlank()) {
                        EventField(
                            label = "Venue",
                            value = model.venue,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (!model.tier.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(12.dp))
                        EventField(
                            label = "Tier",
                            value = model.tier,
                            modifier = Modifier.width(88.dp),
                            alignEnd = true
                        )
                    }
                }
            }

            if (!model.door.isNullOrBlank() || !model.attendee.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    if (!model.attendee.isNullOrBlank()) {
                        EventField(
                            label = "Attendee",
                            value = model.attendee,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (!model.door.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(12.dp))
                        EventField(
                            label = "Door",
                            value = model.door,
                            modifier = Modifier.width(72.dp),
                            alignEnd = true
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            EventField(
                label = "Date & Time",
                value = listOfNotNull(model.dateLabel, model.timeLabel)
                    .joinToString("   ")
                    .ifBlank { "—" }
            )
        }

        Text(
            text = "EVENT DETAILS",
            color = Color(0xFF6B7280),
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

@Composable
private fun EventQrStub(
    qrBitmap: ImageBitmap?,
    model: EventTicketUiModel,
    isUsed: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardWhite)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (qrBitmap != null) {
            Image(
                bitmap = qrBitmap,
                contentDescription = "Event ticket QR",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(110.dp)
                    .graphicsLayer { alpha = if (isUsed) 0.45f else 1f }
                    .background(CardWhite)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .border(1.dp, Hairline, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("No QR", color = Mute, fontSize = 12.sp)
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (isUsed) "Stub detached" else "Scan at entry",
                color = if (isUsed) Expense else Ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            val ref = model.refNo ?: model.orderHint
            Text(
                text = if (!ref.isNullOrBlank()) "Ref $ref" else "Show this code at the door",
                color = Mute,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EventField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    alignEnd: Boolean = false
) {
    Column(
        modifier = modifier,
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start
    ) {
        Text(
            text = label,
            color = EventLabelGrey,
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
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EventTicketPerforation(showScissors: Boolean = true) {
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
            val r = 10.dp.toPx()
            val cy = size.height / 2f
            drawCircle(
                color = Color.Black,
                radius = r,
                center = Offset(0f, cy),
                blendMode = BlendMode.Clear
            )
            drawCircle(
                color = Color.Black,
                radius = r,
                center = Offset(size.width, cy),
                blendMode = BlendMode.Clear
            )
            drawLine(
                color = EventLabelGrey.copy(alpha = 0.55f),
                start = Offset(r + 4.dp.toPx(), cy),
                end = Offset(size.width - r - 4.dp.toPx(), cy),
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
                    contentDescription = "Cut along perforation when used",
                    tint = EventLabelGrey,
                    modifier = Modifier
                        .size(16.dp)
                        .rotate(-45f)
                )
            }
        }
    }
}

private data class EventTicketUiModel(
    val eventTitle: String,
    val venue: String?,
    val dateLabel: String?,
    val timeLabel: String?,
    val seat: String?,
    val tier: String?,
    val door: String?,
    val refNo: String?,
    val orderHint: String?,
    val attendee: String?
)

private fun Ticket.toEventTicketUiModel(): EventTicketUiModel {
    val notes = notes
    val zoned = startsAtMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()) }
    val timeLabel = zoned?.format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))
        ?: notes.notesValue("Time")
        ?: notes.notesValue("Doors")
    val dateLabel = zoned?.format(DateTimeFormatter.ofPattern("d MMMM, yyyy", Locale.ENGLISH))
        ?: startsAtMillis?.let { formatEventWhen(it) }

    val parts = seatOrTier.orEmpty()
        .split("·", "|", ",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    val seat = parts.firstOrNull { it.startsWith("Seat", true) }
        ?.replace(Regex("""(?i)^Seat\s*"""), "")?.trim()
        ?: notes.notesValue("Seat")
        ?: parts.firstOrNull { Regex("""(?i)^\d+[A-Z]?$""").matches(it) }

    val tier = parts.firstOrNull {
        it.contains("VIP", true) || it.contains("Tier", true) ||
            it.contains("General", true) || it.contains("GA", true) ||
            it.contains("Premium", true) || it.contains("Class", true) ||
            it.contains("Standing", true)
    }?.replace(Regex("""(?i)^(Tier|Class)\s*"""), "")?.trim()
        ?: notes.notesValue("Tier")
        ?: notes.notesValue("Class")

    val door = parts.firstOrNull {
        it.startsWith("Door", true) || it.startsWith("Gate", true) ||
            it.startsWith("Entrance", true) || it.startsWith("Section", true) ||
            it.startsWith("Row", true)
    }?.replace(Regex("""(?i)^(Door|Gate|Entrance|Section|Row)\s*"""), "")?.trim()
        ?: notes.notesValue("Door")
        ?: notes.notesValue("Gate")
        ?: notes.notesValue("Section")
        ?: notes.notesValue("Row")

    val venueLabel = this.venue?.takeIf { it.isNotBlank() && !it.contains("→") }
        ?: notes.notesValue("Venue")
        ?: notes.notesValue("Location")
        ?: notes.notesValue("Place")

    val attendee = notes.notesValue("Passenger")
        ?: notes.notesValue("Name")
        ?: notes.notesValue("Attendee")
        ?: notes.notesValue("Guest")

    val eventTitle = title.trim()
        .takeIf { it.isNotBlank() && !it.equals("E-ticket", true) && !it.equals("Scanned ticket", true) }
        ?: "Event"

    return EventTicketUiModel(
        eventTitle = eventTitle.take(48),
        venue = venueLabel?.take(40),
        dateLabel = dateLabel?.take(22),
        timeLabel = timeLabel?.take(12),
        seat = seat?.take(8),
        tier = tier?.take(16),
        door = door?.take(12),
        refNo = orderId?.take(14),
        orderHint = barcodeValue.takeIf {
            it.length in 6..20 && it.all { ch -> ch.isLetterOrDigit() || ch == '-' }
        }?.take(16),
        attendee = attendee?.take(36)
    )
}

private fun String?.notesValue(label: String): String? {
    val prefix = "$label:"
    return orEmpty()
        .split(" · ")
        .map { it.trim() }
        .firstOrNull { it.startsWith(prefix, ignoreCase = true) }
        ?.substringAfter(':')
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
}
