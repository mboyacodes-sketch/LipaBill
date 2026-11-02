package com.lipabill.app.ui.tickets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCut
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
import com.lipabill.app.ui.theme.CardWhite
import com.lipabill.app.ui.theme.Ink

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
