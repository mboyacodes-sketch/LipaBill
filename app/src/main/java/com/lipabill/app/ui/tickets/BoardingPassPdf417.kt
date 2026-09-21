package com.lipabill.app.ui.tickets

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.util.Log
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.pdf417.PDF417Writer
import com.google.zxing.pdf417.encoder.Compaction

/**
 * Paper-style **PDF417** boarding-pass barcode (IATA BCBP payload).
 * Display is horizontal (wide zebra), filling the ticket header width.
 */
object BoardingPassPdf417 {

    private const val TAG = "LipaBill.PDF417"

    /**
     * Encodes payload → horizontal PDF417 [BitMatrix] (wider than tall).
     *
     * @param allowPlaceholder when false (default), refuses ETKT:/BOARDING:/REF: stubs
     *   used for gate BCBP. Booking cards pass true to encode `REF:{PNR}`.
     */
    fun encodeHorizontalMatrix(
        bcbpPayload: String,
        width: Int = 900,
        height: Int = 160,
        allowPlaceholder: Boolean = false
    ): BitMatrix? {
        val raw = bcbpPayload.trim()
        if (raw.isEmpty()) return null
        if (!allowPlaceholder && (
            raw.startsWith("ETKT:", ignoreCase = true) ||
                raw.startsWith("BOARDING:", ignoreCase = true) ||
                raw.startsWith("REF:", ignoreCase = true)
            )
        ) {
            Log.w(TAG, "refusing non-BCBP placeholder for PDF417")
            return null
        }

        val hints = mapOf<EncodeHintType, Any>(
            EncodeHintType.PDF417_COMPACTION to Compaction.BYTE,
            EncodeHintType.MARGIN to 2,
            EncodeHintType.CHARACTER_SET to "ISO-8859-1",
            EncodeHintType.ERROR_CORRECTION to 2
        )

        return runCatching {
            PDF417Writer().encode(
                raw,
                BarcodeFormat.PDF_417,
                width.coerceAtLeast(200),
                height.coerceAtLeast(60),
                hints
            )
        }.onFailure { t ->
            Log.w(TAG, "PDF417Writer failed len=${raw.length}", t)
        }.getOrNull()
    }

    /**
     * @return horizontal [Bitmap] (ARGB_8888), or null if the payload cannot be encoded.
     * Caller owns the bitmap and should [Bitmap.recycle] when done if not handed to Compose.
     */
    fun encodeHorizontalBitmap(
        bcbpPayload: String,
        width: Int = 900,
        height: Int = 160,
        foregroundColor: Int = AndroidColor.BLACK,
        backgroundColor: Int = AndroidColor.WHITE,
        allowPlaceholder: Boolean = false
    ): Bitmap? {
        val matrix = encodeHorizontalMatrix(bcbpPayload, width, height, allowPlaceholder)
            ?: return null
        return bitMatrixToBitmap(matrix, foregroundColor, backgroundColor)
    }

    private fun bitMatrixToBitmap(
        matrix: BitMatrix,
        foregroundColor: Int = AndroidColor.BLACK,
        backgroundColor: Int = AndroidColor.WHITE
    ): Bitmap {
        val w = matrix.width
        val h = matrix.height
        val pixels = IntArray(w * h)
        var i = 0
        for (y in 0 until h) {
            for (x in 0 until w) {
                pixels[i++] =
                    if (matrix.get(x, y)) foregroundColor else backgroundColor
            }
        }
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bmp ->
            bmp.setPixels(pixels, 0, w, 0, 0, w, h)
        }
    }
}
