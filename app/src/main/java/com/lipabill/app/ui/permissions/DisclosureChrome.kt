package com.lipabill.app.ui.permissions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lipabill.app.ui.adapt.LocalWindowForm
import com.lipabill.app.ui.adapt.WindowWidth

/**
 * Extra air around first-run / accessibility disclosure pages.
 * Scales with window height so short split-screens stay usable and tall phones
 * keep copy off the status bar, home indicator, and the illustration split.
 */
internal data class DisclosureChrome(
    val top: Dp,
    val bottom: Dp,
    val side: Dp,
    val art: Dp,
    val copy: Dp
)

@Composable
@ReadOnlyComposable
internal fun disclosureChrome(): DisclosureChrome {
    val heightDp = LocalConfiguration.current.screenHeightDp
    val side = when (LocalWindowForm.current.width) {
        WindowWidth.Compact -> 28.dp
        WindowWidth.Medium -> 36.dp
        WindowWidth.Expanded -> 48.dp
    }
    return when {
        heightDp < 560 -> DisclosureChrome(
            top = 16.dp,
            bottom = 20.dp,
            side = side,
            art = 12.dp,
            copy = 12.dp
        )
        heightDp < 740 -> DisclosureChrome(
            top = 24.dp,
            bottom = 28.dp,
            side = side,
            art = 18.dp,
            copy = 16.dp
        )
        else -> DisclosureChrome(
            top = 32.dp,
            bottom = 36.dp,
            side = side,
            art = 24.dp,
            copy = 20.dp
        )
    }
}
