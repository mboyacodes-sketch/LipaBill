package com.lipabill.app.ui.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.lipabill.app.ui.theme.Accent
import com.lipabill.app.ui.theme.Canvas
import com.lipabill.app.ui.theme.Expense
import com.lipabill.app.ui.theme.HomeType
import com.lipabill.app.ui.theme.Ink
import com.lipabill.app.ui.theme.SoftBlue
import com.lipabill.app.ui.theme.Space

enum class SheetFeedbackTone {
    /** Neutral next-step coaching. */
    Hint,
    /** Something is incomplete or unmatched — user should act. */
    Warning,
    /** Hard failure / blocked action. */
    Error,
    /** Progress / confirmation after an action. */
    Info
}

/**
 * Inline status used on money sheets so empty searches, validation, and dial
 * results are never silent.
 */
@Composable
fun SheetFeedback(
    message: String,
    tone: SheetFeedbackTone = SheetFeedbackTone.Hint,
    modifier: Modifier = Modifier
) {
    val (bg, fg) = when (tone) {
        SheetFeedbackTone.Hint -> Canvas to Ink
        SheetFeedbackTone.Warning -> SoftBlue to Accent
        SheetFeedbackTone.Error -> Expense.copy(alpha = 0.12f) to Expense
        SheetFeedbackTone.Info -> SoftBlue to Accent
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .padding(horizontal = Space.block, vertical = Space.gap)
    ) {
        Text(
            text = message,
            style = HomeType.body,
            color = fg
        )
    }
}

/** Prefer [status] (action result) over [guidance] (what to do next). */
fun resolveSheetFeedback(
    status: String?,
    guidance: String?
): Pair<String, SheetFeedbackTone>? {
    val trimmedStatus = status?.trim()?.takeIf { it.isNotEmpty() }
    if (trimmedStatus != null) {
        return trimmedStatus to SheetFeedbackTone.Info
    }
    val trimmedGuidance = guidance?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val tone = when {
        trimmedGuidance.startsWith("No saved", ignoreCase = true) ||
            trimmedGuidance.startsWith("No match", ignoreCase = true) ||
            trimmedGuidance.contains("not found", ignoreCase = true) ->
            SheetFeedbackTone.Warning
        trimmedGuidance.contains("need", ignoreCase = true) ||
            trimmedGuidance.contains("enter", ignoreCase = true) ||
            trimmedGuidance.contains("choose", ignoreCase = true) ||
            trimmedGuidance.contains("add ", ignoreCase = true) ->
            SheetFeedbackTone.Hint
        else -> SheetFeedbackTone.Hint
    }
    return trimmedGuidance to tone
}
