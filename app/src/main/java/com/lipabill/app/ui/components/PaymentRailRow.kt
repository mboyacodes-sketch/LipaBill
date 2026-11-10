package com.lipabill.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lipabill.app.data.model.MAX_ENABLED_LEDGER_SOURCES
import com.lipabill.app.data.model.MoneySource
import com.lipabill.app.data.model.visibleLedgerSources
import com.lipabill.app.ui.theme.Accent
import com.lipabill.app.ui.theme.CardWhite
import com.lipabill.app.ui.theme.HomeType
import com.lipabill.app.ui.theme.Ink
import com.lipabill.app.ui.theme.Mute
import com.lipabill.app.ui.theme.OnAccent
import com.lipabill.app.ui.theme.SoftBlue
import com.lipabill.app.viewmodel.WalletAccount

/**
 * Insights payment-rail row: capped ledger sources plus Fuliza.
 * Home keeps [WalletAccountSwitch] on the balance pill and [com.lipabill.app.ui.SourceFilterRow] below.
 */
@Composable
fun PaymentRailRow(
    account: WalletAccount,
    showFuliza: Boolean,
    sources: List<MoneySource>,
    selectedSource: MoneySource?,
    onSelectAll: () -> Unit,
    onSelectSource: (MoneySource) -> Unit,
    onSelectFuliza: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shown = visibleLedgerSources(sources, selectedSource, MAX_ENABLED_LEDGER_SOURCES)
    if (!showFuliza && sources.size <= 1) return
    val onLedger = account == WalletAccount.MPESA
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        LedgerSourceChips(
            sources = shown,
            showAllChip = sources.size > 1,
            allSelected = onLedger && selectedSource == null,
            selectedSource = selectedSource.takeIf { onLedger },
            onSelectAll = onSelectAll,
            onSelectSource = onSelectSource
        )
        if (showFuliza) {
            PaymentRailChip(
                label = WalletAccount.FULIZA.chipLabel,
                selected = account == WalletAccount.FULIZA,
                onClick = onSelectFuliza
            )
        }
    }
}

/** All + source chips shared by Insights and the home source filter. */
@Composable
fun LedgerSourceChips(
    sources: List<MoneySource>,
    showAllChip: Boolean,
    allSelected: Boolean,
    selectedSource: MoneySource?,
    onSelectAll: () -> Unit,
    onSelectSource: (MoneySource) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (showAllChip) {
            PaymentRailChip(
                label = "All",
                selected = allSelected,
                onClick = onSelectAll
            )
            sources.forEach { source ->
                PaymentRailChip(
                    label = source.label,
                    selected = selectedSource == source,
                    onClick = { onSelectSource(source) }
                )
            }
        } else if (sources.isNotEmpty()) {
            val only = sources.first()
            PaymentRailChip(
                label = only.label,
                selected = allSelected || selectedSource == only,
                onClick = onSelectAll
            )
        }
    }
}

@Composable
fun PaymentRailChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Ink-on-white for the home balance pill; accent wash everywhere else. */
    onBalancePill: Boolean = false
) {
    val selectedBg = if (onBalancePill) Ink else Accent
    val selectedFg = if (onBalancePill) CardWhite else OnAccent
    val idleBg = if (onBalancePill) Color.Transparent else SoftBlue
    val idleFg = if (onBalancePill) Mute else Ink
    Text(
        text = label,
        style = HomeType.caption,
        color = if (selected) selectedFg else idleFg,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) selectedBg else idleBg)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(
                horizontal = 10.dp,
                vertical = if (onBalancePill) 4.dp else 5.dp
            )
    )
}
