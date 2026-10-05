package com.lipabill.app.ui.util

import android.content.ClipboardManager
import android.content.Context

fun readClipboardText(context: Context): String? {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        ?: return null
    val clip = cm.primaryClip ?: return null
    if (clip.itemCount <= 0) return null
    return clip.getItemAt(0).coerceToText(context)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
}

/** One line for pasting into a single Send or Pay field. */
fun String.asFieldPaste(maxLength: Int = 80): String =
    replace(Regex("\\s+"), " ").trim().take(maxLength)
