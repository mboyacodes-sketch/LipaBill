package com.lipabill.app.ui.detail

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lipabill.app.data.model.MpesaTransaction
import com.lipabill.app.ui.theme.Expense
import com.lipabill.app.ui.theme.Income
import com.lipabill.app.ui.theme.Space
import com.lipabill.app.ui.util.displayLabel
import com.lipabill.app.ui.util.formatKes
import com.lipabill.app.ui.util.formatTimestamp
import com.lipabill.app.ui.util.isOutgoing
import com.lipabill.app.ussd.UssdMenuBuilder
import com.lipabill.app.viewmodel.TransactionDetailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailScreen(
    viewModel: TransactionDetailViewModel,
    onBack: () -> Unit,
    onRepeat: (Long) -> Unit
) {
    val tx by viewModel.transaction.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Receipt") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    val current = tx
                    if (current != null) {
                        IconButton(onClick = { shareTransaction(context, current) }) {
                            Icon(Icons.Outlined.Share, contentDescription = "Share")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        val current = tx
        if (current == null) {
            Text(
                text = "Transaction not found",
                modifier = Modifier.padding(padding).padding(Space.page)
            )
            return@Scaffold
        }

        val canPay = UssdMenuBuilder.canRepeat(current)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.page, vertical = Space.gap),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ReceiptCard(tx = current)
            Spacer(modifier = Modifier.height(Space.block))
            if (canPay) {
                Button(
                    onClick = { onRepeat(current.id) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Another transaction")
                }
                Spacer(modifier = Modifier.height(Space.gap))
            }
            if (canRequestMpesaReverse(current)) {
                OutlinedButton(
                    onClick = { openMpesaReverseSms(context, current) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Reverse transaction")
                }
                Spacer(modifier = Modifier.height(Space.tight))
                Text(
                    text = "Opens Messages to 456 with the original M-PESA SMS. Available for about 24 hours after payment.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(modifier = Modifier.height(Space.section))
        }
    }
}

private fun shareTransaction(context: android.content.Context, tx: MpesaTransaction) {
    val text = shareTextFor(tx)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Share transaction"))
}

/**
 * Opens the default SMS app addressed to Safaricom reverse short code 456,
 * with the original confirmation SMS body prefilled (unedited).
 * Only valid within [REVERSE_WINDOW_MS] of the transaction time.
 */
fun canRequestMpesaReverse(
    tx: MpesaTransaction,
    nowMs: Long = System.currentTimeMillis()
): Boolean {
    if (tx.rawBody.isBlank()) return false
    val age = nowMs - tx.timestampMillis
    // Safaricom DIY reverse is within 24h; hide slightly early so the option
    // doesn’t linger at the edge of an expired window.
    return age in 0 until REVERSE_WINDOW_MS
}

private const val REVERSE_WINDOW_MS = 23L * 60 * 60 * 1000 // slightly under 24h

fun openMpesaReverseSms(context: android.content.Context, tx: MpesaTransaction) {
    if (!canRequestMpesaReverse(tx)) {
        Toast.makeText(
            context,
            "Reversal is only available within about 24 hours of the payment",
            Toast.LENGTH_LONG
        ).show()
        return
    }
    val body = tx.rawBody.trim()
    if (body.isEmpty()) {
        Toast.makeText(
            context,
            "No original SMS on this receipt to send to 456",
            Toast.LENGTH_LONG
        ).show()
        return
    }
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("smsto:456")
        putExtra("sms_body", body)
        putExtra("android.intent.extra.TEXT", body)
    }
    runCatching {
        context.startActivity(intent)
    }.onFailure {
        Toast.makeText(context, "Couldn’t open Messages", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Prefer the original SMS up through the date/time (`on d/m/yy at h:mm AM/PM`),
 * dropping balance / cost / anything after. Falls back to a short structured line
 * when the body is missing or has no timestamp.
 */
internal fun shareTextFor(tx: MpesaTransaction): String {
    truncateAtTimestamp(tx.rawBody)?.let { return it }
    // No recognizable timestamp — still prefer raw SMS over structured Ref/To/Date.
    if (tx.rawBody.isNotBlank()) {
        return tx.rawBody.trim()
    }
    return buildString {
        append("Ref: ").append(tx.code).append('\n')
        append("To: ").append(tx.counterpartyName ?: "—").append('\n')
        append("Date: ").append(formatTimestamp(tx.timestampMillis))
    }
}

private val MpesaBodyTimestampEnd = Regex(
    """(?i)on\s+\d{1,2}/\d{1,2}/\d{2,4}\s+at\s+\d{1,2}[:.]\d{2}(?:\s*[AP]M)?\.?"""
)

internal fun truncateAtTimestamp(rawBody: String): String? {
    if (rawBody.isBlank()) return null
    val match = MpesaBodyTimestampEnd.find(rawBody) ?: return null
    return rawBody.substring(0, match.range.last + 1).trimEnd()
}

@Composable
private fun ReceiptCard(tx: MpesaTransaction) {
    val outgoing = tx.type.isOutgoing()
    val amountColor = if (outgoing) Expense else Income
    val tagLabel = tx.type.displayLabel()
    val tagBg = if (outgoing) {
        Expense.copy(alpha = 0.12f)
    } else {
        Income.copy(alpha = 0.12f)
    }
    val tagFg = if (outgoing) Expense else Income

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(28.dp), clip = false)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Text(
            text = tagLabel,
            style = MaterialTheme.typography.labelMedium,
            color = tagFg,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(Space.card)
                .clip(RoundedCornerShape(20.dp))
                .background(tagBg)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.page, vertical = Space.section),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "M",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(Space.block))
            Text(
                text = "M-PESA",
                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 2.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(Space.block))
            DashedRule()
            Spacer(modifier = Modifier.height(Space.block))

            Text(
                text = formatKes(tx.amount),
                style = MaterialTheme.typography.displayLarge,
                color = amountColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(Space.block))
            DashedRule()
            Spacer(modifier = Modifier.height(Space.block))

            ReceiptRow("Ref", tx.code)
            ReceiptRow("To", tx.counterpartyName ?: "—")
            if (!tx.counterpartyPhone.isNullOrBlank()) {
                ReceiptRow("Phone", tx.counterpartyPhone)
            }
            ReceiptRow("Date", formatTimestamp(tx.timestampMillis))
            if (tx.cost != null) ReceiptRow("Fee", formatKes(tx.cost))
            if (tx.balance != null) ReceiptRow("Balance", formatKes(tx.balance))

            Spacer(modifier = Modifier.height(Space.block))
            DashedRule()
            Spacer(modifier = Modifier.height(Space.block))

            Text(
                text = "Confirmed",
                style = MaterialTheme.typography.labelLarge,
                color = Income
            )
            Spacer(modifier = Modifier.height(Space.tight))
            Text(
                text = "LipaBill · from SMS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ReceiptRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Space.gap),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = Space.block)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun DashedRule() {
    val color = MaterialTheme.colorScheme.outline
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
    ) {
        drawLine(
            color = color,
            start = Offset(0f, 0f),
            end = Offset(size.width, 0f),
            strokeWidth = 2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
        )
    }
}
