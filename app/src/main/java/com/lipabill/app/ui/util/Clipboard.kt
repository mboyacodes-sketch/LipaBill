package com.lipabill.app.ui.util

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.util.Log

private const val TAG = "Clipboard"

/** @return false when the clipboard service rejects the write. */
fun Context.copyPlainText(label: String, text: String): Boolean {
    val clip = ClipData.newPlainText(label, text)
    if (Build.VERSION.SDK_INT >= 33) {
        clip.description.extras = PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
    }
    return try {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return false
        clipboard.setPrimaryClip(clip)
        true
    } catch (e: SecurityException) {
        Log.w(TAG, "copy_blocked", e)
        false
    } catch (e: IllegalStateException) {
        Log.w(TAG, "copy_failed", e)
        false
    }
}
