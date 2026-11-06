package com.lipabill.app.ui.privacy

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lipabill.app.BuildConfig

/**
 * Debug / internal (sideload) builds only.
 *
 * Off by default: FLAG_SECURE stays on. When enabled in Settings, FLAG_SECURE is
 * cleared so scrcpy / screen recording can capture, and sensitive glyphs are
 * blurred in the framebuffer. Keyboards stay sharp while the toggle is on.
 * Play release builds leave capture blocked and this mode unavailable.
 */
object RecordingCapture {
    /** True on debug and internal sideload builds — never on Play release. */
    val available: Boolean
        get() = BuildConfig.DEBUG || BuildConfig.SIDELOAD_DISTRIBUTION
}

val LocalRecordingPrivacy = staticCompositionLocalOf { false }

@Composable
fun Modifier.recordingPrivacyCover(
    active: Boolean = true,
    radius: Dp = 14.dp
): Modifier {
    if (!RecordingCapture.available || !active || !LocalRecordingPrivacy.current) return this
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        this
            .clip(RoundedCornerShape(6.dp))
            .blur(radius, BlurredEdgeTreatment.Rectangle)
    } else {
        this.drawWithContent {
            drawContent()
            drawRect(Color(0xF2F6F7F9))
        }
    }
}
