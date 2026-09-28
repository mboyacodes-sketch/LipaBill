package com.lipabill.app.ui.adapt

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Width of the current window. Fold, unfold, and split-screen all update it. */
enum class WindowWidth {
    /** Phone portrait and a folded foldable. */
    Compact,
    /** Unfolded foldable and a small tablet. */
    Medium,
    /** Tablet and a large unfolded foldable. */
    Expanded
}

data class WindowForm(val width: WindowWidth, val widthDp: Int)

fun windowFormFor(widthDp: Int): WindowForm {
    val width = when {
        widthDp < 600 -> WindowWidth.Compact
        widthDp < 840 -> WindowWidth.Medium
        else -> WindowWidth.Expanded
    }
    return WindowForm(width, widthDp)
}

val LocalWindowForm = staticCompositionLocalOf { WindowForm(WindowWidth.Compact, 360) }

@Composable
fun ProvideWindowWidth(content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        CompositionLocalProvider(
            LocalWindowForm provides windowFormFor(maxWidth.value.toInt()),
            content = content
        )
    }
}

/**
 * Single-column screens use the full foldable width, and sit in a wider
 * centered column on a tablet so lines of text do not span the glass.
 */
@Composable
fun AdaptiveFrame(
    expandedMax: Dp = 960.dp,
    content: @Composable () -> Unit
) {
    if (LocalWindowForm.current.width != WindowWidth.Expanded) {
        content()
        return
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth()
                .widthIn(max = expandedMax)
        ) {
            content()
        }
    }
}
