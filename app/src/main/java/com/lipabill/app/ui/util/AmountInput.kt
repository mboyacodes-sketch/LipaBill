package com.lipabill.app.ui.util

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import java.text.NumberFormat
import java.util.Locale

/**
 * Digits plus at most one decimal point with up to 2 fraction digits.
 * Strips grouping commas and normalizes leading zeros (`05` → `5`, `0.` kept).
 */
fun sanitizeAmountInput(value: String): String {
    val filtered = value.filter { it.isDigit() || it == '.' }
    if (filtered.isEmpty()) return ""

    val hasDot = filtered.contains('.')
    val parts = filtered.split('.', limit = 2)
    var intPart = parts[0].filter { it.isDigit() }.trimStart('0')
    if (intPart.isEmpty() && (hasDot || parts[0].any { it.isDigit() })) {
        intPart = "0"
    }
    if (intPart.isEmpty() && !hasDot) return ""

    val frac = parts.getOrNull(1)?.filter { it.isDigit() }?.take(2).orEmpty()
    return if (hasDot) "$intPart.$frac" else intPart
}

/**
 * Live amount entry display: thousand separators, preserves a trailing `.` and
 * partial cents while typing. Blank → `"0"`.
 *
 * Examples: `"1500"` → `"1,500"`, `"1500."` → `"1,500."`, `"1500.5"` → `"1,500.5"`.
 */
fun formatMoneyInputDisplay(raw: String): String {
    if (raw.isBlank()) return "0"
    val hasDot = raw.contains('.')
    val parts = raw.split('.', limit = 2)
    val intDigits = parts[0].filter { it.isDigit() }.ifEmpty { "0" }
    val frac = parts.getOrNull(1).orEmpty().filter { it.isDigit() }.take(2)
    val intFormatted = formatIntegerGrouping(intDigits)
    return if (hasDot) "$intFormatted.$frac" else intFormatted
}

/** Hero / keypad label: `"KES 1,500"` (or `"KES 0"` when blank). */
fun formatMoneyInputLabel(raw: String): String = "KES ${formatMoneyInputDisplay(raw)}"

/** Settled amount for confirm dialogs: `"KES 1,500.00"`. */
fun formatKesMoney(amount: Double): String = "KES ${formatKes(amount)}"

private fun formatIntegerGrouping(digits: String): String {
    val n = digits.toLongOrNull() ?: return digits
    return NumberFormat.getIntegerInstance(Locale.US).format(n)
}

/**
 * Shows grouped thousands in a text field while the underlying value stays
 * unformatted digits (plus optional `.` and cents).
 *
 * Caret mapping counts significant chars (digits + `.`) so commas are skipped.
 */
object MoneyAmountVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val original = text.text
        if (original.isEmpty()) {
            return TransformedText(AnnotatedString(""), OffsetMapping.Identity)
        }
        val formatted = formatMoneyInputDisplay(original)
        val mapping = SignificantCharOffsetMapping(original, formatted)
        return TransformedText(AnnotatedString(formatted), mapping)
    }
}

/**
 * Maps offsets by counting digits and `.` only — grouping commas are ignored.
 * Assumes [original] has no commas (already sanitized) and [formatted] is the
 * grouped display of the same digits / dot.
 */
private class SignificantCharOffsetMapping(
    private val original: String,
    private val formatted: String
) : OffsetMapping {
    override fun originalToTransformed(offset: Int): Int {
        val o = offset.coerceIn(0, original.length)
        val significant = countSignificant(original, o)
        return indexAfterSignificant(formatted, significant)
    }

    override fun transformedToOriginal(offset: Int): Int {
        val t = offset.coerceIn(0, formatted.length)
        val significant = countSignificant(formatted, t)
        return indexAfterSignificant(original, significant)
    }

    private fun countSignificant(s: String, endExclusive: Int): Int {
        var n = 0
        for (i in 0 until endExclusive.coerceAtMost(s.length)) {
            if (s[i].isDigit() || s[i] == '.') n++
        }
        return n
    }

    private fun indexAfterSignificant(s: String, significantCount: Int): Int {
        if (significantCount <= 0) return 0
        var n = 0
        for (i in s.indices) {
            if (s[i].isDigit() || s[i] == '.') {
                n++
                if (n == significantCount) return i + 1
            }
        }
        return s.length
    }
}
