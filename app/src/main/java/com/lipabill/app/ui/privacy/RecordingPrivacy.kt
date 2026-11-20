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
 * On by default for debug / internal builds: FLAG_SECURE is cleared so scrcpy /
 * screen recording can capture. Ledger PII is blurred; Request shows sharp dummy
 * destinations/handles instead of masking. Keyboards stay sharp while the toggle
 * is on. Turn off in Settings to restore capture blocking. Play release builds
 * leave capture blocked.
 *
 * What a demo may show vs. what gets covered:
 * - Covered: who (counterparty, guest and passenger names and their initials, phones,
 *   accounts, SIM numbers, free-text SMS / notes that carry them), keys (confirmation
 *   codes, QR and barcodes, booking / order refs), what the user holds (wallet balances,
 *   available / remaining, Fuliza owed, safe-to-spend) and exact seats (row, seat).
 * - Shown: amounts of single payments, typed amounts, fees, totals, budgets, goals,
 *   charts, dates, categories, rails, and event title, venue, gate, section, train, coach.
 */
object RecordingCapture {
    /** True on debug and internal sideload builds — never on Play release. */
    val available: Boolean
        get() = BuildConfig.DEBUG || BuildConfig.SIDELOAD_DISTRIBUTION
}

val LocalRecordingPrivacy = staticCompositionLocalOf { false }

private val ExactSeat = Regex("""(?i)\b(row|seat)\b""")

/** True when [seatLine] pins a person to one spot (row or seat), not just a gate or section. */
fun pinsExactSeat(seatLine: String?): Boolean = seatLine != null && ExactSeat.containsMatchIn(seatLine)

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
