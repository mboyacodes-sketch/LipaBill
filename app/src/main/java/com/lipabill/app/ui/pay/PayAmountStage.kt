package com.lipabill.app.ui.pay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lipabill.app.ui.privacy.recordingPrivacyCover
import com.lipabill.app.ui.sheet.FulizaShortfallNote
import com.lipabill.app.ui.theme.Accent
import com.lipabill.app.ui.theme.Expense
import com.lipabill.app.ui.theme.HomeType
import com.lipabill.app.ui.theme.Ink
import com.lipabill.app.ui.theme.Mute
import com.lipabill.app.ui.theme.Space
import com.lipabill.app.ui.util.formatKsh
import com.lipabill.app.ui.util.formatMoneyInputDisplay

/**
 * Navy amount box + remaining / Fuliza note for the merged Pay sheet.
 */
@Composable
internal fun PayAmountEntry(
    amountInput: String,
    amount: Double?,
    balance: Double?,
    focused: Boolean,
    onFocus: () -> Unit,
    modifier: Modifier = Modifier
) {
    val remaining = if (amount != null && balance != null) balance - amount else null
    val shortfall = remaining?.takeIf { it < 0 }?.let { -it }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Space.gap)
    ) {
        Text(
            "Amount",
            style = HomeType.caption,
            fontWeight = FontWeight.SemiBold,
            color = Mute
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(2.dp, Accent, RoundedCornerShape(14.dp))
                .background(if (focused) Accent.copy(alpha = 0.06f) else Color.Transparent)
                .clickable(role = Role.Button, onClick = onFocus)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "KSh",
                style = HomeType.section,
                fontWeight = FontWeight.SemiBold,
                color = Mute
            )
            Text(
                text = formatMoneyInputDisplay(amountInput).ifEmpty { "0" },
                style = HomeType.greeting,
                fontWeight = FontWeight.Bold,
                color = if (amountInput.isEmpty()) Mute else Ink,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .recordingPrivacyCover()
            )
        }
        when {
            remaining != null -> Text(
                "Remaining balance ${formatKsh(remaining.coerceAtLeast(0.0))}",
                style = HomeType.caption,
                color = if (remaining < 0) Expense else Mute,
                modifier = Modifier.recordingPrivacyCover()
            )
            balance != null -> Text(
                "Available ${formatKsh(balance)}",
                style = HomeType.caption,
                color = Mute,
                modifier = Modifier.recordingPrivacyCover()
            )
        }
        if (shortfall != null && shortfall > 0.0) {
            FulizaShortfallNote(shortfall = shortfall)
        }
    }
}
