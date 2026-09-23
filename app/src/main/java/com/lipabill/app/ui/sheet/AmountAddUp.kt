package com.lipabill.app.ui.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.lipabill.app.ui.theme.HomeType
import com.lipabill.app.ui.theme.Ink
import com.lipabill.app.ui.theme.Mute
import com.lipabill.app.ui.theme.SoftBlue
import com.lipabill.app.ui.theme.Space
import com.lipabill.app.ui.util.formatKesMoney
import com.lipabill.app.ui.util.formatMoneyInputDisplay
import com.lipabill.app.ui.util.formatMoneyInputLabel
import com.lipabill.app.ui.util.sanitizeAmountInput
import com.lipabill.app.viewmodel.RepeatTransactionViewModel

/**
 * Running-total helper for market-style shopping: add item amounts, then apply
 * the sum to the payment amount field.
 */
data class AmountAddUpState(
    val entry: String = "",
    val lines: List<Double> = emptyList()
) {
    val entryValue: Double?
        get() = RepeatTransactionViewModel.parseAmount(entry)

    /** Lines plus the in-progress entry (if it’s a valid amount). */
    val total: Double
        get() = lines.sum() + (entryValue ?: 0.0)

    val canAddEntry: Boolean
        get() = entryValue != null

    val canUseTotal: Boolean
        get() = total > 0.0

    fun withKey(key: String): AmountAddUpState =
        copy(entry = appendAmountKey(entry, key))

    fun withBackspace(): AmountAddUpState =
        copy(entry = entry.dropLast(1))

    fun withEntryAdded(): AmountAddUpState {
        val value = entryValue ?: return this
        return copy(entry = "", lines = lines + value)
    }

    fun cleared(): AmountAddUpState = AmountAddUpState()

    /** Digits string suitable for [sanitizeAmountInput] / payment amount fields. */
    fun totalAsAmountInput(): String {
        if (total <= 0.0) return ""
        return if (total % 1.0 == 0.0) {
            total.toLong().toString()
        } else {
            "%.2f".format(total).trimEnd('0').trimEnd('.')
                .let { sanitizeAmountInput(it) }
        }
    }

    companion object {
        /**
         * Seed from the amount already typed on the payment pad.
         * A valid amount becomes the first line (user already tapped +), ready for the next item.
         */
        fun seededFrom(rawAmountInput: String): AmountAddUpState {
            val sanitized = sanitizeAmountInput(rawAmountInput)
            if (sanitized.isBlank()) return AmountAddUpState()
            // Keep incomplete typing in the entry field (e.g. "12.").
            if (sanitized.endsWith('.')) {
                return AmountAddUpState(entry = sanitized)
            }
            val value = RepeatTransactionViewModel.parseAmount(sanitized)
            return if (value != null) {
                AmountAddUpState(entry = "", lines = listOf(value))
            } else {
                AmountAddUpState(entry = sanitized)
            }
        }
    }
}

internal fun appendAmountKey(current: String, key: String): String {
    return when (key) {
        "." -> {
            if (current.contains('.')) current
            else if (current.isEmpty()) "0." else current + "."
        }
        else -> sanitizeAmountInput(current + key)
    }
}

@Composable
fun AmountAddUpDialog(
    initialAmountInput: String = "",
    onDismiss: () -> Unit,
    onUseTotal: (amountInput: String) -> Unit
) {
    var state by remember(initialAmountInput) {
        mutableStateOf(AmountAddUpState.seededFrom(initialAmountInput))
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.page)
                .shadow(12.dp, RoundedCornerShape(28.dp), spotColor = Ink.copy(alpha = 0.12f))
                .clip(RoundedCornerShape(28.dp))
                .background(CardWhite)
                .padding(horizontal = Space.page, vertical = Space.section)
        ) {
            Text(
                text = "Add up",
                style = HomeType.section,
                color = Ink
            )
            Text(
                text = "Enter each item, tap +, then use the total to pay.",
                style = HomeType.caption,
                color = Mute
            )
            Spacer(modifier = Modifier.height(Space.block))

            Text(
                text = formatKesMoney(state.total),
                style = SheetHeroAmountStyle,
                color = Ink,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = when {
                    state.lines.isEmpty() && state.entry.isBlank() -> "No items yet"
                    state.lines.isEmpty() -> "Typing…"
                    else -> "${state.lines.size} item${if (state.lines.size == 1) "" else "s"} · entry ${formatMoneyInputLabel(state.entry)}"
                },
                style = HomeType.caption,
                color = Mute,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            if (state.lines.isNotEmpty()) {
                Spacer(modifier = Modifier.height(Space.gap))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 88.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SoftBlue)
                        .verticalScroll(rememberScrollState())
                        .padding(Space.block),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    state.lines.forEachIndexed { index, line ->
                        Text(
                            text = "${index + 1}. ${formatKesMoney(line)}",
                            style = HomeType.caption,
                            color = Ink
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Space.block))
            Text(
                text = formatMoneyInputDisplay(state.entry),
                style = SheetAmountStyle,
                color = if (state.entry.isBlank()) Mute else Ink,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(Space.gap))

            AmountKeypad(
                onKey = { key -> state = state.withKey(key) },
                onBackspace = { state = state.withBackspace() },
                onClear = { state = state.cleared() },
                onAction = { state = state.withEntryAdded() },
                actionLabel = "+",
                actionEnabled = state.canAddEntry
            )
            Spacer(modifier = Modifier.height(Space.gap))
            Button(
                onClick = {
                    val next = if (state.canAddEntry) state.withEntryAdded() else state
                    if (!next.canUseTotal) return@Button
                    onUseTotal(next.totalAsAmountInput())
                },
                enabled = state.canUseTotal,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Accent,
                    contentColor = CardWhite
                )
            ) {
                Text("Use total", style = HomeType.rowTitle)
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Cancel", color = Mute)
            }
        }
    }
}
