package com.lipabill.app.ui.send

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.lipabill.app.ui.adapt.moneySheetSearchOverlayMaxHeight
import com.lipabill.app.ui.privacy.recordingPrivacyCover
import com.lipabill.app.ui.sheet.FulizaShortfallNote
import com.lipabill.app.ui.sheet.MoneyBreakdownLine
import com.lipabill.app.ui.sheet.MoneyFlowSheetHeader
import com.lipabill.app.ui.sheet.SheetHeroAmountStyle
import com.lipabill.app.ui.theme.Accent
import com.lipabill.app.ui.theme.Canvas
import com.lipabill.app.ui.theme.CardWhite
import com.lipabill.app.ui.theme.Expense
import com.lipabill.app.ui.theme.Hairline
import com.lipabill.app.ui.theme.HomeType
import com.lipabill.app.ui.theme.Ink
import com.lipabill.app.ui.theme.Mute
import com.lipabill.app.ui.theme.SoftBlue
import com.lipabill.app.ui.theme.Space
import com.lipabill.app.ui.util.formatKsh
import com.lipabill.app.ui.util.formatMoneyInputDisplay
import com.lipabill.app.viewmodel.maskPhone
import com.lipabill.app.viewmodel.sentThisMonthCopy

/** "Send money · Available KSh …" */
@Composable
internal fun SendSheetHeader(balance: Double?) {
    MoneyFlowSheetHeader(title = "Send money", balance = balance)
}

/** Selected recipient card from the Send amount artboard. */
@Composable
internal fun SendPartyCard(
    name: String?,
    phone: String?,
    sentThisMonth: Int,
    emptyLabel: String = "Choose who to send to",
    onChange: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val subtitle = when {
        phone != null && onChange != null ->
            "${maskPhone(phone)} · ${sentThisMonthCopy(sentThisMonth)}"
        phone != null -> maskPhone(phone)
        else -> null
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, Hairline, RoundedCornerShape(16.dp))
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(SoftBlue)
        ) {
            Text(
                name?.trim()?.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                style = HomeType.rowTitle,
                fontWeight = FontWeight.Bold,
                color = Accent
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                name ?: emptyLabel,
                style = HomeType.rowTitle,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.recordingPrivacyCover()
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = HomeType.caption,
                    color = Mute,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.recordingPrivacyCover()
                )
            }
        }
        if (onChange != null) {
            TextButton(onClick = onChange) {
                Text("Change", style = HomeType.label, color = Accent)
            }
        }
    }
}

/**
 * Search hits float over content under the field so the input does not scroll away.
 */
@Composable
internal fun SendSearchResultsOverlay(
    matches: List<SendSearchMatch>,
    onSelect: (SendSearchMatch) -> Unit,
    modifier: Modifier = Modifier
) {
    if (matches.isEmpty()) return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .zIndex(2f)
            .padding(top = 4.dp)
            .heightIn(max = moneySheetSearchOverlayMaxHeight())
            .shadow(10.dp, RoundedCornerShape(16.dp), clip = false)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, Hairline, RoundedCornerShape(16.dp))
            .background(CardWhite)
            .verticalScroll(rememberScrollState())
    ) {
        matches.forEachIndexed { index, match ->
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .height(1.dp)
                        .background(Hairline)
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { onSelect(match) }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(SoftBlue)
                ) {
                    Text(
                        match.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                        style = HomeType.rowTitle,
                        fontWeight = FontWeight.Bold,
                        color = Accent
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        match.name,
                        style = HomeType.rowTitle,
                        color = Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.recordingPrivacyCover()
                    )
                    Text(
                        maskPhone(match.phone),
                        style = HomeType.caption,
                        color = Mute,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.recordingPrivacyCover()
                    )
                }
            }
        }
    }
}

data class SendSearchMatch(
    val id: Long,
    val name: String,
    val phone: String,
    val sentThisMonth: Int
)

/**
 * Amount page body: centered amount, cost breakdown, Fuliza note.
 */
@Composable
internal fun SendAmountEntry(
    amountInput: String,
    amount: Double?,
    balance: Double?,
    feeEstimate: Double?,
    onFocus: () -> Unit,
    modifier: Modifier = Modifier
) {
    val amountOrZero = amount ?: 0.0
    val fee = feeEstimate ?: 0.0
    val total = amountOrZero + fee
    val shortfall = if (amount != null && balance != null && total > balance) total - balance else null
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.section)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onFocus)
                .padding(vertical = Space.block),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Space.gap)
        ) {
            Text(
                "Enter amount",
                style = HomeType.caption,
                color = Mute,
                textAlign = TextAlign.Center
            )
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    "KSh",
                    style = HomeType.section,
                    color = Mute,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = formatMoneyInputDisplay(amountInput).ifEmpty { "0" },
                    style = SheetHeroAmountStyle,
                    fontWeight = FontWeight.Bold,
                    color = if (amountInput.isEmpty()) Mute else Ink,
                    maxLines = 1,
                    modifier = Modifier.recordingPrivacyCover()
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Canvas)
                .padding(Space.card),
            verticalArrangement = Arrangement.spacedBy(Space.block)
        ) {
            MoneyBreakdownLine(
                label = "Transaction cost",
                value = feeEstimate?.let { "about ${formatKsh(it)}" } ?: "Shown by M-Pesa"
            )
            MoneyBreakdownLine(label = "Total", value = formatKsh(total))
            if (balance != null) {
                val remaining = balance - total
                MoneyBreakdownLine(
                    label = "Remaining balance",
                    value = formatKsh(remaining.coerceAtLeast(0.0)),
                    strong = true,
                    valueColor = if (remaining < 0) Expense else Ink
                )
            }
        }

        if (shortfall != null && shortfall > 0.0) {
            FulizaShortfallNote(shortfall = shortfall)
        }
    }
}
