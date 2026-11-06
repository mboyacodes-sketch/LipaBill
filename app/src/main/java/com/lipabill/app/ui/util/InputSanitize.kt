package com.lipabill.app.ui.util

/** Digits only, capped — till / business / social numeric handles. */
fun sanitizeDigits(raw: String): String = sanitizeDigits(raw, max = 12)

fun sanitizeDigits(raw: String, max: Int): String =
    raw.filter { it.isDigit() }.take(max)

/** Request / Send phone field: digits, spaces, and a leading +. */
fun sanitizePayPhone(raw: String): String =
    raw.filter { it.isDigit() || it == '+' || it == ' ' }.take(16)

/** Paybill account / shop code. */
fun sanitizePayAccount(raw: String): String =
    raw.filter { it.isLetterOrDigit() }.take(12)

/** Whole-shilling amount typed on Request QR (KES, no decimals). */
fun sanitizeWholeShillings(raw: String): String =
    raw.filter { it.isDigit() }.take(6)
