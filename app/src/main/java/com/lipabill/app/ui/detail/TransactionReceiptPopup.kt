package com.lipabill.app.ui.detail

import com.lipabill.app.ui.components.WalletMark
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lipabill.app.data.model.MpesaTransaction
import com.lipabill.app.data.model.TransactionType
import com.lipabill.app.engage.SpendCategory
import com.lipabill.app.engage.needsCategoryReview
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
import com.lipabill.app.viewmodel.PayeeEditResult
import kotlinx.coroutines.launch

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
    onRepeat: ((Long) -> Unit)? = null,
    spendCategory: SpendCategory = SpendCategory.OTHER,
    onCountLinked: (suspend (MpesaTransaction) -> Int)? = null,
    onSavePayee: (suspend (String?, SpendCategory) -> PayeeEditResult)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val amount = txAmountLook(tx)
    val fuliza = tx.type == TransactionType.FULIZA
    val outgoing = tx.type.isOutgoing()
    val amountColor = if (amount.outgoingStyle) Expense else Income
    val amountPrefix = amount.prefix
    val canPay = UssdMenuBuilder.canRepeat(tx)
    val typeLabel = tx.type.displayLabel()
    val avatarName = tx.counterpartyName?.takeIf { it.isNotBlank() } ?: tx.source.label
    var editing by remember(tx.id) { mutableStateOf(false) }
    var draftName by remember(tx.id) { mutableStateOf(tx.counterpartyName.orEmpty()) }
    var draftCategory by remember(tx.id) { mutableStateOf(spendCategory) }
    var linkedCount by remember(tx.id) { mutableIntStateOf(1) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(tx.id, editing, spendCategory) {
        draftCategory = spendCategory
        if (editing && onCountLinked != null) {
            linkedCount = onCountLinked(tx).coerceAtLeast(1)
        }
    }

    BackHandler(onBack = {
        if (editing) editing = false else onDismiss()
    })

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
                            fontWeight = FontWeight.Medium,
                            privacyCover = !tx.counterpartyName.isNullOrBlank()
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
                            StatusPill(text = spendCategory.label, filled = false)
                            if (needsCategoryReview(tx.type, tx.counterpartyName)) {
                                StatusPill(text = "Check category", filled = false)
                            }
                        }
                        if (onSavePayee != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            if (!editing) {
                                OutlinedButton(
                                    onClick = {
                                        draftName = tx.counterpartyName.orEmpty()
                                        draftCategory = spendCategory
                                        editing = true
                                    },
                                    shape = RoundedCornerShape(18.dp),
                                    border = BorderStroke(1.dp, Hairline),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.Edit,
                                        contentDescription = null,
                                        tint = Ink,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.size(6.dp))
                                    Text(
                                        if (needsCategoryReview(tx.type, tx.counterpartyName)) {
                                            "Check category"
                                        } else {
                                            "Edit name & category"
                                        },
                                        style = HomeType.label,
                                        color = Ink
                                    )
                                }
                            } else {
                                PayeeEditPanel(
                                    draftName = draftName,
                                    onNameChange = { draftName = it },
                                    draftCategory = draftCategory,
                                    onCategoryChange = { draftCategory = it },
                                    linkedCount = linkedCount,
                                    saving = saving,
                                    onCancel = { editing = false },
                                    onSave = {
                                        if (saving) return@PayeeEditPanel
                                        saving = true
                                        scope.launch {
                                            val result = onSavePayee(
                                                draftName.trim().ifEmpty { null },
                                                draftCategory
                                            )
                                            saving = false
                                            editing = false
                                            val n = result.linkedCount.coerceAtLeast(1)
                                            Toast.makeText(
                                                context,
                                                if (n == 1) {
                                                    "Updated this payment"
                                                } else {
                                                    "Applies to $n similar payments"
                                                },
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                )
                            }
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
                        emphasize = true,
                        coverValue = fuliza
                    )
                }

                TicketPerforation()

                DetailSection(title = "Transaction details") {
                    DetailRow("Date", formatTimestamp(tx.timestampMillis))
                    DetailRow("Type", typeLabel)
                    // Every receipt names its wallet or bank, with the same mark as the payment flow.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (tx.source.isWallet) "Wallet" else "Bank",
                            style = HomeType.caption,
                            color = LabelGrey
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            WalletMark(tx.source, size = 18.dp)
                            Text(text = tx.source.label, color = Ink, fontSize = 12.sp)
                        }
                    }
                    if (!tx.counterpartyPhone.isNullOrBlank()) {
                        DetailRow(tx.source.partyIdLabel, tx.counterpartyPhone, coverValue = true)
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
private fun PayeeEditPanel(
    draftName: String,
    onNameChange: (String) -> Unit,
    draftCategory: SpendCategory,
    onCategoryChange: (SpendCategory) -> Unit,
    linkedCount: Int,
    saving: Boolean,
    onCancel: () -> Unit,
    onSave: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DetailFill)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Name", style = HomeType.label, color = Mute, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(4.dp))
        BasicTextField(
            value = draftName,
            onValueChange = onNameChange,
            singleLine = true,
            textStyle = TextStyle(
                color = Ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            ),
            cursorBrush = SolidColor(Accent),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(CardWhite)
                .border(1.dp, Hairline, RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 10.dp)
                .recordingPrivacyCover(true)
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text("Category", style = HomeType.label, color = Mute, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SpendCategory.editable.forEach { cat ->
                val selected = cat == draftCategory
                Text(
                    text = cat.label,
                    style = HomeType.label.asPillLabel(),
                    color = if (selected) OnAccent else Ink,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .background(if (selected) Accent else CardWhite)
                        .border(
                            width = if (selected) 0.dp else 1.dp,
                            color = if (selected) Color.Transparent else Hairline,
                            shape = RoundedCornerShape(percent = 50)
                        )
                        .clickable { onCategoryChange(cat) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (linkedCount <= 1) {
                "Applies to this payment only."
            } else {
                "Applies to $linkedCount similar payments."
            },
            style = HomeType.caption,
            color = Mute,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                enabled = !saving,
                modifier = Modifier.weight(1f).height(40.dp),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, Hairline)
            ) {
                Text("Cancel", style = HomeType.label, color = Ink)
            }
            Button(
                onClick = onSave,
                enabled = !saving,
                modifier = Modifier.weight(1f).height(40.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Accent,
                    contentColor = OnAccent
                )
            ) {
                Text(if (saving) "Saving…" else "Save", style = HomeType.label)
            }
        }
    }
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
