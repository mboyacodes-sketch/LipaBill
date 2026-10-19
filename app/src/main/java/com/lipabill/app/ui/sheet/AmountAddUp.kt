package com.lipabill.app.ui.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lipabill.app.ui.theme.Accent
import com.lipabill.app.ui.theme.CardWhite
import com.lipabill.app.ui.theme.OnAccent
import com.lipabill.app.ui.theme.HomeType
import com.lipabill.app.ui.theme.Ink
import com.lipabill.app.ui.theme.Mute
import com.lipabill.app.ui.theme.SoftBlue
import com.lipabill.app.ui.util.appendAmountKey
import com.lipabill.app.ui.util.formatKesMoney
import com.lipabill.app.ui.util.formatMoneyInputDisplay
import com.lipabill.app.ui.util.sanitizeAmountInput
import com.lipabill.app.viewmodel.RepeatTransactionViewModel

/**
 * Running total for a payment: add, subtract, multiply, or divide, then use the result.
 */
enum class AmountOp(val symbol: String) {
    ADD("+"),
    SUB("−"),
    MUL("×"),
    DIV("÷")
}

data class AmountAddUpState(
    val entry: String = "",
    val acc: Double? = null,
    val pending: AmountOp? = null,
    val tape: List<String> = emptyList()
) {
    val entryValue: Double?
        get() = RepeatTransactionViewModel.parseAmount(entry)

    /** Committed total, with the number being typed applied by [pending] (or added). */
    val total: Double
        get() {
            val right = entryValue
            val left = acc
            return when {
                left == null -> right ?: 0.0
                right == null -> left
                pending == null || pending == AmountOp.ADD -> roundMoney(left + right)
                else -> apply(left, pending, right) ?: left
            }
        }

    val canAddEntry: Boolean
        get() = entryValue != null

    val canUseTotal: Boolean
        get() = total > 0.0

    fun withKey(key: String): AmountAddUpState =
        copy(entry = appendAmountKey(entry, key))

    fun withBackspace(): AmountAddUpState =
        if (entry.isNotEmpty()) copy(entry = entry.dropLast(1))
        else copy(pending = null)

    /** Commit the typed number with + and clear the entry. */
    fun withEntryAdded(): AmountAddUpState = commit(pending ?: AmountOp.ADD)

    /**
     * Apply the typed number with the operator already chosen, then arm [op]
     * for the next number. The first number is stored on its own.
     */
    fun withOperator(op: AmountOp): AmountAddUpState {
        val committed = if (entryValue != null) commit(pending ?: AmountOp.ADD) else this
        if (committed.acc == null) return committed
        return committed.copy(pending = op)
    }

    fun cleared(): AmountAddUpState = AmountAddUpState()

    /** Digits string suitable for [sanitizeAmountInput] / payment amount fields. */
    fun totalAsAmountInput(): String = amountInputString(total)

    private fun commit(op: AmountOp): AmountAddUpState {
        val right = entryValue ?: return this
        val left = acc
        val next = if (left == null) right else apply(left, op, right) ?: return this
        val line = if (left == null) {
            moneyText(right)
        } else {
            "${op.symbol} ${moneyText(right)}"
        }
        return copy(acc = next, entry = "", pending = null, tape = tape + line)
    }

    companion object {
        /**
         * Seed from the amount already typed on the payment pad.
         * [operator] is armed so the next number is subtracted, multiplied, or divided.
         */
        fun seededFrom(rawAmountInput: String, operator: AmountOp? = null): AmountAddUpState {
            val sanitized = sanitizeAmountInput(rawAmountInput)
            if (sanitized.isBlank()) return AmountAddUpState(pending = operator)
            if (sanitized.endsWith('.')) {
                return AmountAddUpState(entry = sanitized, pending = operator)
            }
            val value = RepeatTransactionViewModel.parseAmount(sanitized)
            return if (value != null) {
                AmountAddUpState(
                    acc = value,
                    pending = operator,
                    tape = listOf(moneyText(value))
                )
            } else {
                AmountAddUpState(entry = sanitized, pending = operator)
            }
        }
    }
}

private fun apply(left: Double, op: AmountOp, right: Double): Double? {
    val raw = when (op) {
        AmountOp.ADD -> left + right
        AmountOp.SUB -> left - right
        AmountOp.MUL -> left * right
        AmountOp.DIV -> if (right == 0.0) null else left / right
    } ?: return null
    return roundMoney(raw)
}

private fun roundMoney(value: Double): Double =
    kotlin.math.round(value * 100.0) / 100.0

private fun moneyText(value: Double): String {
    val rounded = roundMoney(value)
    return if (kotlin.math.abs(rounded % 1.0) < 0.001) {
        rounded.toLong().toString()
    } else {
        "%.2f".format(java.util.Locale.US, rounded).trimEnd('0').trimEnd('.')
    }
}

private fun amountInputString(total: Double): String {
    if (total <= 0.0) return ""
    return sanitizeAmountInput(moneyText(total))
}

@Composable
fun AmountAddUpDialog(
    initialAmountInput: String = "",
    initialOperator: AmountOp? = null,
    onDismiss: () -> Unit,
    onUseTotal: (amountInput: String) -> Unit
) {
    var state by remember(initialAmountInput, initialOperator) {
        mutableStateOf(AmountAddUpState.seededFrom(initialAmountInput, initialOperator))
    }
    val linesScroll = rememberScrollState()
    LaunchedEffect(state.tape.size, linesScroll.maxValue) {
        if (state.tape.isNotEmpty()) {
            linesScroll.animateScrollTo(linesScroll.maxValue)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .shadow(12.dp, RoundedCornerShape(28.dp), spotColor = Ink.copy(alpha = 0.12f))
                .clip(RoundedCornerShape(28.dp))
                .background(CardWhite)
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Text(
                text = "Calculate",
                style = HomeType.section,
                color = Ink
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Use +, −, ×, or ÷, then pay the result.",
                style = HomeType.caption,
                color = Mute
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = formatKesMoney(state.total),
                style = SheetHeroAmountStyle,
                color = Ink,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            val acc = state.acc
            val pending = state.pending
            Text(
                text = when {
                    acc == null && state.entry.isBlank() -> "No amount yet"
                    pending != null && acc != null && state.entry.isNotBlank() ->
                        "${moneyText(acc)} ${pending.symbol} ${formatMoneyInputDisplay(state.entry)}"
                    pending != null && acc != null ->
                        "${moneyText(acc)} ${pending.symbol}"
                    state.entry.isNotBlank() && acc == null -> "Typing…"
                    else -> "Running total"
                },
                style = HomeType.caption,
                color = Mute,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            if (state.tape.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 88.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(SoftBlue)
                        .verticalScroll(linesScroll)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    state.tape.forEach { line ->
                        Text(
                            text = line,
                            style = HomeType.caption,
                            color = Ink
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = formatMoneyInputDisplay(state.entry),
                style = SheetAmountStyle,
                color = if (state.entry.isBlank()) Mute else Ink,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 36.dp),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))
            AmountKeypad(
                onKey = { key -> state = state.withKey(key) },
                onBackspace = { state = state.withBackspace() },
                onClear = { state = state.cleared() },
                onOperator = { op -> state = state.withOperator(op) },
                activeOperator = state.pending
            )

            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = {
                    val next = if (state.canAddEntry) state.withEntryAdded() else state
                    if (!next.canUseTotal) return@Button
                    onUseTotal(next.totalAsAmountInput())
                },
                enabled = state.canUseTotal,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Accent,
                    contentColor = OnAccent
                )
            ) {
                Text("Use total", style = HomeType.rowTitle)
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 2.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("Cancel", style = HomeType.label, color = Mute)
            }
        }
    }
}
