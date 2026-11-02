package com.lipabill.app.data.model

import android.content.Context
import android.telephony.TelephonyManager
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
