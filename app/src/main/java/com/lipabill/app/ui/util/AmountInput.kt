package com.lipabill.app.ui.util

import java.text.NumberFormat
import java.util.Locale

/** Field has digits and no letters, so the in-app keypad should open on the digit keys. */
fun String.opensOnDigitKeys(): Boolean =
    any { it.isDigit() } && none { it.isLetter() }

/** One keypad tap: at most one decimal point, at most two fraction digits. */
fun appendAmountKey(current: String, key: String): String {
    return when (key) {
        "." -> {
            if (current.contains('.')) current
            else if (current.isEmpty()) "0." else current + "."
        }
        else -> sanitizeAmountInput(current + key)
    }
}

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

/**
 * Digits for amount fields: whole values without a decimal, else up to 2 fraction digits.
 * When [blankIfNonPositive], zero/negative become blank (add-up “use total”).
 */
fun Double.toAmountInput(blankIfNonPositive: Boolean = false): String {
    if (blankIfNonPositive && this <= 0.0) return ""
    val rounded = kotlin.math.round(this * 100.0) / 100.0
    val text = if (kotlin.math.abs(rounded % 1.0) < 0.001) {
        rounded.toLong().toString()
    } else {
        "%.2f".format(Locale.US, rounded).trimEnd('0').trimEnd('.')
    }
    return sanitizeAmountInput(text)
}

private fun formatIntegerGrouping(digits: String): String {
    val n = digits.toLongOrNull() ?: return digits
    return NumberFormat.getIntegerInstance(Locale.US).format(n)
}
