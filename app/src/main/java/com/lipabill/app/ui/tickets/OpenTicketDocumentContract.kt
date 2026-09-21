package com.lipabill.app.ui.tickets

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract

/**
 * Opens the system document picker for e-tickets / boarding passes.
 *
 * Uses ACTION_OPEN_DOCUMENT with an explicit MIME list so PDFs stay selectable
 * on OEMs that mishandle multi-type OpenDocument launches.
 */
class OpenTicketDocumentContract : ActivityResultContract<Unit, Uri?>() {

    override fun createIntent(context: Context, input: Unit): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf(
                    "application/pdf",
                    "image/*",
                    "image/jpeg",
                    "image/png",
                    "image/webp",
                    "application/vnd.apple.pkpass",
                    "application/zip",
                    "application/octet-stream"
                )
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        if (resultCode != Activity.RESULT_OK) return null
        return intent?.data
    }
}
