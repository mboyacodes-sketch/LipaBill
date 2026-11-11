package com.lipabill.app.data.tickets

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

/**
 * Routes an uploaded file to .pkpass or e-ticket (PDF / image) analysis.
 */
object TicketDocumentImporter {

    suspend fun import(
        context: Context,
        uri: Uri,
        kind: TicketDocumentKind = TicketDocumentKind.AUTO
    ): TicketImport {
        val isPkPass = kind == TicketDocumentKind.PKPASS || isPkPass(context, uri)

        return when {
            isPkPass -> PkPassParser.parse(context, uri).toTicketImport()
            else -> ETicketAnalyzer.analyze(context, uri, kind)
        }
    }

    /** True for Wallet passes, by MIME type, path or the file's display name. */
    fun isPkPass(context: Context, uri: Uri): Boolean =
        PkPassParser.isPkPassUri(uri, context.contentResolver.getType(uri), displayName(context, uri))

    private fun displayName(context: Context, uri: Uri): String? {
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) return cursor.getString(idx)
                    }
                }
        }
        return uri.lastPathSegment
    }
}
