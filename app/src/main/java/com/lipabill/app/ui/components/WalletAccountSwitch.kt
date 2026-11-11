package com.lipabill.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lipabill.app.viewmodel.WalletAccount

/** M-Pesa / Fuliza switch for the home balance pill. */
@Composable
fun WalletAccountSwitch(
    account: WalletAccount,
    onSelect: (WalletAccount) -> Unit,
    modifier: Modifier = Modifier,
    accounts: List<WalletAccount> = WalletAccount.entries
) {
    if (accounts.size <= 1) return
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        accounts.forEach { option ->
            PaymentRailChip(
                label = option.chipLabel,
                selected = option == account,
                onClick = { onSelect(option) },
                onBalancePill = true
            )
        }
    }
}
