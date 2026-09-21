package com.lipabill.app.ui.tickets

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lipabill.app.ui.theme.Canvas
import com.lipabill.app.ui.theme.CardWhite
import com.lipabill.app.ui.theme.Ink
import com.lipabill.app.ui.theme.LabelBlue
import com.lipabill.app.ui.theme.Mute
import com.lipabill.app.ui.theme.RouteBlue
import com.lipabill.app.ui.theme.SoftBlue
import java.util.Locale

@Composable
internal fun TravelDetailCell(label: String, value: String, modifier: Modifier = Modifier) {
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
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun TravelPassengerRow(name: String, subtitle: String?) {
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
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = Mute,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
internal fun TravelTicketPerforation() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(20.dp)
            .background(CardWhite)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = (-10).dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(Canvas)
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = 10.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(Canvas)
        )
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .align(Alignment.Center)
                .padding(horizontal = 14.dp)
        ) {
            drawLine(
                color = LabelBlue.copy(alpha = 0.55f),
                start = Offset(0f, 0f),
                end = Offset(size.width, 0f),
                strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
            )
        }
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
