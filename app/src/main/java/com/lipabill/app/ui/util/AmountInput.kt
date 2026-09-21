package com.lipabill.app.ui.util

/** Digits plus at most one decimal point with up to 2 fraction digits. */
fun sanitizeAmountInput(value: String): String {
    val raw = value.filter { it.isDigit() || it == '.' }
    val parts = raw.split('.')
    return if (parts.size <= 1) {
        raw
    } else {
        parts.first() + "." + parts.drop(1).joinToString("").take(2)
    }
}
