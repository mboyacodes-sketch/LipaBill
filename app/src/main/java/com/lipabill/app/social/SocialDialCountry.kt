package com.lipabill.app.social

import com.lipabill.app.data.model.AppRegion
import com.lipabill.app.data.model.isoToFlagEmoji

/**
 * Dialing codes for socials that store an international phone (WhatsApp).
 * Order: East Africa first, then other common WhatsApp markets.
 */
data class SocialDialCountry(
    val iso: String,
    val dial: String,
    val label: String
) {
    val flagEmoji: String get() = isoToFlagEmoji(iso)
    val chipLabel: String get() = "$flagEmoji +$dial"
}

object SocialDialCountries {
    val all: List<SocialDialCountry> = listOf(
        SocialDialCountry("KE", "254", "Kenya"),
        SocialDialCountry("UG", "256", "Uganda"),
        SocialDialCountry("TZ", "255", "Tanzania"),
        SocialDialCountry("RW", "250", "Rwanda"),
        SocialDialCountry("ET", "251", "Ethiopia"),
        SocialDialCountry("SS", "211", "South Sudan"),
        SocialDialCountry("SO", "252", "Somalia"),
        SocialDialCountry("NG", "234", "Nigeria"),
        SocialDialCountry("GH", "233", "Ghana"),
        SocialDialCountry("ZA", "27", "South Africa"),
        SocialDialCountry("AE", "971", "United Arab Emirates"),
        SocialDialCountry("SA", "966", "Saudi Arabia"),
        SocialDialCountry("IN", "91", "India"),
        SocialDialCountry("GB", "44", "United Kingdom"),
        SocialDialCountry("US", "1", "United States"),
        SocialDialCountry("CA", "1", "Canada")
    )

    fun defaultFor(region: AppRegion): SocialDialCountry =
        all.firstOrNull { it.iso.equals(region.iso, ignoreCase = true) }
            ?: all.first()

    fun byDial(dial: String): SocialDialCountry? =
        all.firstOrNull { it.dial == dial.filter { ch -> ch.isDigit() } }

    /**
     * Split a stored international number into dial + national.
     * Longest dial match wins so `1` does not steal `254`.
     */
    fun splitInternational(
        digits: String,
        fallback: SocialDialCountry
    ): Pair<SocialDialCountry, String> {
        val clean = digits.filter { it.isDigit() }
        if (clean.isEmpty()) return fallback to ""
        val match = all
            .sortedByDescending { it.dial.length }
            .firstOrNull { clean.startsWith(it.dial) && clean.length > it.dial.length }
        return if (match != null) {
            match to clean.removePrefix(match.dial)
        } else {
            fallback to clean.trimStart('0')
        }
    }

    fun composeInternational(dial: String, national: String): String {
        val d = dial.filter { it.isDigit() }
        val n = national.filter { it.isDigit() }.trimStart('0')
        return d + n
    }
}
