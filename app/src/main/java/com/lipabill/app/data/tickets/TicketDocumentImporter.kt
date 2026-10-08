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
        val mime = context.contentResolver.getType(uri)
        val name = displayName(context, uri)
        val isPkPass = kind == TicketDocumentKind.PKPASS ||
            PkPassParser.isPkPassUri(uri, mime, name) ||
            name?.endsWith(".pkpass", ignoreCase = true) == true

        return when {
            isPkPass && kind == TicketDocumentKind.FLIGHT_E_TICKET -> {
                // Wallet pass on the flight path = boarding already in hand
                PkPassParser.parse(context, uri).toTicketImport().copy(
                    expectsBoardingPass = true,
                    hasBoardingPass = true
                )
            }
            isPkPass -> PkPassParser.parse(context, uri).toTicketImport()
            else -> ETicketAnalyzer.analyze(context, uri, kind)
        }
    }

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
