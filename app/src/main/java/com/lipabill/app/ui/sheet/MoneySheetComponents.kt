package com.lipabill.app.ui.sheet

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lipabill.app.ui.adapt.LocalWindowForm
import com.lipabill.app.ui.adapt.WindowHeight
import com.lipabill.app.ui.adapt.WindowWidth
import com.lipabill.app.ui.adapt.moneySheetAmountKeyHeight
import com.lipabill.app.ui.components.BalanceAmountRow
import com.lipabill.app.ui.privacy.recordingPrivacyCover
import com.lipabill.app.ui.util.SecureKeyboardWindow
import com.lipabill.app.ui.util.rememberKeyPressHaptic
import com.lipabill.app.ui.theme.CardWhite
import com.lipabill.app.ui.theme.OnAccent
import com.lipabill.app.ui.theme.OnPastel
import com.lipabill.app.ui.theme.ContentMaxWidth
import kotlinx.coroutines.delay
import com.lipabill.app.ui.theme.Expense
import com.lipabill.app.ui.theme.Hairline
import com.lipabill.app.ui.theme.HomeType
import com.lipabill.app.ui.theme.Income
import com.lipabill.app.ui.theme.Ink
import com.lipabill.app.ui.theme.Accent
import com.lipabill.app.ui.theme.AvatarPastels
import com.lipabill.app.ui.theme.Canvas
import com.lipabill.app.ui.theme.LocalAppType
import com.lipabill.app.ui.theme.Mute
import com.lipabill.app.ui.theme.SoftBlue
import com.lipabill.app.ui.theme.Space
import com.lipabill.app.ui.util.hideKeyboardOnOutsideTap
import com.lipabill.app.ui.util.moneySheetWindowPadding

private val SheetCanvas: androidx.compose.ui.graphics.Color
    @androidx.compose.runtime.Composable
    @androidx.compose.runtime.ReadOnlyComposable
    get() = Canvas
private val SoftFill: androidx.compose.ui.graphics.Color
    @androidx.compose.runtime.Composable
    @androidx.compose.runtime.ReadOnlyComposable
    get() = SoftBlue
private val AvatarPalette = AvatarPastels

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
    pendingDeduction: Double? = null,
    balanceCaption: String = "Available balance",
    balancePrefix: String = "Available",
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
    secondaryCtaLabel: String? = null,
    secondaryCtaEnabled: Boolean = true,
    onSecondaryCta: (() -> Unit)? = null,
    onKey: (String) -> Unit,
    onBackspace: () -> Unit,
    extraAboveKeypad: (@Composable () -> Unit)? = null,
    showKeypad: Boolean = true,
    showRecipients: Boolean = true,
    /** When false, [recipientsContent] still renders without the “Payment ›” row. */
    showRecipientsHeader: Boolean = true,
    /**
     * On the details path ([showKeypad] false), show the amount pad under the scroll
     * so Send/Pay can keep recipient + amount on one sheet.
     */
    showAmountKeypad: Boolean = false,
    /** When set, amount step shows an optional “Add up” helper that can fill the amount. */
    onApplyAddUpAmount: ((String) -> Unit)? = null,
    /** Clears the typed amount (calculator C). */
    onClearAmount: (() -> Unit)? = null,
    /** Optional in-app text keyboard (details step) — replaces the system IME. */
    inAppTextKeyboard: (@Composable () -> Unit)? = null,
    /**
     * When the in-app text keyboard opens, scroll details to the end so bottom fields stay
     * visible. Send keeps this false — the search field sits at the top.
     */
    scrollDetailsToEndOnKeyboard: Boolean = true,
    /** Inline coaching / status above the CTA — never leave empty searches silent. */
    feedbackMessage: String? = null,
    feedbackTone: SheetFeedbackTone = SheetFeedbackTone.Hint,
    /** An amount above the M-PESA balance can still be paid. Fuliza covers the rest. */
    fulizaCoversShortfall: Boolean = false,
    /** Replaces the default amount stage above the keypad (Send uses its own layout). */
    amountStage: (@Composable (Modifier) -> Unit)? = null,
    /** Small note under the primary button, e.g. where the PIN is entered. */
    footnote: String? = null,
    /** Replaces the balance card at the top of the details step. */
    detailsHeader: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val amountValue = pendingDeduction
    val exceedsBalance = amountExceedsBalance(balance, amountValue)
    val balanceBlocksPay = exceedsBalance && !fulizaCoversShortfall
    val canFund = fulizaCoversShortfall || (balance != null && balance > 0.0)
    val amountColor = if (balanceBlocksPay) Expense else Ink
    val ctaReallyEnabled = ctaEnabled && canFund && !balanceBlocksPay
    var showAddUp by remember { mutableStateOf(false) }
    /** Amount on the payment pad when Calculate opened — restored if the user cancels. */
    var addUpSeed by remember { mutableStateOf("") }
    var addUpOp by remember { mutableStateOf<AmountOp?>(null) }
    val window = LocalWindowForm.current
    // Content stays inset; amount / text keyboards always span the full phone width.
    val sidePad = when (window.width) {
        WindowWidth.Compact -> Space.page + Space.section
        WindowWidth.Medium -> 28.dp
        WindowWidth.Expanded -> 32.dp
    }
    val shortSheet = window.height == WindowHeight.Compact
    val sheetTopInset = when {
        shortSheet -> 10.dp
        else -> 16.dp
    }
    val topPad = when {
        shortSheet -> Space.section
        showKeypad -> Space.section + Space.block
        else -> Space.section
    }
    val contentPad = Modifier.padding(horizontal = sidePad)

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val padCeiling = maxHeight * if (shortSheet) 0.40f else 0.45f
    Column(
        modifier = modifier
            .padding(top = sheetTopInset)
            .fillMaxHeight()
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(CardWhite)
            .hideKeyboardOnOutsideTap()
            .moneySheetWindowPadding()
            .padding(top = topPad, bottom = Space.block)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .width(40.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Hairline)
        )
        Spacer(
            modifier = Modifier.height(
                if (showKeypad) Space.section + Space.block else Space.gap
            )
        )

        if (showKeypad && amountStage != null) {
            amountStage(
                Modifier
                    .weight(1f, fill = true)
                    .fillMaxWidth()
                    .then(contentPad)
            )
        }
        if (showKeypad && amountStage == null) {
            AmountStage(
                balance = balance,
                balancePrefix = balancePrefix,
                selectedName = selectedName,
                selectedSubtitle = selectedSubtitle,
                selectedSelected = selectedSelected,
                amountDisplay = amountDisplay,
                amountColor = amountColor,
                exceedsBalance = exceedsBalance,
                fulizaCoversShortfall = fulizaCoversShortfall,
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
                    .then(contentPad)
            )
        }
        if (showKeypad) {
            AmountKeypad(
                onKey = onKey,
                onBackspace = onBackspace,
                onClear = onClearAmount,
                onOperator = onApplyAddUpAmount?.let {
                    { op ->
                        addUpSeed = amountInput
                        addUpOp = op
                        showAddUp = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = padCeiling)
            )
            Spacer(modifier = Modifier.height(if (shortSheet) Space.gap else Space.block))
        } else {
            val detailsScroll = rememberScrollState()
            // Fields sit at the bottom of the scroll content — when the in-app keyboard
            // appears the viewport shrinks; scroll down so the focused field stays visible.
            LaunchedEffect(
                inAppTextKeyboard != null,
                feedbackMessage,
                scrollDetailsToEndOnKeyboard
            ) {
                if (!scrollDetailsToEndOnKeyboard) return@LaunchedEffect
                if (inAppTextKeyboard != null || !feedbackMessage.isNullOrBlank()) {
                    delay(64)
                    detailsScroll.animateScrollTo(detailsScroll.maxValue)
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f, fill = true)
                    .fillMaxWidth()
                    .then(contentPad)
                    .verticalScroll(detailsScroll)
            ) {
                Spacer(modifier = Modifier.height(Space.block))
                if (detailsHeader != null) {
                    detailsHeader()
                } else {
                    BalanceCard(
                        balance = balance,
                        pendingDeduction = null,
                        caption = balanceCaption
                    )
                }
                if (showRecipients) {
                    if (showRecipientsHeader) {
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
                    } else {
                        Spacer(modifier = Modifier.height(Space.section))
                    }
                    recipientsContent()
                }
                if (extraAboveKeypad != null) {
                    Spacer(modifier = Modifier.height(Space.block))
                    extraAboveKeypad()
                }
                // Coaching under fields when the text keyboard is up; amount pad keeps it above CTA.
                if (!showAmountKeypad && !feedbackMessage.isNullOrBlank()) {
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = padCeiling)
                ) {
                    inAppTextKeyboard()
                }
                Spacer(modifier = Modifier.height(Space.gap))
            } else if (showAmountKeypad) {
                Spacer(modifier = Modifier.height(Space.gap))
                AmountKeypad(
                    onKey = onKey,
                    onBackspace = onBackspace,
                    onClear = onClearAmount,
                    onOperator = onApplyAddUpAmount?.let {
                        { op ->
                            addUpSeed = amountInput
                            addUpOp = op
                            showAddUp = true
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = padCeiling)
                )
                Spacer(modifier = Modifier.height(Space.gap))
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(contentPad)
        ) {
            // Amount pad (standalone step or merged sheet): keep status above the CTA.
            if ((showKeypad || showAmountKeypad) && !feedbackMessage.isNullOrBlank()) {
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
                    contentColor = OnAccent,
                    disabledContainerColor = SoftFill,
                    disabledContentColor = Mute
                )
            ) {
                Text(ctaLabel, style = HomeType.rowTitle)
            }
            if (footnote != null) {
                Spacer(modifier = Modifier.height(Space.gap))
                Text(
                    text = footnote,
                    style = HomeType.caption,
                    color = Mute,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (secondaryCtaLabel != null && onSecondaryCta != null) {
                TextButton(
                    onClick = onSecondaryCta,
                    enabled = secondaryCtaEnabled,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(secondaryCtaLabel, style = HomeType.rowTitle)
                }
            }
        }
    }
    }
    }

    if (showAddUp && onApplyAddUpAmount != null) {
        AmountAddUpDialog(
            initialAmountInput = addUpSeed,
            initialOperator = addUpOp,
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
    balancePrefix: String,
    selectedName: String?,
    selectedSubtitle: String?,
    selectedSelected: Boolean,
    amountDisplay: String,
    amountColor: Color,
    exceedsBalance: Boolean,
    fulizaCoversShortfall: Boolean = false,
    onChangeRecipient: (() -> Unit)?,
    changeLabel: String,
    onOpenAddUp: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = Space.section),
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
        Spacer(modifier = Modifier.height(Space.section + Space.block))
        Text(
            text = amountDisplay,
            style = SheetHeroAmountStyle,
            color = amountColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(Space.section))
        when {
            exceedsBalance -> {
                Text(
                    text = if (fulizaCoversShortfall) {
                        "Fuliza covers the rest of this payment"
                    } else {
                        "More than available balance"
                    },
                    style = HomeType.caption,
                    color = if (fulizaCoversShortfall) Mute else Expense,
                    textAlign = TextAlign.Center
                )
            }
            balance != null -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text("$balancePrefix ", style = HomeType.caption, color = Mute)
                    BalanceAmountRow(
                        balance = balance,
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
                    color = Mute,
                    textAlign = TextAlign.Center
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
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.recordingPrivacyCover()
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = HomeType.caption,
                        color = Mute,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.recordingPrivacyCover()
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
    pendingDeduction: Double? = null,
    caption: String = "Available balance"
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
                    caption
                },
                style = HomeType.label,
                color = Mute
            )
            Spacer(modifier = Modifier.height(Space.tight))
            BalanceAmountRow(
                balance = balance,
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
                color = OnPastel,
                modifier = Modifier.recordingPrivacyCover()
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

private enum class KeypadCellKind { DIGIT, BACKSPACE, CLEAR, ACTION, EMPTY, OPERATOR }

private data class KeypadCell(
    val label: String,
    val kind: KeypadCellKind,
    val weight: Float = 1f,
    val op: AmountOp? = null
)

private fun classicRows(actionLabel: String): List<List<KeypadCell>> = listOf(
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

private fun operatorRows(): List<List<KeypadCell>> {
    fun op(value: AmountOp) = KeypadCell(value.symbol, KeypadCellKind.OPERATOR, op = value)
    return listOf(
        listOf(AmountOp.DIV, AmountOp.MUL, AmountOp.SUB, AmountOp.ADD).map { op(it) },
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
            KeypadCell(".", KeypadCellKind.DIGIT)
        ),
        listOf(
            KeypadCell("0", KeypadCellKind.DIGIT, weight = 3f),
            KeypadCell("", KeypadCellKind.EMPTY)
        )
    )
}

@Composable
fun AmountKeypad(
    onKey: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: (() -> Unit)? = null,
    /** Optional right-column action (e.g. “+” to add an item or open Add up). */
    onAction: (() -> Unit)? = null,
    actionLabel: String = "+",
    actionEnabled: Boolean = true,
    /** When set, a ÷ × − + row is shown and [onAction] is not used. */
    onOperator: ((AmountOp) -> Unit)? = null,
    activeOperator: AmountOp? = null,
    modifier: Modifier = Modifier
) {
    SecureKeyboardWindow()
    val rows = if (onOperator != null) operatorRows() else classicRows(actionLabel)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val rowCount = rows.size
        val chrome = Space.block * 2 + Space.gap * (rowCount - 1).coerceAtLeast(0)
        val keyHeight = if (maxHeight.value.isFinite() && maxHeight > chrome) {
            ((maxHeight - chrome) / rowCount).coerceIn(36.dp, moneySheetAmountKeyHeight())
        } else {
            moneySheetAmountKeyHeight()
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SheetCanvas)
                .padding(horizontal = Space.gap, vertical = Space.block),
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
                                        keyHeight = keyHeight,
                                        modifier = Modifier.weight(cell.weight),
                                        onClick = onClear
                                    )
                                } else {
                                    KeypadKey(
                                        label = "⌫",
                                        keyHeight = keyHeight,
                                        modifier = Modifier.weight(cell.weight),
                                        onClick = onBackspace
                                    )
                                }
                            }
                            KeypadCellKind.BACKSPACE -> KeypadKey(
                                label = "⌫",
                                keyHeight = keyHeight,
                                modifier = Modifier.weight(cell.weight),
                                onClick = onBackspace
                            )
                            KeypadCellKind.ACTION -> {
                                if (onAction != null) {
                                    KeypadKey(
                                        label = cell.label,
                                        accent = true,
                                        enabled = actionEnabled,
                                        keyHeight = keyHeight,
                                        modifier = Modifier.weight(cell.weight),
                                        onClick = onAction
                                    )
                                } else {
                                    Spacer(modifier = Modifier.weight(cell.weight))
                                }
                            }
                            KeypadCellKind.OPERATOR -> {
                                val op = cell.op
                                if (onOperator != null && op != null) {
                                    KeypadKey(
                                        label = cell.label,
                                        accent = op == activeOperator,
                                        keyHeight = keyHeight,
                                        modifier = Modifier.weight(cell.weight),
                                        onClick = { onOperator(op) }
                                    )
                                } else {
                                    Spacer(modifier = Modifier.weight(cell.weight))
                                }
                            }
                            KeypadCellKind.DIGIT -> KeypadKey(
                                label = cell.label,
                                keyHeight = keyHeight,
                                modifier = Modifier.weight(cell.weight),
                                onClick = { onKey(cell.label) }
                            )
                        }
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
    enabled: Boolean = true,
    keyHeight: Dp = 52.dp
) {
    val haptic = rememberKeyPressHaptic()
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
    val iconSize = if (keyHeight < 44.dp) 18.dp else 22.dp
    Box(
        modifier = modifier
            .height(keyHeight)
            .shadow(1.dp, RoundedCornerShape(14.dp), spotColor = Color.Black.copy(alpha = 0.05f))
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .then(
                if (enabled) {
                    Modifier.clickable {
                        haptic()
                        onClick()
                    }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (label == "⌫") {
            Icon(
                Icons.AutoMirrored.Outlined.Backspace,
                contentDescription = "Delete",
                tint = fg,
                modifier = Modifier.size(iconSize)
            )
        } else {
            Text(label, style = HomeType.greeting, color = fg)
        }
    }
}

@Composable
fun MoneyConfirmDialog(
    amountLabel: String,
    detail: String,
    confirmLabel: String = "Confirm",
    note: String = "You’ll enter your PIN on LipaBill’s secure keypad.",
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
                .padding(horizontal = 28.dp, vertical = 24.dp)
                .widthIn(max = ContentMaxWidth)
                .shadow(12.dp, RoundedCornerShape(28.dp), spotColor = Color.Black.copy(alpha = 0.12f))
                .clip(RoundedCornerShape(28.dp))
                .background(CardWhite)
                .padding(horizontal = Space.page + Space.block, vertical = Space.section + Space.block),
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
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.recordingPrivacyCover(detail.isNotBlank())
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
                    contentColor = OnAccent
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
