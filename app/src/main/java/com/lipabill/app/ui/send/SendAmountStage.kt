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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lipabill.app.ui.privacy.recordingPrivacyCover
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
import com.lipabill.app.ui.util.formatKsh
import com.lipabill.app.ui.util.formatMoneyInputDisplay
import com.lipabill.app.viewmodel.maskPhone
import com.lipabill.app.viewmodel.sentThisMonthCopy

/** Amber note for Fuliza cover; dark text keeps it readable in both themes. */
private val FulizaNoteFill = Color(0xFFFDF0DC)
private val FulizaNoteInk = Color(0xFF5C3205)

private val QUICK_AMOUNTS = listOf("500", "1000", "2000", "5000")

/**
 * Send's amount step: who, how much, what it costs, and what is left.
 * The keypad and button below come from the shared money sheet.
 */
@Composable
internal fun SendAmountStage(
    balance: Double?,
    recipientName: String?,
    recipientPhone: String?,
    sentThisMonth: Int,
    amountInput: String,
    amount: Double?,
    feeEstimate: Double?,
    onChangeRecipient: () -> Unit,
    onQuickAmount: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val fee = feeEstimate ?: 0.0
    val total = amount?.let { it + fee }
    val shortfall = if (total != null && balance != null && total > balance) total - balance else null
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SendSheetHeader(balance)

        RecipientCard(
            name = recipientName,
            phone = recipientPhone,
            sentThisMonth = sentThisMonth,
            onChange = onChangeRecipient
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Enter amount", style = HomeType.caption, color = Mute)
            Row(verticalAlignment = Alignment.Bottom) {
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            QUICK_AMOUNTS.forEach { quick ->
                val on = amountInput == quick
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .heightIn(min = 40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .border(if (on) 2.dp else 1.dp, if (on) Accent else Hairline, RoundedCornerShape(20.dp))
                        .background(if (on) SoftBlue else CardWhite)
                        .clickable(role = Role.Button) { onQuickAmount(quick) }
                        .padding(horizontal = 14.dp)
                ) {
                    Text(
                        formatKsh(quick.toDouble()).removePrefix("KSh "),
                        style = HomeType.label,
                        fontWeight = FontWeight.SemiBold,
                        color = if (on) Accent else Ink
                    )
                }
            }
        }

        if (amount != null && total != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Canvas)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                BreakdownLine(
                    label = "Transaction cost",
                    value = feeEstimate?.let { "about ${formatKsh(it)}" } ?: "Shown by M-Pesa"
                )
                BreakdownLine(label = "Total", value = formatKsh(total))
                if (balance != null) {
                    val remaining = balance - total
                    BreakdownLine(
                        label = "Remaining balance",
                        value = formatKsh(remaining.coerceAtLeast(0.0)),
                        strong = true,
                        valueColor = if (remaining < 0) Expense else Ink
                    )
                }
            }
        }

        if (shortfall != null && shortfall > 0.0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(FulizaNoteFill)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    Icons.Outlined.Bolt,
                    contentDescription = null,
                    tint = FulizaNoteInk,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Fuliza covers the rest of this payment: about ${formatKsh(shortfall)} " +
                        "plus a 1% access fee.",
                    style = HomeType.caption,
                    color = FulizaNoteInk,
                    modifier = Modifier.recordingPrivacyCover()
                )
            }
        }
    }
}

/** "Send money · Available KSh …" — the same top line on both Send steps. */
@Composable
internal fun SendSheetHeader(balance: Double?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Send money",
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

/** One past recipient on the first Send step, styled like the amount step's card. */
@Composable
internal fun SendRecipientRow(
    name: String,
    phone: String,
    sentThisMonth: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(if (selected) 2.dp else 1.dp, if (selected) Accent else Hairline, RoundedCornerShape(16.dp))
            .background(if (selected) SoftBlue else CardWhite)
            .clickable(role = Role.Button, onClick = onClick)
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
                name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                style = HomeType.rowTitle,
                fontWeight = FontWeight.Bold,
                color = Accent
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                name,
                style = HomeType.rowTitle,
                color = Ink,
                maxLines = 1,
                modifier = Modifier.recordingPrivacyCover()
            )
            Text(
                "${maskPhone(phone)} · ${sentThisMonthCopy(sentThisMonth)}",
                style = HomeType.caption,
                color = Mute,
                maxLines = 1,
                modifier = Modifier.recordingPrivacyCover()
            )
        }
    }
}

@Composable
private fun RecipientCard(
    name: String?,
    phone: String?,
    sentThisMonth: Int,
    onChange: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, Hairline, RoundedCornerShape(16.dp))
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
                name ?: "Choose who to send to",
                style = HomeType.rowTitle,
                color = Ink,
                maxLines = 1,
                modifier = Modifier.recordingPrivacyCover()
            )
            if (phone != null) {
                Text(
                    "${maskPhone(phone)} · ${sentThisMonthCopy(sentThisMonth)}",
                    style = HomeType.caption,
                    color = Mute,
                    maxLines = 1,
                    modifier = Modifier.recordingPrivacyCover()
                )
            }
        }
        TextButton(onClick = onChange) {
            Text("Change", style = HomeType.label, color = Accent)
        }
    }
}

@Composable
private fun BreakdownLine(
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
