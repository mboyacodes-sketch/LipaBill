package com.lipabill.app.ui.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.lipabill.app.metrics.AppLog

private const val TAG = "ShareIntents"

/**
 * Opens the dialer with [phone] pre-filled (no CALL_PHONE permission).
 * @return false when no dialer is available or startActivity fails.
 */
fun Context.dialPhoneNumber(phone: String): Boolean {
    val trimmed = phone.trim()
    if (trimmed.isEmpty()) return false
    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(trimmed)}"))
    return try {
        startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        AppLog.w(TAG, "no_dialer", e)
        false
    } catch (e: SecurityException) {
        AppLog.w(TAG, "dial_blocked", e)
        false
    }
}

/**
 * Opens the system share sheet for plain text.
 * @return false when no handler is available or startActivity fails.
 */
fun Context.sharePlainText(chooserTitle: String, text: String): Boolean {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    return try {
        startActivity(Intent.createChooser(send, chooserTitle))
        true
    } catch (e: ActivityNotFoundException) {
        AppLog.w(TAG, "no_share_handler", e)
        false
    } catch (e: SecurityException) {
        AppLog.w(TAG, "share_blocked", e)
        false
    }
}
