package com.lipabill.app.data

import java.util.Locale

/** Lowercase + collapsed whitespace for merchant / category matching. */
fun foldMerchantName(raw: String?): String? {
    val text = raw?.trim()?.lowercase(Locale.US).orEmpty()
    if (text.isEmpty()) return null
    return text.replace(Regex("\\s+"), " ")
}
