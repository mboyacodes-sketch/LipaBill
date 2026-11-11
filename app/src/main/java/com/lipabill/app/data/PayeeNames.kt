package com.lipabill.app.data

import java.util.Locale

private val PLACEHOLDER_PAYBILL = Regex("""^paybill\s+(\d+)$""")
private val PLACEHOLDER_TILL = Regex("""^till\s+(\d+)$""")
private val PLACEHOLDER_POCHI = Regex("""^pochi(?:\s+la\s+biashara)?\s+(\S+)$""")
/** Branch / suburb suffixes only — avoid bare "town" so names like "China Town" stay intact. */
private val TRAILING_AREA = Regex(
    """\s+(westlands|kilimani|lavington|karen|cbd|branch|mall|centre|center|plaza|junction|stage)$"""
)

/** Fold dial placeholders and branch suffixes into a comparable merchant key. */
fun foldPayeeName(raw: String): String {
    val folded = foldMerchantName(raw) ?: return raw.trim().lowercase(Locale.US)
    PLACEHOLDER_PAYBILL.matchEntire(folded)?.let { return "bill:${it.groupValues[1]}" }
    PLACEHOLDER_TILL.matchEntire(folded)?.let { return "till:${it.groupValues[1]}" }
    PLACEHOLDER_POCHI.matchEntire(folded)?.let { return "pochi:${it.groupValues[1]}" }
    return stripTrailingArea(folded)
}

private fun stripTrailingArea(name: String): String {
    var s = name
    while (true) {
        val next = TRAILING_AREA.replace(s, "").trim()
        if (next == s || next.isEmpty()) return s
        s = next
    }
}
