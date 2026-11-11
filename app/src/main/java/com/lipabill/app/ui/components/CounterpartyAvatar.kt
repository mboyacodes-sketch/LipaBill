package com.lipabill.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lipabill.app.data.model.MpesaTransaction
import com.lipabill.app.data.model.TransactionType
import com.lipabill.app.ui.privacy.recordingPrivacyCover
import com.lipabill.app.ui.theme.HomeType

/** Sign + whether the amount should use the outgoing/debit color. */
data class TxAmountLook(
    val prefix: String,
    val outgoingStyle: Boolean
)

fun txAmountLook(tx: MpesaTransaction): TxAmountLook {
    val fuliza = tx.type == TransactionType.FULIZA
    val outgoing = tx.type.isOutgoing()
    return TxAmountLook(
        prefix = when {
            fuliza -> ""
            outgoing -> "−"
            else -> "+"
        },
        outgoingStyle = fuliza || outgoing
    )
}

fun counterpartyInitial(name: String?): String =
    name?.trim()?.firstOrNull()?.uppercaseChar()?.toString() ?: "?"

@Composable
fun CounterpartyAvatar(
    name: String?,
    fill: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    border: Color? = null,
    textStyle: TextStyle = HomeType.rowTitle,
    fontWeight: FontWeight? = null,
    privacyCover: Boolean = false
) {
    Box(
        modifier = modifier
            .size(size)
            .then(if (border != null) Modifier.border(1.5.dp, border, CircleShape) else Modifier)
            .clip(CircleShape)
            .background(fill),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = counterpartyInitial(name),
            style = textStyle,
            color = contentColor,
            fontWeight = fontWeight,
            modifier = Modifier.recordingPrivacyCover(privacyCover)
        )
    }
}
