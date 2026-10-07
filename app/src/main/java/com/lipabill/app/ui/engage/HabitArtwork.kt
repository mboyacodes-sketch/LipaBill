package com.lipabill.app.ui.engage

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.lipabill.app.ui.theme.Accent
import com.lipabill.app.ui.theme.ActionMetrics
import com.lipabill.app.ui.theme.ActionPay
import com.lipabill.app.ui.theme.ActionTickets
import com.lipabill.app.ui.theme.CardWhite
import com.lipabill.app.ui.theme.Hairline
import com.lipabill.app.ui.theme.Ink
import com.lipabill.app.ui.theme.LabelBlue
import com.lipabill.app.ui.theme.Mute
import com.lipabill.app.ui.theme.SoftBlue
import com.lipabill.app.viewmodel.ChallengeCardKind
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun motionEnabled(): Boolean {
    val context = LocalContext.current
    val scale = Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f
    )
    return scale > 0f
}

/**
 * Three-day pause mark: a takeaway bag inside a ring of days.
 * A finished pause draws a check and lets a short pastel burst fall away.
 * An ended pause stays quiet, with an empty ring.
 */
@Composable
fun HabitMark(
    kind: ChallengeCardKind,
    day: Int,
    totalDays: Int,
    modifier: Modifier = Modifier
) {
    val motion = motionEnabled()
    val alive = kind == ChallengeCardKind.OFFER || kind == ChallengeCardKind.ACTIVE
    val transition = rememberInfiniteTransition(label = "habit-mark")
    val dash by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (motion && alive) 28f else 0f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing)),
        label = "dash"
    )
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (motion && kind == ChallengeCardKind.ACTIVE) 1.16f else 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse"
    )
    val bob by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (motion && alive) 1f else 0f,
        animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Reverse),
        label = "bob"
    )
    val days = totalDays.coerceAtLeast(1)
    val done = when (kind) {
        ChallengeCardKind.COMPLETED -> days
        ChallengeCardKind.ACTIVE -> (day - 1).coerceIn(0, days)
        else -> 0
    }
    val pulseIndex = if (kind == ChallengeCardKind.ACTIVE) (day - 1).coerceIn(0, days - 1) else -1
    val arc by animateFloatAsState(
        targetValue = done / days.toFloat() * 360f,
        animationSpec = tween(if (motion) 700 else 0),
        label = "arc"
    )
    val burst = remember { Animatable(if (kind == ChallengeCardKind.COMPLETED && !motion) 1f else 0f) }
    LaunchedEffect(kind, motion) {
        if (kind == ChallengeCardKind.COMPLETED) {
            if (motion) {
                burst.snapTo(0f)
                burst.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
            } else {
                burst.snapTo(1f)
            }
        } else {
            burst.snapTo(0f)
        }
    }
    val ink = Ink
    val mute = Mute
    val accent = Accent
    val track = if (kind == ChallengeCardKind.BROKEN) Hairline else SoftBlue
    val card = CardWhite
    val confetti = listOf(ActionPay, ActionMetrics, ActionTickets, LabelBlue)
    Canvas(modifier = modifier.clearAndSetSemantics { }) {
        val s = size.minDimension
        val c = Offset(size.width / 2f, size.height / 2f)
        val ring = s * 0.38f
        val stroke = (s * 0.035f).coerceAtLeast(3.dp.toPx())
        drawCircle(color = track.copy(alpha = 0.55f), radius = s * 0.28f, center = c)
        drawArc(
            color = track,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(c.x - ring, c.y - ring),
            size = Size(ring * 2f, ring * 2f),
            style = Stroke(
                width = stroke,
                cap = StrokeCap.Round,
                pathEffect = if (alive) {
                    PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 9.dp.toPx()), dash.dp.toPx())
                } else {
                    null
                }
            )
        )
        if (arc > 0.5f) {
            drawArc(
                color = accent,
                startAngle = -90f,
                sweepAngle = arc,
                useCenter = false,
                topLeft = Offset(c.x - ring, c.y - ring),
                size = Size(ring * 2f, ring * 2f),
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        val nodeR = stroke * 0.95f
        for (i in 0 until days) {
            val angle = Math.toRadians((-90.0 + i * (360.0 / days)))
            val at = Offset(
                c.x + cos(angle).toFloat() * ring,
                c.y + sin(angle).toFloat() * ring
            )
            val filled = i < done
            val scale = if (i == pulseIndex) pulse else 1f
            drawCircle(color = card, radius = nodeR * scale + 1.5.dp.toPx(), center = at)
            drawCircle(
                color = if (filled) accent else if (kind == ChallengeCardKind.BROKEN) mute else accent,
                radius = nodeR * scale,
                center = at,
                style = if (filled) {
                    androidx.compose.ui.graphics.drawscope.Fill
                } else {
                    Stroke(width = 2.dp.toPx())
                }
            )
        }
        val lift = (bob - 0.5f) * 5.dp.toPx()
        drawBag(
            center = Offset(c.x, c.y + lift),
            scale = s,
            ink = if (kind == ChallengeCardKind.BROKEN) mute else ink,
            fill = track,
            check = if (kind == ChallengeCardKind.COMPLETED) burst.value else 0f,
            checkColor = accent
        )
        val t = burst.value
        if (t in 0.02f..0.98f) {
            repeat(14) { i ->
                val angle = Math.toRadians((i * (360.0 / 14.0)) + 8.0)
                val dist = (s * 0.16f + (i % 3) * s * 0.05f) * t
                val alpha = (1f - t).coerceIn(0f, 1f)
                drawCircle(
                    color = confetti[i % confetti.size].copy(alpha = alpha),
                    radius = (s * 0.018f) * (1.15f - t * 0.4f),
                    center = Offset(
                        c.x + cos(angle).toFloat() * dist,
                        c.y + sin(angle).toFloat() * dist + t * 8.dp.toPx()
                    )
                )
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBag(
    center: Offset,
    scale: Float,
    ink: Color,
    fill: Color,
    check: Float,
    checkColor: Color
) {
    val w = scale * 0.22f
    val h = scale * 0.24f
    val left = center.x - w / 2f
    val top = center.y - h * 0.42f
    drawRoundRect(
        color = fill,
        topLeft = Offset(left, top),
        size = Size(w, h),
        cornerRadius = CornerRadius(w * 0.16f, w * 0.16f)
    )
    drawRoundRect(
        color = ink,
        topLeft = Offset(left, top),
        size = Size(w, h),
        cornerRadius = CornerRadius(w * 0.16f, w * 0.16f),
        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
    )
    val handle = Size(w * 0.46f, h * 0.38f)
    drawArc(
        color = ink,
        startAngle = 200f,
        sweepAngle = 140f,
        useCenter = false,
        topLeft = Offset(center.x - handle.width / 2f, top - handle.height * 0.72f),
        size = handle,
        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
    )
    if (check <= 0f) return
    val path = Path().apply {
        moveTo(center.x - w * 0.22f, center.y + h * 0.02f)
        lineTo(center.x - w * 0.04f, center.y + h * 0.18f)
        lineTo(center.x + w * 0.24f, center.y - h * 0.12f)
    }
    val measure = PathMeasure().apply { setPath(path, false) }
    val segment = Path()
    measure.getSegment(0f, measure.length * check.coerceIn(0f, 1f), segment, true)
    drawPath(
        path = segment,
        color = checkColor,
        style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
    )
}

/** Seven day marks for the week, with today a little larger. */
@Composable
fun WeekGlyph(modifier: Modifier = Modifier) {
    val motion = motionEnabled()
    val today = remember { java.time.LocalDate.now().dayOfWeek.value }
    val transition = rememberInfiniteTransition(label = "week-glyph")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (motion) 1.2f else 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "today"
    )
    val accent = Accent
    val track = Hairline
    val card = CardWhite
    Canvas(modifier = modifier.clearAndSetSemantics { }) {
        val slot = size.width / 7f
        val r = slot * 0.28f
        val cy = size.height / 2f
        for (i in 0 until 7) {
            val cx = slot * i + slot / 2f
            val isToday = i + 1 == today
            val past = i + 1 < today
            val scale = if (isToday) pulse else 1f
            drawCircle(color = card, radius = r * scale + 1.dp.toPx(), center = Offset(cx, cy))
            drawCircle(
                color = if (past || isToday) accent else track,
                radius = r * scale,
                center = Offset(cx, cy),
                style = if (past || isToday) {
                    androidx.compose.ui.graphics.drawscope.Fill
                } else {
                    Stroke(width = 1.6.dp.toPx())
                }
            )
        }
    }
}
