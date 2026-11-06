package com.lipabill.app.ui.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

private const val TAG = "PersistableUri"

/** Best-effort persistable read grant for a document picker URI. */
fun Context.takePersistableRead(uri: Uri) {
    runCatching {
        contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    }.onFailure {
        Log.w(TAG, "persist_read_failed", it)
    }
}
