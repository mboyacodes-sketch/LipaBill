package com.lipabill.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 *
 * Font size steps down for large balances so values like 50,333.28 are never clipped to
 * look like 5,033.28 inside the home card.
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
    val label = if (visible) formatKes(displayAmount) else MASKED_BALANCE
    val fittedStyle = remember(amountStyle, label, visible) {
        if (!visible) amountStyle
        else fitBalanceStyle(amountStyle, label)
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = horizontalArrangement
    ) {
        Text(
            text = label,
            style = fittedStyle,
            color = amountColor,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            textAlign = if (horizontalArrangement == Arrangement.Center) {
                TextAlign.Center
            } else {
                TextAlign.Start
            },
            modifier = Modifier.weight(1f, fill = true)
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

/**
 * Shrink hero balance type from the formatted string length so 1,000,000.00 and
 * 12,345,678.90 stay on one line without looking truncated.
 */
internal fun fitBalanceStyle(base: TextStyle, formatted: String): TextStyle {
    val factor = when {
        formatted.length >= 14 -> 0.52f // e.g. 12,345,678.90
        formatted.length >= 12 -> 0.58f // e.g. 1,250,333.28
        formatted.length >= 10 -> 0.68f // e.g. 250,333.28
        formatted.length >= 9 -> 0.78f  // e.g. 50,333.28
        else -> 1f
    }
    if (factor == 1f) return base
    return base.copy(
        fontSize = (base.fontSize.value * factor).sp,
        lineHeight = (base.lineHeight.value * factor).sp
    )
}
