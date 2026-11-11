package com.lipabill.app.ui.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lipabill.app.ui.privacy.recordingPrivacyCover
import com.lipabill.app.ui.theme.HomeType
import com.lipabill.app.ui.theme.Ink
import com.lipabill.app.ui.theme.Mute
import com.lipabill.app.ui.theme.Space
import com.lipabill.app.ui.util.formatKsh

/** Amber note for Fuliza cover; dark text keeps it readable in both themes. */
private val FulizaNoteFill = Color(0xFFFDF0DC)
private val FulizaNoteInk = Color(0xFF5C3205)

@Composable
fun MoneyFlowSheetHeader(
    title: String,
    balance: Double?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = HomeType.section,
            fontWeight = FontWeight.Bold,
            color = Ink,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = balance?.let { "Available ${formatKsh(it)}" } ?: "Balance not read yet",
            style = HomeType.caption,
            color = Mute,
            modifier = Modifier.recordingPrivacyCover()
        )
    }
}

/** Shared cost / remaining line used by Send amount breakdown. */
@Composable
fun MoneyBreakdownLine(
    label: String,
    value: String,
    strong: Boolean = false,
    valueColor: Color = Ink
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = HomeType.caption, color = Mute, modifier = Modifier.weight(1f))
        Text(
            value,
            style = HomeType.caption,
            fontWeight = if (strong) FontWeight.Bold else FontWeight.SemiBold,
            color = valueColor,
            modifier = Modifier.recordingPrivacyCover()
        )
    }
}

/**
 * Amber Fuliza cover note. Pass [shortfall] for the “about KSh …” line;
 * omit it for the short caption used on the classic amount pad.
 */
@Composable
fun FulizaShortfallNote(
    shortfall: Double? = null,
    modifier: Modifier = Modifier
) {
    if (shortfall != null && shortfall <= 0.0) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(FulizaNoteFill)
            .padding(Space.card),
        horizontalArrangement = Arrangement.spacedBy(Space.block)
    ) {
        Icon(
            Icons.Outlined.Bolt,
            contentDescription = null,
            tint = FulizaNoteInk,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = if (shortfall != null) {
                "Fuliza covers the rest of this payment: about ${formatKsh(shortfall)} " +
                    "plus a 1% access fee."
            } else {
                "Fuliza covers the rest of this payment"
            },
            style = HomeType.caption,
            color = FulizaNoteInk,
            modifier = Modifier.recordingPrivacyCover(shortfall != null)
        )
    }
}
