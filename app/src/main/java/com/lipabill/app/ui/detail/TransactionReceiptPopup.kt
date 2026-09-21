package com.lipabill.app.ui.detail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import com.lipabill.app.data.model.MpesaTransaction
import com.lipabill.app.ui.theme.Accent
import com.lipabill.app.ui.theme.CardWhite
import com.lipabill.app.ui.theme.Expense
import com.lipabill.app.ui.theme.Hairline
import com.lipabill.app.ui.theme.HomeType
import com.lipabill.app.ui.theme.Income
import com.lipabill.app.ui.theme.Ink
import com.lipabill.app.ui.theme.Mute
import com.lipabill.app.ui.theme.SoftBlue
import com.lipabill.app.ui.util.displayLabel
import com.lipabill.app.ui.util.formatKes
import com.lipabill.app.ui.util.formatTimestamp
import com.lipabill.app.ui.util.isOutgoing
import com.lipabill.app.ussd.UssdMenuBuilder

private val DetailFill = Color(0xFFF7F8FA)
private val LabelGrey = Color(0xFF9CA3AF)

/**
 * Floating ticket-style receipt. Host must blur the content underneath
 * (same composition) — see TransactionListScreen — so the backdrop reads
 * as bokeh, not a dim overlay.
 * Tap the backdrop (or X / back) to dismiss.
 */
@Composable
fun TransactionReceiptPopup(
    tx: MpesaTransaction,
    onDismiss: () -> Unit,
    onRepeat: ((Long) -> Unit)? = null
) {
    val context = LocalContext.current
    val outgoing = tx.type.isOutgoing()
    val amountColor = if (outgoing) Expense else Income
    val amountPrefix = if (outgoing) "−" else "+"
    val canPay = UssdMenuBuilder.canRepeat(tx)
    val typeLabel = tx.type.displayLabel()

    BackHandler(onBack = onDismiss)

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Light frost only — readability comes from the blurred host content.
            .background(Color.White.copy(alpha = 0.18f))
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
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .border(1.5.dp, Accent, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "M",
                                color = Accent,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = tx.counterpartyName?.takeIf { it.isNotBlank() } ?: "M-PESA",
                            style = HomeType.section,
                            color = Ink,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
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
                        Spacer(modifier = Modifier.height(8.dp))
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

                DetailSection(title = "Payment details") {
                    DetailRow("Amount", formatKes(tx.amount))
                    if (tx.cost != null) {
                        DetailRow("Fee", formatKes(tx.cost))
                    }
                    DetailRow(
                        label = if (outgoing) "Total paid" else "Total received",
                        value = formatKes(tx.amount),
                        emphasize = true
                    )
                }

                TicketPerforation()

                DetailSection(title = "Transaction details") {
                    DetailRow("Date", formatTimestamp(tx.timestampMillis))
                    DetailRow(
                        label = "Transaction ID",
                        value = tx.code,
                        trailing = {
                            Icon(
                                Icons.Outlined.ContentCopy,
                                contentDescription = "Copy",
                                tint = Mute,
                                modifier = Modifier
                                    .size(15.dp)
                                    .clickable {
                                        copyText(context, tx.code, "Transaction ID copied")
                                    }
                            )
                        }
                    )
                    DetailRow("Type", typeLabel)
                    if (!tx.counterpartyPhone.isNullOrBlank()) {
                        DetailRow("Phone", tx.counterpartyPhone)
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
                            Text("Another transaction", style = HomeType.label)
                        }
                    } else {
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
        style = HomeType.label,
        color = if (filled) CardWhite else Ink,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (filled) Accent else DetailFill)
            .border(
                width = if (filled) 0.dp else 1.dp,
                color = if (filled) Color.Transparent else Hairline,
                shape = RoundedCornerShape(16.dp)
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
    trailing: (@Composable () -> Unit)? = null
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.weight(1f)
        ) {
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = value,
                color = Ink,
                fontSize = if (emphasize) 14.sp else 12.sp,
                fontWeight = if (emphasize) FontWeight.Medium else FontWeight.Normal,
                textAlign = TextAlign.End,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            trailing?.invoke()
        }
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
                color = LabelGrey.copy(alpha = 0.5f),
                start = Offset(r + 4.dp.toPx(), cy),
                end = Offset(size.width - r - 4.dp.toPx(), cy),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
            )
        }
    }
}

private fun copyText(context: Context, text: String, toast: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("LipaBill", text))
    Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
}

private fun shareTransaction(context: Context, tx: MpesaTransaction) {
    val text = shareTextFor(tx)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Share transaction"))
}
