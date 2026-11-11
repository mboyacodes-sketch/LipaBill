package com.lipabill.app.ui.adapt

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
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

/** Height of the current window — short phones, split-screen, and tall tablets. */
enum class WindowHeight {
    /** Short phone, landscape, or a squat split. */
    Compact,
    /** Typical phone portrait. */
    Medium,
    /** Tall phone or tablet. */
    Expanded
}

data class WindowForm(
    val width: WindowWidth,
    val widthDp: Int,
    val height: WindowHeight = WindowHeight.Medium,
    val heightDp: Int = 800
)

fun windowFormFor(widthDp: Int, heightDp: Int = 800): WindowForm {
    val width = when {
        widthDp < 600 -> WindowWidth.Compact
        widthDp < 840 -> WindowWidth.Medium
        else -> WindowWidth.Expanded
    }
    val height = when {
        heightDp < 640 -> WindowHeight.Compact
        heightDp < 840 -> WindowHeight.Medium
        else -> WindowHeight.Expanded
    }
    return WindowForm(width, widthDp, height, heightDp)
}

/**
 * Visible height for Pay/Send sheets. Short windows use more of the glass so the
 * keypad still fits; tall tablets leave a slightly larger strip of home above.
 */
fun moneySheetHeightFraction(heightDp: Int): Float = when {
    heightDp < 640 -> 0.94f
    heightDp < 840 -> 0.90f
    else -> 0.88f
}

/** Default amount-pad key height for the current window height class. */
@Composable
@ReadOnlyComposable
fun moneySheetAmountKeyHeight(): Dp = when (LocalWindowForm.current.height) {
    WindowHeight.Compact -> 42.dp
    WindowHeight.Medium -> 48.dp
    WindowHeight.Expanded -> 52.dp
}

/** Search-hit overlay cap so short sheets keep the amount/CTA visible. */
@Composable
@ReadOnlyComposable
fun moneySheetSearchOverlayMaxHeight(): Dp = when (LocalWindowForm.current.height) {
    WindowHeight.Compact -> 140.dp
    WindowHeight.Medium -> 200.dp
    WindowHeight.Expanded -> 240.dp
}

val LocalWindowForm = staticCompositionLocalOf { WindowForm(WindowWidth.Compact, 360) }

@Composable
fun ProvideWindowWidth(content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        CompositionLocalProvider(
            LocalWindowForm provides windowFormFor(
                widthDp = maxWidth.value.toInt(),
                heightDp = maxHeight.value.toInt()
            ),
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
