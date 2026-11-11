package com.lipabill.app.ui.detail

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lipabill.app.data.model.MoneySource
import com.lipabill.app.data.model.MpesaTransaction
import com.lipabill.app.data.model.TransactionType
import com.lipabill.app.ui.components.CounterpartyAvatar
import com.lipabill.app.ui.components.txAmountLook
import com.lipabill.app.ui.privacy.recordingPrivacyCover
import com.lipabill.app.ui.theme.Accent
import com.lipabill.app.ui.theme.CardWhite
import com.lipabill.app.ui.theme.OnAccent
import com.lipabill.app.ui.theme.Expense
import com.lipabill.app.ui.theme.Hairline
import com.lipabill.app.ui.theme.HomeType
import com.lipabill.app.ui.theme.Income
import com.lipabill.app.ui.theme.Ink
import com.lipabill.app.ui.theme.Mute
import com.lipabill.app.ui.theme.SoftBlue
import com.lipabill.app.ui.theme.asPillLabel
import com.lipabill.app.ui.util.copyPlainText
import com.lipabill.app.ui.util.displayLabel
import com.lipabill.app.ui.util.formatKes
import com.lipabill.app.ui.util.formatTimestamp
import com.lipabill.app.ui.util.sharePlainText
import com.lipabill.app.ussd.UssdMenuBuilder

private val DetailFill: Color
    @androidx.compose.runtime.Composable
    @androidx.compose.runtime.ReadOnlyComposable
    get() = SoftBlue

private val LabelGrey: Color
    @androidx.compose.runtime.Composable
    @androidx.compose.runtime.ReadOnlyComposable
    get() = Mute

/**
 * Floating ticket-style receipt over a light frost scrim.
 * Tap the backdrop (or X / back) to dismiss.
 */
@Composable
fun TransactionReceiptPopup(
    tx: MpesaTransaction,
    onDismiss: () -> Unit,
    onRepeat: ((Long) -> Unit)? = null
) {
    val context = LocalContext.current
    val amount = txAmountLook(tx)
    val fuliza = tx.type == TransactionType.FULIZA
    val outgoing = tx.type.isOutgoing()
    val amountColor = if (amount.outgoingStyle) Expense else Income
    val amountPrefix = amount.prefix
    val canPay = UssdMenuBuilder.canRepeat(tx)
    val typeLabel = tx.type.displayLabel()
    val avatarName = tx.counterpartyName?.takeIf { it.isNotBlank() } ?: tx.source.label

    BackHandler(onBack = onDismiss)

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Light frost only — readability comes from the blurred host content.
            .background(Color.White.copy(alpha = 0.18f))
            .safeDrawingPadding()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .padding(vertical = 28.dp)
                .shadow(18.dp, RoundedCornerShape(20.dp), clip = false)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .clip(RoundedCornerShape(20.dp))
                .background(CardWhite)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { /* keep open when tapping the receipt */ }
                )
                .verticalScroll(rememberScrollState())
        ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(2.dp)
                            .size(36.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = "Close",
                            tint = Mute,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(top = 16.dp, bottom = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CounterpartyAvatar(
                            name = avatarName,
                            fill = Color.Transparent,
                            contentColor = Accent,
                            size = 44.dp,
                            border = Accent,
                            textStyle = HomeType.section.copy(fontSize = 18.sp),
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = tx.counterpartyName?.takeIf { it.isNotBlank() } ?: tx.source.label,
                            style = HomeType.section,
                            color = Ink,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.recordingPrivacyCover(
                                !tx.counterpartyName.isNullOrBlank()
                            )
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = amountPrefix + formatKes(tx.amount),
                            color = amountColor,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Normal,
                            letterSpacing = (-0.6).sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (tx.code.isNotBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Transaction ID",
                                style = HomeType.label,
                                color = Mute
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        copyText(context, tx.code, "Transaction ID copied")
                                    }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = tx.code,
                                    color = Ink,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = 1.4.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.recordingPrivacyCover(true)
                                )
                                Icon(
                                    Icons.Outlined.ContentCopy,
                                    contentDescription = "Copy Transaction ID",
                                    tint = Mute,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatusPill(text = "Confirmed", filled = false)
                            StatusPill(text = typeLabel, filled = true)
                        }
                    }
                }

                TicketPerforation()

                DetailSection(title = if (fuliza) "Fuliza details" else "Payment details") {
                    DetailRow(if (fuliza) "Fuliza amount" else "Amount", formatKes(tx.amount))
                    if (tx.cost != null) {
                        DetailRow(if (fuliza) "Access fee" else "Fee", formatKes(tx.cost))
                    }
                    DetailRow(
                        label = when {
                            fuliza -> "Outstanding"
                            outgoing -> "Total paid"
                            else -> "Total received"
                        },
                        value = formatKes(if (fuliza) tx.balance else tx.amount),
                        emphasize = true
                    )
                }

                TicketPerforation()

                DetailSection(title = "Transaction details") {
                    DetailRow("Date", formatTimestamp(tx.timestampMillis))
                    DetailRow("Type", typeLabel)
                    if (tx.source != MoneySource.MPESA) {
                        DetailRow("Source", tx.source.label)
                    }
                    if (!tx.counterpartyPhone.isNullOrBlank()) {
                        DetailRow("Phone", tx.counterpartyPhone, coverValue = true)
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(top = 4.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (canRequestMpesaReverse(tx)) {
                        OutlinedButton(
                            onClick = { openMpesaReverseSms(context, tx) },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(22.dp),
                            border = BorderStroke(1.dp, Hairline)
                        ) {
                            Text("Reverse", style = HomeType.label, color = Ink)
                        }
                    }
                    if (canPay && onRepeat != null) {
                        Button(
                            onClick = { onRepeat(tx.id) },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(22.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SoftBlue,
                                contentColor = Accent
                            )
                        ) {
                            Text("Another", style = HomeType.label)
                        }
                    } else if (!canRequestMpesaReverse(tx)) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    OutlinedButton(
                        onClick = { shareTransaction(context, tx) },
                        modifier = Modifier.size(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Hairline),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Share,
                            contentDescription = "Share",
                            tint = Ink,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
}

@Composable
private fun StatusPill(text: String, filled: Boolean) {
    Text(
        text = text,
        style = HomeType.label.asPillLabel(),
        color = if (filled) OnAccent else Ink,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(if (filled) Accent else DetailFill)
            .border(
                width = if (filled) 0.dp else 1.dp,
                color = if (filled) Color.Transparent else Hairline,
                shape = RoundedCornerShape(percent = 50)
            )
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
private fun DetailSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(DetailFill)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = title,
            style = HomeType.label,
            color = Ink
        )
        Spacer(modifier = Modifier.height(4.dp))
        content()
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    emphasize: Boolean = false,
    coverValue: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = HomeType.caption,
            color = LabelGrey,
            modifier = Modifier.padding(end = 10.dp)
        )
        Text(
            text = value,
            color = Ink,
            fontSize = if (emphasize) 14.sp else 12.sp,
            fontWeight = if (emphasize) FontWeight.Medium else FontWeight.Normal,
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .recordingPrivacyCover(coverValue)
        )
    }
}

@Composable
private fun TicketPerforation() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(16.dp)
            .background(CardWhite)
    ) {
        val dash = LabelGrey.copy(alpha = 0.5f)
        Canvas(modifier = Modifier.fillMaxWidth().height(16.dp)) {
            val r = 8.dp.toPx()
            val cy = size.height / 2f
            drawCircle(
                color = Color.Black,
                radius = r,
                center = Offset(0f, cy),
                blendMode = BlendMode.Clear
            )
            drawCircle(
                color = Color.Black,
                radius = r,
                center = Offset(size.width, cy),
                blendMode = BlendMode.Clear
            )
            drawLine(
                color = dash,
                start = Offset(r + 4.dp.toPx(), cy),
                end = Offset(size.width - r - 4.dp.toPx(), cy),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
            )
        }
    }
}

private fun copyText(context: Context, text: String, toast: String) {
    val ok = context.copyPlainText("LipaBill", text)
    Toast.makeText(
        context,
        if (ok) toast else "Couldn’t copy right now",
        Toast.LENGTH_SHORT
    ).show()
}

private fun shareTransaction(context: Context, tx: MpesaTransaction) {
    if (!context.sharePlainText("Share transaction", shareTextFor(tx))) {
        Toast.makeText(context, "Couldn’t share right now", Toast.LENGTH_SHORT).show()
    }
}
