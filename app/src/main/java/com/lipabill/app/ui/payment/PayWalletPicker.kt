package com.lipabill.app.ui.payment

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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.lipabill.app.data.model.AppRegion
import com.lipabill.app.data.model.MoneySource
import com.lipabill.app.ui.components.WalletMark
import com.lipabill.app.ui.sheet.MoneyFlowSheetHeader
import com.lipabill.app.ui.theme.Space
import com.lipabill.app.ui.privacy.recordingPrivacyCover
import com.lipabill.app.ui.theme.Accent
import com.lipabill.app.ui.theme.CardWhite
import com.lipabill.app.ui.theme.Expense
import com.lipabill.app.ui.theme.Hairline
import com.lipabill.app.ui.theme.HomeType
import com.lipabill.app.ui.theme.Ink
import com.lipabill.app.ui.theme.Mute
import com.lipabill.app.ui.theme.SoftBlue
import com.lipabill.app.ui.util.formatKsh
import com.lipabill.app.ussd.PaymentWallet
import com.lipabill.app.ussd.SimLine
import java.text.NumberFormat
import java.util.Locale

private val FromCardShape = RoundedCornerShape(18.dp)

/** Whole-unit money in the sheet header's style: "KSh 12,340" in Kenya, else the region symbol. */
private fun walletMoney(region: AppRegion, amount: Double): String =
    if (region == AppRegion.KENYA) {
        formatKsh(amount)
    } else {
        "${region.currencySymbol} " + NumberFormat.getIntegerInstance(Locale.US).format(Math.round(amount))
    }

/** "KSh 12,340 available" for [source], or null before that wallet has sent a balance SMS. */
private fun availableLine(region: AppRegion, source: MoneySource?, balances: Map<MoneySource, Double>): String? {
    val balance = source?.let { balances[it] } ?: return null
    return "${walletMoney(region, balance)} available"
}

/**
 * "From" row on Send, Pay and Repeat: the wallet that will dial and its balance.
 * With two or more dialable lines, Change opens a dropdown overlay (does not push the sheet).
 * Same-MNO dual-SIM sets [requiresExplicitPick] so Confirm waits until a line is chosen.
 * On-device MNO wallets only; banks never appear here.
 */
@Composable
fun PayWalletPicker(
    region: AppRegion,
    eligibleLines: List<SimLine>,
    selectedSubscriptionId: Int?,
    activeWallet: PaymentWallet?,
    missingMessage: String,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    balances: Map<MoneySource, Double> = emptyMap(),
    requiresExplicitPick: Boolean = false,
    explicitPickMessage: String? = null,
    sessionPickedSubscriptionId: Int? = null
) {
    if (eligibleLines.isEmpty()) {
        Text(
            text = missingMessage,
            style = HomeType.caption,
            color = Expense,
            modifier = modifier
                .fillMaxWidth()
                .clip(FromCardShape)
                .background(CardWhite)
                .border(1.dp, Hairline, FromCardShape)
                .padding(16.dp)
        )
        return
    }
    val awaitingPick = requiresExplicitPick && sessionPickedSubscriptionId == null
    val displaySubId = sessionPickedSubscriptionId ?: selectedSubscriptionId
    val line = eligibleLines.firstOrNull { it.subscriptionId == displaySubId }
        ?: eligibleLines.first()
    val wallet = activeWallet ?: line.payWalletFor(region)
    val switchable = eligibleLines.size > 1
    var open by remember(awaitingPick) { mutableStateOf(awaitingPick && switchable) }
    val density = LocalDensity.current
    var menuWidth by remember { mutableStateOf(0.dp) }
    val subtitle = when {
        awaitingPick ->
            explicitPickMessage ?: "Two lines — pick which pays."
        else ->
            availableLine(region, wallet?.ledgerSource, balances) ?: line.label
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { size ->
                menuWidth = with(density) { size.width.toDp() }
            }
            .clip(FromCardShape)
            .background(CardWhite)
            .border(1.dp, if (awaitingPick) SoftBlue else Hairline, FromCardShape)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .then(
                    if (switchable) {
                        Modifier.clickable(
                            role = Role.DropdownList,
                            onClick = { open = !open }
                        )
                    } else {
                        Modifier
                    }
                )
                .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "From",
                style = HomeType.label,
                fontWeight = FontWeight.SemiBold,
                color = Mute,
                modifier = Modifier.width(40.dp)
            )
            wallet?.let { WalletMark(it.ledgerSource, size = 32.dp) }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (awaitingPick) {
                        wallet?.networkName?.let { "Pick $it line" } ?: "Pick payment line"
                    } else {
                        wallet?.badge ?: line.label
                    },
                    style = HomeType.rowTitle,
                    fontWeight = FontWeight.SemiBold,
                    color = Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = HomeType.caption,
                    color = if (awaitingPick) Accent else Mute,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.recordingPrivacyCover()
                )
            }
            if (switchable) {
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowDown,
                    contentDescription = "Change wallet",
                    tint = Mute,
                    modifier = Modifier.size(22.dp)
                )
            } else {
                Spacer(modifier = Modifier.width(4.dp))
            }
        }
        DropdownMenu(
            expanded = open && switchable,
            onDismissRequest = { open = false },
            offset = DpOffset(0.dp, 4.dp),
            shape = FromCardShape,
            containerColor = CardWhite,
            modifier = Modifier
                .then(if (menuWidth > 0.dp) Modifier.width(menuWidth) else Modifier.fillMaxWidth())
                .border(1.dp, Hairline, FromCardShape)
        ) {
            WalletOptions(
                region = region,
                lines = eligibleLines,
                selectedSubscriptionId = if (awaitingPick) null else line.subscriptionId,
                balances = balances,
                onSelect = {
                    open = false
                    onSelect(it)
                },
                modifier = Modifier
                    .width(menuWidth.coerceAtLeast(200.dp))
                    .padding(6.dp)
            )
        }
    }
}

/** One radio row per dialable line: mark, wallet, SIM, balance. */
@Composable
private fun WalletOptions(
    region: AppRegion,
    lines: List<SimLine>,
    selectedSubscriptionId: Int?,
    balances: Map<MoneySource, Double>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.selectableGroup()) {
        lines.forEach { option ->
            val wallet = option.payWalletFor(region)
            val selected = option.subscriptionId == selectedSubscriptionId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (selected) SoftBlue else CardWhite)
                    .selectable(
                        selected = selected,
                        role = Role.RadioButton,
                        onClick = { onSelect(option.subscriptionId) }
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                wallet?.let { WalletMark(it.ledgerSource, size = 32.dp) }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = wallet?.badge ?: option.label,
                        style = HomeType.rowTitle,
                        fontWeight = FontWeight.SemiBold,
                        color = Ink,
                        maxLines = 1
                    )
                    Text(
                        text = option.label,
                        style = HomeType.caption,
                        color = Mute,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.recordingPrivacyCover()
                    )
                }
                val balance = wallet?.ledgerSource?.let { balances[it] }
                if (balance != null) {
                    Text(
                        text = walletMoney(region, balance),
                        style = HomeType.label,
                        fontWeight = FontWeight.SemiBold,
                        color = Ink,
                        modifier = Modifier.recordingPrivacyCover()
                    )
                }
            }
        }
    }
}


/**
 * Shared Send / Pay details header: title, then the From picker (balance lives on the picker).
 */
@Composable
fun MoneySheetWalletBlock(
    title: String,
    region: AppRegion,
    eligibleLines: List<SimLine>,
    selectedSubscriptionId: Int?,
    activeWallet: PaymentWallet?,
    missingMessage: String,
    onSelect: (Int) -> Unit,
    showPicker: Boolean,
    balances: Map<MoneySource, Double> = emptyMap(),
    requiresExplicitPick: Boolean = false,
    explicitPickMessage: String? = null,
    sessionPickedSubscriptionId: Int? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.gap)) {
        MoneyFlowSheetHeader(title = title)
        if (showPicker) {
            PayWalletPicker(
                region = region,
                eligibleLines = eligibleLines,
                selectedSubscriptionId = selectedSubscriptionId,
                activeWallet = activeWallet,
                missingMessage = missingMessage,
                onSelect = onSelect,
                balances = balances,
                requiresExplicitPick = requiresExplicitPick,
                explicitPickMessage = explicitPickMessage,
                sessionPickedSubscriptionId = sessionPickedSubscriptionId
            )
        }
    }
}
