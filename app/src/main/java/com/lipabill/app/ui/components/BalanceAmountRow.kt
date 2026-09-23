package com.lipabill.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.lipabill.app.ui.theme.Expense
import com.lipabill.app.ui.theme.Ink
import com.lipabill.app.ui.util.formatKes
import kotlinx.coroutines.delay

private const val AUTO_HIDE_MS = 3_000L
private const val MASKED_BALANCE = "******"
/** Only shrink home hero balance type (e.g. HomeType.balance ~44sp). */
private const val HERO_FIT_MIN_SP = 28f

/**
 * Shows available balance hidden by default (tap asterisks to reveal), auto-hides
 * after a few seconds unless [alwaysShow] is on. When [pendingDeduction] is set and
 * the balance is visible, displays balance minus that amount (projected remaining).
 *
 * Layout is wrap-content. Pass [modifier] with `fillMaxWidth()` and
 * [horizontalArrangement] = Center for the home hero so the amount stays centered.
 */
@Composable
fun BalanceAmountRow(
    balance: Double?,
    alwaysShow: Boolean,
    amountStyle: TextStyle,
    modifier: Modifier = Modifier,
    pendingDeduction: Double? = null,
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
    val interaction = remember { MutableInteractionSource() }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = horizontalArrangement
    ) {
        Text(
            text = label,
            style = fittedStyle,
            color = amountColor,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = if (alwaysShow) {
                Modifier
            } else {
                Modifier.clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onClickLabel = if (revealed) "Hide balance" else "Show balance"
                ) {
                    revealed = !revealed
                }
            }
        )
    }
}

/**
 * Shrink hero balance type from the formatted string length so multi-million values
 * stay on one line. Smaller caption/greeting styles are left alone.
 */
internal fun fitBalanceStyle(base: TextStyle, formatted: String): TextStyle {
    if (base.fontSize.value < HERO_FIT_MIN_SP) return base
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
