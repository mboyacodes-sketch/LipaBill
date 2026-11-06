package com.lipabill.app.ui.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log

private const val TAG = "ShareIntents"

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
        Log.w(TAG, "no_share_handler", e)
        false
    } catch (e: SecurityException) {
        Log.w(TAG, "share_blocked", e)
        false
    }
}
