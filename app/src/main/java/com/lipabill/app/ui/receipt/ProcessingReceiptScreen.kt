package com.lipabill.app.ui.receipt

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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lipabill.app.LipaBillApp
import com.lipabill.app.data.local.entity.RepeatAttemptEntity
import com.lipabill.app.data.model.TransactionType
import com.lipabill.app.ui.theme.Expense
import com.lipabill.app.ui.theme.Income
import com.lipabill.app.ui.theme.Space
import com.lipabill.app.ui.util.displayLabel
import com.lipabill.app.ui.util.formatKes
import com.lipabill.app.ui.util.formatTimestamp
import com.lipabill.app.ui.util.isOutgoing
import com.lipabill.app.ussd.PendingPaymentReceipt
import com.lipabill.app.ussd.RepeatOutcome
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProcessingReceiptScreen(
    auditId: Long,
    onDone: () -> Unit,
    onBack: () -> Unit
) {
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as LipaBillApp
    val snapshot = remember(auditId) { PendingPaymentReceipt.peek(auditId) }
    var attempt by remember { mutableStateOf<RepeatAttemptEntity?>(null) }
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    fun refreshAttempt() {
        scope.launch {
            attempt = app.repeatRepository.getById(auditId)
        }
    }

    DisposableEffect(lifecycleOwner, auditId) {
        refreshAttempt()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshAttempt()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val amount = snapshot?.amount ?: attempt?.amount
    val name = snapshot?.counterpartyName ?: attempt?.counterpartyName
    val phone = snapshot?.counterpartyPhone ?: attempt?.counterpartyPhone
    val type = snapshot?.type ?: TransactionType.UNKNOWN
    val account = snapshot?.accountHint
    val startedAt = snapshot?.startedAtMillis ?: attempt?.createdAtMillis
        ?: System.currentTimeMillis()

    val statusTitle: String
    val statusDetail: String
    when (attempt?.outcome) {
        RepeatOutcome.COMPLETED_TO_PIN -> {
            statusTitle = "PIN submitted"
            statusDetail = "Waiting for M-Pesa confirmation SMS. Sync will update your history."
        }
        RepeatOutcome.ABORTED_MISMATCH,
        RepeatOutcome.ABORTED_ERROR -> {
            statusTitle = "Interrupted"
            statusDetail = "Payment automation stopped. Check the dialer, then try again if needed."
        }
        RepeatOutcome.USER_CANCELLED -> {
            statusTitle = if (attempt?.detail == "pending") "Processing" else "Cancelled"
            statusDetail = if (attempt?.detail == "pending") {
                "Finish any dialer prompts. You’ll enter your PIN on LipaBill’s keypad."
            } else {
                "This payment was cancelled."
            }
        }
        else -> {
            statusTitle = "Processing"
            statusDetail = "Finish any dialer prompts. You’ll enter your PIN on LipaBill’s keypad."
        }
    }
    val isActive = statusTitle == "Processing" || statusTitle == "PIN submitted"

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Payment") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.page, vertical = Space.gap),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val outgoing = type.isOutgoing()
            val tagBg = if (outgoing) Expense.copy(alpha = 0.12f) else Income.copy(alpha = 0.12f)
            val tagFg = if (outgoing) Expense else Income

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(10.dp, RoundedCornerShape(28.dp), clip = false)
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Text(
                    text = type.displayLabel(),
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
                        if (isActive && statusTitle == "Processing") {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 2.5.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Text(
                                text = "M",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
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
                        text = formatKes(amount),
                        style = MaterialTheme.typography.displayLarge,
                        color = Expense,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(Space.block))
                    DashedRule()
                    Spacer(modifier = Modifier.height(Space.block))

                    ReceiptRow("Status", statusTitle)
                    ReceiptRow("To", name ?: "—")
                    if (!phone.isNullOrBlank()) {
                        ReceiptRow(
                            label = when (type) {
                                TransactionType.PAYBILL -> "Paybill"
                                TransactionType.BUY_GOODS -> "Till"
                                TransactionType.POCHI -> "Pochi"
                                else -> "Phone"
                            },
                            value = phone
                        )
                    }
                    if (!account.isNullOrBlank()) {
                        ReceiptRow("Account", account)
                    }
                    ReceiptRow("Started", formatTimestamp(startedAt))
                    ReceiptRow("Attempt", "#$auditId")

                    Spacer(modifier = Modifier.height(Space.block))
                    DashedRule()
                    Spacer(modifier = Modifier.height(Space.block))

                    Text(
                        text = statusTitle,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(Space.tight))
                    Text(
                        text = statusDetail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(Space.block))
            Button(
                onClick = {
                    PendingPaymentReceipt.clear()
                    onDone()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Done")
            }
            TextButton(onClick = {
                PendingPaymentReceipt.clear()
                onBack()
            }) {
                Text("Back to home")
            }
            Spacer(modifier = Modifier.height(Space.section))
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
