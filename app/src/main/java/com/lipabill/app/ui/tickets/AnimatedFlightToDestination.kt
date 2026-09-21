package com.lipabill.app.ui.tickets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.lipabill.app.ui.theme.RouteBlue

/**
 * Straight dashed route line with a static plane in the middle,
 * nose pointed toward destination (90°).
 */
@Composable
fun AnimatedFlightToDestination(
    modifier: Modifier = Modifier,
    tint: Color = RouteBlue.copy(alpha = 0.65f)
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val y = size.height / 2f
            val gap = 18.dp.toPx() // leave a hole under the plane
            val mid = size.width / 2f
            val stroke = 2.dp.toPx()
            val dash = PathEffect.dashPathEffect(
                floatArrayOf(7.dp.toPx(), 5.dp.toPx()),
                phase = 0f
            )
            // Left segment: source → plane
            if (mid - gap > 0f) {
                drawLine(
                    color = tint.copy(alpha = 0.55f),
                    start = Offset(0f, y),
                    end = Offset(mid - gap, y),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                    pathEffect = dash
                )
            }
            // Right segment: plane → destination
            if (mid + gap < size.width) {
                drawLine(
                    color = tint.copy(alpha = 0.55f),
                    start = Offset(mid + gap, y),
                    end = Offset(size.width, y),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                    pathEffect = dash
                )
            }
        }

        Icon(
            imageVector = Icons.Filled.Flight,
            contentDescription = "Flight direction",
            tint = tint,
            modifier = Modifier
                .rotate(90f)
                .size(26.dp)
        )
    }
}
