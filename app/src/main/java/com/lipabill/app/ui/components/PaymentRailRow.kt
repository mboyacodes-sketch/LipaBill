package com.lipabill.app.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lipabill.app.data.model.MAX_ENABLED_LEDGER_SOURCES
import com.lipabill.app.data.model.MoneySource
import com.lipabill.app.data.model.visibleLedgerSources
import com.lipabill.app.ui.LipaFilterChip
import com.lipabill.app.ui.theme.SoftBlue
import com.lipabill.app.viewmodel.WalletAccount

/**
 * Insights payment-rail row: capped ledger sources plus Fuliza.
 * One rail at a time (or All when [selectedSources] is empty). Home uses
 * [com.lipabill.app.ui.SourceFilterRow] instead.
 */
@Composable
fun PaymentRailRow(
    account: WalletAccount,
    showFuliza: Boolean,
    sources: List<MoneySource>,
    /** Empty = All; otherwise the single selected rail (Set kept for shared chips). */
    selectedSources: Set<MoneySource>,
    onSelectAll: () -> Unit,
    onSelectSource: (MoneySource) -> Unit,
    onSelectFuliza: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Selected rail stays visible ahead of the rest, within the chip cap.
    val ordered = sources.filter { it in selectedSources } + sources.filterNot { it in selectedSources }
    val shown = visibleLedgerSources(ordered, null, maxOf(MAX_ENABLED_LEDGER_SOURCES, selectedSources.size))
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
            allSelected = onLedger && selectedSources.isEmpty(),
            selectedSources = if (onLedger) selectedSources else emptySet(),
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
    selectedSources: Set<MoneySource>,
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
                    selected = source in selectedSources,
                    onClick = { onSelectSource(source) }
                )
            }
        } else if (sources.isNotEmpty()) {
            val only = sources.first()
            PaymentRailChip(
                label = only.label,
                selected = allSelected || only in selectedSources,
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
    modifier: Modifier = Modifier
) {
    LipaFilterChip(
        label = label,
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        unselectedContainer = SoftBlue,
        unselectedBorder = Color.Transparent
    )
}
