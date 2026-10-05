package com.lipabill.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lipabill.app.ui.theme.CardWhite
import com.lipabill.app.ui.theme.HomeType
import com.lipabill.app.ui.theme.Ink
import com.lipabill.app.ui.theme.Mute
import com.lipabill.app.viewmodel.WalletAccount

@Composable
fun WalletAccountSwitch(
    account: WalletAccount,
    onSelect: (WalletAccount) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        WalletAccount.entries.forEach { option ->
            val selected = option == account
            Text(
                text = option.chipLabel,
                style = HomeType.caption,
                color = if (selected) CardWhite else Mute,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (selected) Ink else Color.Transparent)
                    .clickable { onSelect(option) }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}
