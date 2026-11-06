package com.lipabill.app.ui.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

private const val TAG = "ExternalIntents"

/** Opens an https URL in a browser / App Link handler. Non-https schemes are rejected. */
fun Context.openHttpsUrl(url: String): Boolean {
    val uri = Uri.parse(url)
    if (!uri.scheme.equals("https", ignoreCase = true)) {
        Log.w(TAG, "rejected_non_https")
        return false
    }
    val intent = Intent(Intent.ACTION_VIEW, uri)
    return try {
        startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "no_browser", e)
        false
    } catch (e: SecurityException) {
        Log.w(TAG, "https_blocked", e)
        false
    }
}

/** Opens the mail composer for [email]. */
fun Context.openMailto(email: String, subject: String): Boolean {
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:")
        putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
        putExtra(Intent.EXTRA_SUBJECT, subject)
    }
    return try {
        startActivity(Intent.createChooser(intent, "Email support"))
        true
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "no_mail_app", e)
        false
    } catch (e: SecurityException) {
        Log.w(TAG, "mailto_blocked", e)
        false
    }
}
