package com.lipabill.app.data.tickets

import java.util.Locale

/** East African airports this app treats as real IATA codes, not city-name fragments. */
internal val KNOWN_IATA = setOf(
    "NBO", "MBA", "KIS", "EDL", "MYD", "ZNZ", "JRO", "DAR", "EBB", "KGL", "WIL"
)

private val IataExact = Regex("""^[A-Z]{3}$""")
private val IataWord = Regex("""\b([A-Z]{3})\b""")
private val IataParen = Regex("""\(([A-Z]{3})\)""")
private val IataRoute = Regex("""\b([A-Z]{3})\s*→\s*([A-Z]{3})\b""")

/** Any 3-letter code: parenthetical, a bare token, or the first word in the text. */
internal fun extractIata(raw: String?): String? {
    val text = routeText(raw) ?: return null
    parentheticalIata(text)?.let { return it }
    if (IataExact.matches(text)) return text
    return IataWord.find(text)?.groupValues?.getOrNull(1)
}

/**
 * A code this app knows, or a Kenyan city name mapped to its airport.
 * Ignores city-name fragments such as "Nairob".
 */
internal fun extractKnownIata(raw: String?): String? {
    val text = routeText(raw) ?: return null
    parentheticalIata(text)?.takeIf { it in KNOWN_IATA }?.let { return it }
    if (text in KNOWN_IATA) return text
    when {
        text.contains("NAIROBI") || text.contains("JKIA") || text.contains("JOMO") -> return "NBO"
        text.contains("MOMBASA") || text.contains("MOI") -> return "MBA"
        text.contains("KISUMU") -> return "KIS"
        text.contains("ELDORET") -> return "EDL"
        text.contains("MALINDI") -> return "MYD"
    }
    return IataWord.findAll(text)
        .map { it.groupValues[1] }
        .firstOrNull { it in KNOWN_IATA }
}

/** Two known airport codes joined by an arrow. */
internal fun parseIataRoute(raw: String?): Pair<String, String>? {
    val text = routeText(raw) ?: return null
    val match = IataRoute.find(text) ?: return null
    val from = match.groupValues[1]
    val to = match.groupValues[2]
    if (from !in KNOWN_IATA || to !in KNOWN_IATA) return null
    return from to to
}

private fun routeText(raw: String?): String? =
    raw?.trim()?.takeIf { it.isNotEmpty() }?.uppercase(Locale.US)

private fun parentheticalIata(text: String): String? =
    IataParen.find(text)?.groupValues?.getOrNull(1)
