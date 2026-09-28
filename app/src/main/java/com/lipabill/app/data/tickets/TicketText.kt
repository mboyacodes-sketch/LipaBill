package com.lipabill.app.data.tickets

import java.time.Month
import java.util.Locale

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
