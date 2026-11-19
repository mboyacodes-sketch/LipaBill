package com.lipabill.app.metrics

import android.util.Log
import com.lipabill.app.BuildConfig

/**
 * Debug-only logcat. Release builds are a no-op (R8 also strips [Log]).
 *
 * Messages must be closed tokens — never SMS bodies, PINs, amounts, phones,
 * USSD dialog text, file names with PII, or accessibility node dumps.
 * Throwables are logged by type name only (no [Throwable.getMessage]).
 */
object AppLog {

    fun d(tag: String, msg: String) {
        if (BuildConfig.DEBUG) Log.d(tag, msg)
    }

    fun i(tag: String, msg: String) {
        if (BuildConfig.DEBUG) Log.i(tag, msg)
    }

    fun w(tag: String, msg: String, error: Throwable? = null) {
        if (!BuildConfig.DEBUG) return
        if (error == null) Log.w(tag, msg) else Log.w(tag, "$msg ${error.javaClass.simpleName}")
    }

    fun e(tag: String, msg: String, error: Throwable? = null) {
        if (!BuildConfig.DEBUG) return
        if (error == null) Log.e(tag, msg) else Log.e(tag, "$msg ${error.javaClass.simpleName}")
    }
}
