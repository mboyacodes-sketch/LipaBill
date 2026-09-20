package com.lipabill.app.data.tickets

import android.content.Intent
import android.net.Uri

/**
 * Resolves a ticket document URI from VIEW / SEND intents (.pkpass, PDF, images).
 */
object PkPassIntents {

    fun extractUri(intent: Intent?): Uri? {
        if (intent == null) return null
        val action = intent.action
        val type = intent.type?.lowercase().orEmpty()
        val data = intent.data
        val stream = intent.getParcelableExtraCompat(Intent.EXTRA_STREAM)
        val name = (data ?: stream)?.lastPathSegment.orEmpty()

        return when (action) {
            Intent.ACTION_VIEW -> {
                val uri = data ?: return null
                when {
                    isTicketDocument(uri, type, name) -> uri
                    else -> null
                }
            }
            Intent.ACTION_SEND -> {
                val candidate = stream ?: data ?: return null
                when {
                    isTicketDocument(candidate, type, name.ifBlank { candidate.lastPathSegment.orEmpty() }) ->
                        candidate
                    else -> null
                }
            }
            else -> null
        }
    }

    private fun isTicketDocument(uri: Uri, mime: String, name: String): Boolean {
        if (PkPassParser.isPkPassUri(uri, mime, name)) return true
        if (mime == "application/vnd.apple.pkpass") return true
        if (mime == "application/pdf" || name.endsWith(".pdf", ignoreCase = true)) return true
        if (mime.startsWith("image/")) return true
        if (mime == "application/octet-stream" &&
            (name.endsWith(".pdf", true) || name.endsWith(".pkpass", true))
        ) {
            return true
        }
        return false
    }

    @Suppress("DEPRECATION")
    private fun Intent.getParcelableExtraCompat(key: String): Uri? {
        return if (android.os.Build.VERSION.SDK_INT >= 33) {
            getParcelableExtra(key, Uri::class.java)
        } else {
            getParcelableExtra(key) as? Uri
        }
    }
}
