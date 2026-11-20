package com.lipabill.app.data.model

import android.content.Context
import android.telephony.TelephonyManager
import com.lipabill.app.ussd.SimLineHelper
import java.util.Locale

enum class RegionGuessSource {
    SIM,
    NETWORK,
    LOCALE
}

data class RegionGuess(
    val region: AppRegion,
    val source: RegionGuessSource
)

/**
 * SIM first (the wallet lives on that line), then the registered network,
 * then the phone language. Unknown countries stay null so Kenya is not forced.
 */
fun guessAppRegion(
    simIso: String?,
    networkIso: String?,
    localeIso: String?
): RegionGuess? {
    AppRegion.fromIsoOrNull(simIso)?.let {
        return RegionGuess(it, RegionGuessSource.SIM)
    }
    AppRegion.fromIsoOrNull(networkIso)?.let {
        return RegionGuess(it, RegionGuessSource.NETWORK)
    }
    AppRegion.fromIsoOrNull(localeIso)?.let {
        return RegionGuess(it, RegionGuessSource.LOCALE)
    }
    return null
}

fun RegionGuess.hint(): String = when (source) {
    RegionGuessSource.SIM -> "From this SIM — change if that's not right."
    RegionGuessSource.NETWORK -> "From the mobile network — change if that's not right."
    RegionGuessSource.LOCALE -> "From this phone — change if that's not right."
}

/**
 * Map SIM country ISO / MCC into LipaBill regions. Empty when nothing
 * recognizable is present (do not gate the picker in that case).
 */
fun regionsFromSimSignals(
    countryIsos: Collection<String?>,
    mccs: Collection<Int?> = emptyList()
): Set<AppRegion> {
    val found = linkedSetOf<AppRegion>()
    for (iso in countryIsos) {
        AppRegion.fromIsoOrNull(iso)?.let { found += it }
    }
    for (mcc in mccs) {
        AppRegion.fromMccOrNull(mcc)?.let { found += it }
    }
    return found
}

/**
 * Regions the user may pick when SIMs are known.
 * Keeps [currentlySelected] choosable so Settings never traps a past pick.
 * Empty [presentOnSims] → every region (unknown / no SIM signal).
 */
fun selectableAppRegions(
    presentOnSims: Set<AppRegion>,
    currentlySelected: AppRegion
): Set<AppRegion> {
    if (presentOnSims.isEmpty()) return AppRegion.entries.toSet()
    return presentOnSims + currentlySelected
}

/**
 * SIM and network country codes do not need extra permission.
 * Location is not read. Dual-SIM uses the default voice line.
 */
fun Context.detectAppRegion(): RegionGuess? {
    val telephony = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    return guessAppRegion(
        simIso = telephony?.simCountryIso,
        networkIso = telephony?.networkCountryIso,
        localeIso = Locale.getDefault().country
    )
}

/** Countries reported by SIMs in this phone (ISO and/or MCC). */
fun Context.regionsPresentOnSims(): Set<AppRegion> {
    val signals = SimLineHelper.listSimRegionSignals(this)
    if (signals.isNotEmpty()) {
        return regionsFromSimSignals(
            countryIsos = signals.map { it.countryIso },
            mccs = signals.map { it.mcc }
        )
    }
    val telephony = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    return regionsFromSimSignals(
        countryIsos = listOf(telephony?.simCountryIso),
        mccs = emptyList()
    )
}
