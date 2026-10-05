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

/**
 * Debug builds only. When on, sensitive glyphs are blurred in the framebuffer
 * so a screen recording can show the layout without names, numbers, references,
 * or a readable PIN pad. Release builds leave this off.
 */
val LocalRecordingPrivacy = staticCompositionLocalOf { false }

@Composable
fun Modifier.recordingPrivacyCover(
    active: Boolean = true,
    radius: Dp = 14.dp
): Modifier {
    if (!active || !LocalRecordingPrivacy.current) return this
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
