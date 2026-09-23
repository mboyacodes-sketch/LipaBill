package com.lipabill.app.ui.sheet

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Wallet
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lipabill.app.ui.components.BalanceAmountRow
import com.lipabill.app.ui.theme.CardWhite
import kotlinx.coroutines.delay
import com.lipabill.app.ui.theme.Expense
import com.lipabill.app.ui.theme.Hairline
import com.lipabill.app.ui.theme.HomeType
import com.lipabill.app.ui.theme.Income
import com.lipabill.app.ui.theme.Ink
import com.lipabill.app.ui.theme.Accent
import com.lipabill.app.ui.theme.LocalAppType
import com.lipabill.app.ui.theme.Mute
import com.lipabill.app.ui.theme.SoftBlue
import com.lipabill.app.ui.theme.Space
import com.lipabill.app.ui.util.formatMoneyInputLabel
import com.lipabill.app.ui.util.hideKeyboardOnOutsideTap
import com.lipabill.app.ui.util.imeAndNavBarsPadding

private val SheetCanvas = Color(0xFFF7F7F8)
private val SoftFill = Color(0xFFF2F3F5)
private val AvatarPalette = listOf(
    Color(0xFFDCE8F5),
    Color(0xFFE8DCF5),
    Color(0xFFDCF5E8),
    Color(0xFFF5E8DC),
    Color(0xFFE8F0DC)
)

val SheetAmountStyle: TextStyle
    @Composable
    @ReadOnlyComposable
    get() = LocalAppType.current.sheetAmount

val SheetHeroAmountStyle: TextStyle
    @Composable
    @ReadOnlyComposable
    get() = LocalAppType.current.sheetHeroAmount

val SheetInputStyle: TextStyle
    @Composable
    @ReadOnlyComposable
    get() = LocalAppType.current.sheetInput

@Composable
fun MoneySheetScaffold(
    balance: Double?,
    alwaysShowBalance: Boolean,
    pendingDeduction: Double? = null,
    selectedName: String?,
    selectedSubtitle: String?,
    amountDisplay: String,
    selectedSelected: Boolean,
    /** Raw amount digits (no KES / commas) — seeds Add up when + is tapped. */
    amountInput: String = "",
    recipientsHeader: String = "Recipients",
    onRecipientsHeaderClick: (() -> Unit)? = null,
    recipientsContent: @Composable () -> Unit,
    ctaLabel: String,
    ctaEnabled: Boolean,
    onCta: () -> Unit,
    onKey: (String) -> Unit,
    onBackspace: () -> Unit,
    extraAboveKeypad: (@Composable () -> Unit)? = null,
    showKeypad: Boolean = true,
    showRecipients: Boolean = true,
    /** When set, amount step shows an optional “Add up” helper that can fill the amount. */
    onApplyAddUpAmount: ((String) -> Unit)? = null,
    /** Clears the typed amount (calculator C). */
    onClearAmount: (() -> Unit)? = null,
    /** Optional in-app text keyboard (details step) — replaces the system IME. */
    inAppTextKeyboard: (@Composable () -> Unit)? = null,
    /** Inline coaching / status above the CTA — never leave empty searches silent. */
    feedbackMessage: String? = null,
    feedbackTone: SheetFeedbackTone = SheetFeedbackTone.Hint,
    modifier: Modifier = Modifier
) {
    val amountValue = pendingDeduction
    val exceedsBalance = balance != null &&
        amountValue != null &&
        amountValue > 0.0 &&
        amountValue > balance
    val amountColor = if (exceedsBalance) Expense else Ink
    val ctaReallyEnabled = ctaEnabled && !exceedsBalance
    var showAddUp by remember { mutableStateOf(false) }
    /** Amount on the payment pad when Add up opened — restored if the user cancels. */
    var addUpSeed by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
            .background(CardWhite)
            .hideKeyboardOnOutsideTap()
            .imeAndNavBarsPadding()
            .padding(horizontal = Space.page)
            .padding(top = Space.gap, bottom = Space.block)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .width(40.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Hairline)
        )
        Spacer(modifier = Modifier.height(Space.gap))

        if (showKeypad) {
            AmountStage(
                balance = balance,
                alwaysShowBalance = alwaysShowBalance,
                selectedName = selectedName,
                selectedSubtitle = selectedSubtitle,
                selectedSelected = selectedSelected,
                amountDisplay = amountDisplay,
                amountColor = amountColor,
                exceedsBalance = exceedsBalance,
                onChangeRecipient = onRecipientsHeaderClick,
                changeLabel = recipientsHeader,
                onOpenAddUp = onApplyAddUpAmount?.let {
                    {
                        addUpSeed = amountInput
                        showAddUp = true
                    }
                },
                modifier = Modifier
                    .weight(1f, fill = true)
                    .fillMaxWidth()
            )
            AmountKeypad(
                onKey = onKey,
                onBackspace = onBackspace,
                onClear = onClearAmount,
                onAction = onApplyAddUpAmount?.let {
                    {
                        addUpSeed = amountInput
                        showAddUp = true
                    }
                },
                actionLabel = "+"
            )
            Spacer(modifier = Modifier.height(Space.block))
        } else {
            val detailsScroll = rememberScrollState()
            // Fields sit at the bottom of the scroll content — when the in-app keyboard
            // appears the viewport shrinks; scroll down so the focused field stays visible.
            LaunchedEffect(inAppTextKeyboard != null, feedbackMessage) {
                if (inAppTextKeyboard != null || !feedbackMessage.isNullOrBlank()) {
                    delay(64)
                    detailsScroll.animateScrollTo(detailsScroll.maxValue)
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f, fill = true)
                    .fillMaxWidth()
                    .verticalScroll(detailsScroll)
            ) {
                Spacer(modifier = Modifier.height(Space.block))
                BalanceCard(
                    balance = balance,
                    alwaysShowBalance = alwaysShowBalance,
                    pendingDeduction = null
                )
                Spacer(modifier = Modifier.height(Space.block))
                SelectedAmountRow(
                    name = selectedName,
                    subtitle = selectedSubtitle,
                    amountDisplay = "",
                    selected = selectedSelected,
                    showAmount = false
                )
                if (showRecipients) {
                    Spacer(modifier = Modifier.height(Space.block))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (onRecipientsHeaderClick != null) {
                                    Modifier.clickable(onClick = onRecipientsHeaderClick)
                                } else Modifier
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(recipientsHeader, style = HomeType.section, color = Ink)
                        Icon(
                            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                            contentDescription = null,
                            tint = Mute,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(Space.block))
                    recipientsContent()
                }
                if (extraAboveKeypad != null) {
                    Spacer(modifier = Modifier.height(Space.block))
                    extraAboveKeypad()
                }
                // Coaching sits under the fields so it stays visible above the in-app keyboard.
                if (!feedbackMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(Space.gap))
                    SheetFeedback(
                        message = feedbackMessage,
                        tone = feedbackTone
                    )
                }
                Spacer(modifier = Modifier.height(Space.section))
            }
            if (inAppTextKeyboard != null) {
                Spacer(modifier = Modifier.height(Space.gap))
                inAppTextKeyboard()
                Spacer(modifier = Modifier.height(Space.gap))
            }
        }

        // Amount step: no text fields — keep status above the CTA.
        if (showKeypad && !feedbackMessage.isNullOrBlank()) {
            SheetFeedback(
                message = feedbackMessage,
                tone = feedbackTone
            )
            Spacer(modifier = Modifier.height(Space.gap))
        }

        Button(
            onClick = onCta,
            enabled = ctaReallyEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(26.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Accent,
                contentColor = CardWhite,
                disabledContainerColor = SoftFill,
                disabledContentColor = Mute
            )
        ) {
            Text(ctaLabel, style = HomeType.rowTitle)
        }
    }

    if (showAddUp && onApplyAddUpAmount != null) {
        AmountAddUpDialog(
            initialAmountInput = addUpSeed,
            onDismiss = {
                // Cancel / back — put back whatever was on the pad when Add up opened.
                onApplyAddUpAmount(addUpSeed)
                showAddUp = false
            },
            onUseTotal = { nextAmount ->
                onApplyAddUpAmount(nextAmount)
                showAddUp = false
            }
        )
    }
}

@Composable
private fun AmountStage(
    balance: Double?,
    alwaysShowBalance: Boolean,
    selectedName: String?,
    selectedSubtitle: String?,
    selectedSelected: Boolean,
    amountDisplay: String,
    amountColor: Color,
    exceedsBalance: Boolean,
    onChangeRecipient: (() -> Unit)?,
    changeLabel: String,
    onOpenAddUp: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CompactRecipientChip(
            name = selectedName,
            subtitle = selectedSubtitle,
            selected = selectedSelected,
            changeLabel = changeLabel,
            onClick = onChangeRecipient
        )
        Spacer(modifier = Modifier.height(Space.section))
        Text(
            text = amountDisplay,
            style = SheetHeroAmountStyle,
            color = amountColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(Space.block))
        when {
            exceedsBalance -> {
                Text(
                    text = "More than available balance",
                    style = HomeType.caption,
                    color = Expense
                )
            }
            balance != null -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Available ", style = HomeType.caption, color = Mute)
                    BalanceAmountRow(
                        balance = balance,
                        alwaysShow = alwaysShowBalance,
                        amountStyle = HomeType.caption,
                        pendingDeduction = null,
                        horizontalArrangement = Arrangement.Start
                    )
                }
            }
            else -> {
                Text(
                    text = "Enter amount",
                    style = HomeType.caption,
                    color = Mute
                )
            }
        }
    }
}

@Composable
private fun CompactRecipientChip(
    name: String?,
    subtitle: String?,
    selected: Boolean,
    changeLabel: String,
    onClick: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(SoftFill)
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
            )
            .padding(horizontal = Space.block, vertical = Space.gap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.block)
    ) {
        if (name != null) {
            AvatarBadge(
                label = name,
                selected = selected,
                modifier = Modifier.size(36.dp)
            )
            Column {
                Text(
                    text = name,
                    style = HomeType.rowTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = HomeType.caption,
                        color = Mute,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        } else {
            Text(changeLabel, style = HomeType.body, color = Mute)
        }
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = changeLabel,
            tint = Mute,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun BalanceCard(
    balance: Double?,
    alwaysShowBalance: Boolean,
    pendingDeduction: Double? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(16.dp), spotColor = Color.Black.copy(alpha = 0.06f))
            .clip(RoundedCornerShape(16.dp))
            .background(SoftFill)
            .padding(horizontal = Space.card, vertical = Space.cardH),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (pendingDeduction != null && pendingDeduction > 0) {
                    "Remaining balance"
                } else {
                    "Available balance"
                },
                style = HomeType.label,
                color = Mute
            )
            Spacer(modifier = Modifier.height(Space.tight))
            BalanceAmountRow(
                balance = balance,
                alwaysShow = alwaysShowBalance,
                pendingDeduction = pendingDeduction,
                amountStyle = HomeType.greeting
            )
        }
        Box(
            modifier = Modifier
                .size(width = 52.dp, height = 34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(CardWhite)
                .border(1.dp, Hairline, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.Wallet,
                contentDescription = null,
                tint = Ink,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SelectedAmountRow(
    name: String?,
    subtitle: String?,
    amountDisplay: String,
    selected: Boolean,
    showAmount: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (name != null) {
            AvatarBadge(
                label = name,
                selected = selected,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.width(Space.block))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = HomeType.rowTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = HomeType.caption,
                        color = Mute,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        } else {
            Text(
                text = "Select a recipient",
                style = HomeType.body,
                color = Mute,
                modifier = Modifier.weight(1f)
            )
        }
        if (showAmount) {
            Text(
                text = amountDisplay,
                style = SheetAmountStyle,
                color = Ink
            )
        }
    }
}

@Composable
private fun AvatarBadge(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    colorSeed: Int = label.hashCode()
) {
    val bg = AvatarPalette[abs(colorSeed) % AvatarPalette.size]
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(CircleShape)
                .background(bg),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                style = HomeType.rowTitle,
                color = Ink
            )
        }
        if (selected) {
            Icon(
                Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = Income,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(16.dp)
                    .background(CardWhite, CircleShape)
            )
        }
    }
}

private fun abs(n: Int): Int = if (n == Int.MIN_VALUE) 0 else kotlin.math.abs(n)

@Composable
fun HorizontalRecipient(
    name: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(88.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AvatarBadge(
            label = name,
            selected = selected,
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(Space.gap))
        Text(
            text = name,
            style = HomeType.caption,
            color = Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subtitle,
            style = HomeType.caption,
            color = Mute,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun HorizontalRecipientRow(
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Space.block)
    ) {
        content()
    }
}

private enum class KeypadCellKind { DIGIT, BACKSPACE, CLEAR, ACTION, EMPTY }

private data class KeypadCell(
    val label: String,
    val kind: KeypadCellKind,
    val weight: Float = 1f
)

@Composable
fun AmountKeypad(
    onKey: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: (() -> Unit)? = null,
    /** Optional right-column action (e.g. “+” to add an item or open Add up). */
    onAction: (() -> Unit)? = null,
    actionLabel: String = "+",
    actionEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    // Calculator-style grid: digits left, ops on the right.
    val rows = listOf(
        listOf(
            KeypadCell("7", KeypadCellKind.DIGIT),
            KeypadCell("8", KeypadCellKind.DIGIT),
            KeypadCell("9", KeypadCellKind.DIGIT),
            KeypadCell("C", KeypadCellKind.CLEAR)
        ),
        listOf(
            KeypadCell("4", KeypadCellKind.DIGIT),
            KeypadCell("5", KeypadCellKind.DIGIT),
            KeypadCell("6", KeypadCellKind.DIGIT),
            KeypadCell("⌫", KeypadCellKind.BACKSPACE)
        ),
        listOf(
            KeypadCell("1", KeypadCellKind.DIGIT),
            KeypadCell("2", KeypadCellKind.DIGIT),
            KeypadCell("3", KeypadCellKind.DIGIT),
            KeypadCell(actionLabel, KeypadCellKind.ACTION)
        ),
        listOf(
            KeypadCell(".", KeypadCellKind.DIGIT),
            KeypadCell("0", KeypadCellKind.DIGIT, weight = 2f),
            KeypadCell("", KeypadCellKind.EMPTY)
        )
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SheetCanvas)
            .padding(Space.block),
        verticalArrangement = Arrangement.spacedBy(Space.gap)
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.gap)
            ) {
                row.forEach { cell ->
                    when (cell.kind) {
                        KeypadCellKind.EMPTY -> Spacer(modifier = Modifier.weight(cell.weight))
                        KeypadCellKind.CLEAR -> {
                            if (onClear != null) {
                                KeypadKey(
                                    label = cell.label,
                                    accent = true,
                                    modifier = Modifier.weight(cell.weight),
                                    onClick = onClear
                                )
                            } else {
                                KeypadKey(
                                    label = "⌫",
                                    modifier = Modifier.weight(cell.weight),
                                    onClick = onBackspace
                                )
                            }
                        }
                        KeypadCellKind.BACKSPACE -> KeypadKey(
                            label = "⌫",
                            modifier = Modifier.weight(cell.weight),
                            onClick = onBackspace
                        )
                        KeypadCellKind.ACTION -> {
                            if (onAction != null) {
                                KeypadKey(
                                    label = cell.label,
                                    accent = true,
                                    enabled = actionEnabled,
                                    modifier = Modifier.weight(cell.weight),
                                    onClick = onAction
                                )
                            } else {
                                Spacer(modifier = Modifier.weight(cell.weight))
                            }
                        }
                        KeypadCellKind.DIGIT -> KeypadKey(
                            label = cell.label,
                            modifier = Modifier.weight(cell.weight),
                            onClick = { onKey(cell.label) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KeypadKey(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    enabled: Boolean = true
) {
    val bg = when {
        !enabled -> SoftFill
        accent -> SoftBlue
        else -> CardWhite
    }
    val fg = when {
        !enabled -> Mute
        accent -> Accent
        else -> Ink
    }
    Box(
        modifier = modifier
            .height(52.dp)
            .shadow(1.dp, RoundedCornerShape(14.dp), spotColor = Color.Black.copy(alpha = 0.05f))
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (label == "⌫") {
            Icon(
                Icons.AutoMirrored.Outlined.Backspace,
                contentDescription = "Delete",
                tint = fg,
                modifier = Modifier.size(22.dp)
            )
        } else {
            Text(label, style = HomeType.greeting, color = fg)
        }
    }
}

fun formatSheetAmount(raw: String): String = formatMoneyInputLabel(raw)

@Composable
fun MoneyConfirmDialog(
    amountLabel: String,
    detail: String,
    confirmLabel: String = "Confirm",
    note: String = "You’ll enter your M-Pesa PIN on LipaBill’s secure keypad.",
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.page)
                .shadow(12.dp, RoundedCornerShape(28.dp), spotColor = Color.Black.copy(alpha = 0.12f))
                .clip(RoundedCornerShape(28.dp))
                .background(CardWhite)
                .padding(horizontal = Space.page, vertical = Space.section),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = amountLabel,
                style = SheetHeroAmountStyle,
                color = Ink,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(Space.block))
            Text(
                text = detail,
                style = HomeType.rowTitle,
                color = Ink,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            if (note.isNotBlank()) {
                Spacer(modifier = Modifier.height(Space.gap))
                Text(
                    text = note,
                    style = HomeType.caption,
                    color = Mute,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(modifier = Modifier.height(Space.section))
            Button(
                onClick = onConfirm,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Accent,
                    contentColor = CardWhite
                )
            ) {
                Text(confirmLabel, style = HomeType.rowTitle)
            }
            Spacer(modifier = Modifier.height(Space.tight))
            TextButton(onClick = onDismiss) {
                Text("Cancel", style = HomeType.label, color = Mute)
            }
        }
    }
}
