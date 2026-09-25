package com.lipabill.app.region

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.lipabill.app.BuildConfig
import com.lipabill.app.ussd.SimLineHelper

/**
 * LipaBill is built for Kenya (Safaricom M-Pesa). Play builds block use when the
 * device is clearly outside Kenya. Internal / debug builds skip the gate for QA.
 *
 * Signals (any Kenya match → allow):
 * - Network or SIM country ISO `ke`
 * - Active subscription country ISO `ke`
 * - Safaricom SIM (MCC/MNC 639-02 or carrier name)
 *
 * If no country signal is available yet (no radio / no permission), allow so
 * first-run and airplane mode are not bricked — payment paths still need Safaricom.
 */
object KenyaRegionGate {

    const val COUNTRY_ISO = "ke"

    sealed class Verdict {
        data object Allowed : Verdict()
        data class Blocked(val detectedIso: String?) : Verdict()
    }

    fun evaluate(context: Context): Verdict {
        if (BuildConfig.DEBUG || BuildConfig.SIDELOAD_DISTRIBUTION) {
            return Verdict.Allowed
        }

        val appCtx = context.applicationContext
        val isos = collectCountryIsos(appCtx)

        if (isos.any { it == COUNTRY_ISO }) return Verdict.Allowed
        if (SimLineHelper.hasPhoneStatePermission(appCtx) &&
            SimLineHelper.hasSafaricomSim(appCtx)
        ) {
            return Verdict.Allowed
        }

        val foreign = isos.firstOrNull { it != COUNTRY_ISO }
        if (foreign != null) return Verdict.Blocked(foreign)

        // Unknown — don't block; Safaricom gates still protect Send/Pay.
        return Verdict.Allowed
    }

    fun isAllowed(context: Context): Boolean =
        evaluate(context) is Verdict.Allowed

    @SuppressLint("MissingPermission")
    private fun collectCountryIsos(context: Context): List<String> {
        val out = LinkedHashSet<String>()
        val tm = context.getSystemService(TelephonyManager::class.java)
        tm?.networkCountryIso?.normalizeIso()?.let { out += it }
        tm?.simCountryIso?.normalizeIso()?.let { out += it }

        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_PHONE_STATE
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            val sm = context.getSystemService(SubscriptionManager::class.java)
            val infos = try {
                sm?.activeSubscriptionInfoList
            } catch (_: SecurityException) {
                null
            }
            infos?.forEach { info ->
                info.countryIso?.normalizeIso()?.let { out += it }
            }
        }
        return out.toList()
    }

    private fun String.normalizeIso(): String? =
        trim().lowercase().takeIf { it.length == 2 }
}
