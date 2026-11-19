package com.lipabill.app.ui.tickets

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha

/** Shared fade for a pass that has been marked used. */
internal const val UsedPassAlpha = 0.45f

internal fun Modifier.fadeWhenUsed(used: Boolean): Modifier =
    if (used) alpha(UsedPassAlpha) else this
