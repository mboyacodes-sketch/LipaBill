package com.lipabill.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lipabill.app.ui.privacy.recordingPrivacyCover
import com.lipabill.app.ui.theme.Expense
import com.lipabill.app.ui.theme.SoftBlue
import com.lipabill.app.ui.theme.Ink
import com.lipabill.app.ui.theme.Mute
import com.lipabill.app.ui.util.formatKes
import kotlinx.coroutines.delay

private const val AUTO_HIDE_MS = 3_000L
private const val MASKED_BALANCE = "******"
/** Only shrink home hero balance type (e.g. HomeType.balance ~44sp). */
private const val HERO_FIT_MIN_SP = 28f

private val EyeChipFill: Color
    @Composable
    @ReadOnlyComposable
    get() = SoftBlue
private val AmountRowHeight = 48.dp
private val EyeChipSize = 36.dp

/**
 * Available balance stays masked until the user taps (asterisks or eye), then
 * auto-hides after a short TTL. When [pendingDeduction] is set and visible,
 * shows balance minus that amount (projected remaining).
 *
 * With [showEyeToggle], amount stays left and the eye is vertically centered on the right.
 */
@Composable
fun BalanceAmountRow(
    balance: Double?,
    amountStyle: TextStyle,
    modifier: Modifier = Modifier,
    pendingDeduction: Double? = null,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    showEyeToggle: Boolean = false,
    eyeInCircle: Boolean = false
) {
    var revealed by remember { mutableStateOf(false) }

    LaunchedEffect(revealed) {
        if (revealed) {
            delay(AUTO_HIDE_MS)
            revealed = false
        }
    }

    val displayAmount = when {
        !revealed -> null
        balance == null -> null
        pendingDeduction != null && pendingDeduction > 0.0 -> balance - pendingDeduction
        else -> balance
    }
    val amountColor = when {
        !revealed -> Ink
        displayAmount != null && displayAmount < 0 -> Expense
        else -> Ink
    }
    val label = if (revealed) formatKes(displayAmount) else MASKED_BALANCE
    val fittedStyle = remember(amountStyle, label, revealed) {
        if (!revealed) amountStyle
        else fitBalanceStyle(amountStyle, label)
    }
    val interaction = remember { MutableInteractionSource() }
    val toggle: () -> Unit = { revealed = !revealed }

    // Sheets / metrics: tap amount to reveal, no eye — original wrap-content row.
    if (!showEyeToggle) {
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
                modifier = Modifier
                    .recordingPrivacyCover()
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        role = Role.Button,
                        onClickLabel = if (revealed) "Hide balance" else "Show balance",
                        onClick = toggle
                    )
            )
        }
        return
    }

    // Home hero: amount left, eye chip right, tight text metrics for optical centering.
    val eyeAmountStyle = fittedStyle.copy(
        lineHeight = fittedStyle.fontSize,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.Both
        )
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(AmountRowHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Text(
            text = label,
            style = eyeAmountStyle,
            color = amountColor,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .recordingPrivacyCover()
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onClickLabel = if (revealed) "Hide balance" else "Show balance",
                    onClick = toggle
                )
        )
        Spacer(modifier = Modifier.width(12.dp))
        val eyeContent = @Composable {
            Icon(
                imageVector = if (revealed) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                contentDescription = if (revealed) "Hide balance" else "Show balance",
                tint = Mute,
                modifier = Modifier.size(18.dp)
            )
        }
        if (eyeInCircle) {
            Box(
                modifier = Modifier
                    .size(EyeChipSize)
                    .clip(CircleShape)
                    .background(EyeChipFill)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClick = toggle
                    ),
                contentAlignment = Alignment.Center
            ) {
                eyeContent()
            }
        } else {
            Box(
                modifier = Modifier
                    .size(EyeChipSize)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClick = toggle
                    ),
                contentAlignment = Alignment.Center
            ) {
                eyeContent()
            }
        }
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
