package com.lipabill.app.data.tickets

import java.time.Month
import java.util.Locale

internal val SGR_TERMINI = listOf(
    "Nairobi Terminus", "Mombasa Terminus", "Syokimau", "Athi River", "Emali",
    "Mtito Andei", "Voi", "Miasenyi", "Mariakani", "Suswa", "Naivasha", "Mai Mahiu"
)

/** Termini plus the short city names printed before they are normalized. */
internal val SGR_STATIONS = SGR_TERMINI + listOf("Nairobi", "Mombasa")

internal fun sgrStationName(name: String): String {
    val trimmed = name.trim()
    return when {
        trimmed.equals("Nairobi", true) -> "Nairobi Terminus"
        trimmed.equals("Mombasa", true) -> "Mombasa Terminus"
        else -> trimmed
    }
}

internal fun firstMatch(text: String, regex: Regex): String? =
    regex.find(text)?.groupValues?.getOrNull(1)?.trim()

internal fun parseMonth(raw: String): Month? {
    val key = raw.trim().lowercase(Locale.ENGLISH).take(3)
    return mapOf(
        "jan" to Month.JANUARY, "feb" to Month.FEBRUARY, "mar" to Month.MARCH,
        "apr" to Month.APRIL, "may" to Month.MAY, "jun" to Month.JUNE,
        "jul" to Month.JULY, "aug" to Month.AUGUST, "sep" to Month.SEPTEMBER,
        "oct" to Month.OCTOBER, "nov" to Month.NOVEMBER, "dec" to Month.DECEMBER
    )[key]
}
