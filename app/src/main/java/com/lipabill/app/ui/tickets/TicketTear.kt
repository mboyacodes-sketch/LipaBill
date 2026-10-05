package com.lipabill.app.ui.tickets

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Shared fade for a pass that has been marked used. */
internal const val UsedPassAlpha = 0.45f

internal fun Modifier.fadeWhenUsed(used: Boolean): Modifier =
    if (used) alpha(UsedPassAlpha) else this

internal data class TicketTear(
    val gap: Dp,
    val stubDrop: Dp,
    val stubTilt: Float,
    val topLift: Dp,
    val stubShadow: Dp
)

/**
 * Tear motion shared by event and SGR cards. Distances stay per card so each
 * layout still falls the way it was drawn.
 */
@Composable
internal fun rememberTicketTear(
    used: Boolean,
    label: String,
    gapWhenUsed: Dp,
    dropWhenUsed: Dp,
    liftWhenUsed: Dp,
    shadowWhenUsed: Dp,
    shadowAtRest: Dp = 4.dp
): TicketTear {
    val tear = updateTransition(targetState = used, label = label)
    val move = tween<Dp>(720, easing = FastOutSlowInEasing)
    val tilt = tween<Float>(720, easing = FastOutSlowInEasing)
    val gap by tear.animateDp(transitionSpec = { move }, label = "gap") {
        if (it) gapWhenUsed else 0.dp
    }
    val stubDrop by tear.animateDp(transitionSpec = { move }, label = "stubDrop") {
        if (it) dropWhenUsed else 0.dp
    }
    val stubTilt by tear.animateFloat(transitionSpec = { tilt }, label = "stubTilt") {
        if (it) 3.5f else 0f
    }
    val topLift by tear.animateDp(transitionSpec = { move }, label = "topLift") {
        if (it) liftWhenUsed else 0.dp
    }
    val stubShadow by tear.animateDp(transitionSpec = { move }, label = "stubShadow") {
        if (it) shadowWhenUsed else shadowAtRest
    }
    return TicketTear(gap, stubDrop, stubTilt, topLift, stubShadow)
}

internal fun tornTicketShapes(
    used: Boolean,
    corner: Dp
): Pair<RoundedCornerShape, RoundedCornerShape> {
    val top = if (used) {
        RoundedCornerShape(topStart = corner, topEnd = corner, bottomStart = 12.dp, bottomEnd = 12.dp)
    } else {
        RoundedCornerShape(topStart = corner, topEnd = corner, bottomStart = 0.dp, bottomEnd = 0.dp)
    }
    val stub = if (used) {
        RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = corner, bottomEnd = corner)
    } else {
        RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomStart = corner, bottomEnd = corner)
    }
    return top to stub
}
