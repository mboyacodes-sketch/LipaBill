package com.lipabill.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.lipabill.app.ui.theme.Expense
import com.lipabill.app.ui.theme.Ink
import com.lipabill.app.ui.theme.Mute
import com.lipabill.app.ui.util.formatKes
import kotlinx.coroutines.delay

private const val AUTO_HIDE_MS = 3_000L
private const val MASKED_BALANCE = "••••••"

/**
 * Shows available balance hidden by default (eye to reveal), auto-hides after a few seconds
 * unless [alwaysShow] is on. When [pendingDeduction] is set and the balance is visible,
 * displays balance minus that amount (projected remaining).
 */
@Composable
fun BalanceAmountRow(
    balance: Double?,
    alwaysShow: Boolean,
    amountStyle: TextStyle,
    modifier: Modifier = Modifier,
    pendingDeduction: Double? = null,
    eyeTint: Color = Mute,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start
) {
    var revealed by remember { mutableStateOf(false) }
    val visible = alwaysShow || revealed

    LaunchedEffect(revealed, alwaysShow) {
        if (revealed && !alwaysShow) {
            delay(AUTO_HIDE_MS)
            revealed = false
        }
    }
    LaunchedEffect(alwaysShow) {
        if (alwaysShow) revealed = false
    }

    val displayAmount = when {
        !visible -> null
        balance == null -> null
        pendingDeduction != null && pendingDeduction > 0.0 -> balance - pendingDeduction
        else -> balance
    }
    val amountColor = when {
        !visible -> Ink
        displayAmount != null && displayAmount < 0 -> Expense
        else -> Ink
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = horizontalArrangement
    ) {
        Text(
            text = if (visible) formatKes(displayAmount) else MASKED_BALANCE,
            style = amountStyle,
            color = amountColor
        )
        if (!alwaysShow) {
            IconButton(
                onClick = { revealed = !revealed },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (revealed) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                    contentDescription = if (revealed) "Hide balance" else "Show balance",
                    tint = eyeTint,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
