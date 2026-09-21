package com.lipabill.app.ui.tickets

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.lipabill.app.data.model.TicketBarcodeFormat

private const val TAG = "LipaBill.Barcode"

fun TicketBarcodeFormat.toZxing(): BarcodeFormat = when (this) {
    TicketBarcodeFormat.QR_CODE -> BarcodeFormat.QR_CODE
    TicketBarcodeFormat.CODE_128 -> BarcodeFormat.CODE_128
    TicketBarcodeFormat.PDF_417 -> BarcodeFormat.PDF_417
    TicketBarcodeFormat.AZTEC -> BarcodeFormat.AZTEC
    TicketBarcodeFormat.OTHER -> BarcodeFormat.QR_CODE
}

/**
 * General ticket barcode (events / SGR / booking).
 *
 * @param onGrey black modules on transparent — sits on the grey boarding-style header.
 */
fun encodeTicketBarcode(
    value: String,
    format: TicketBarcodeFormat = TicketBarcodeFormat.QR_CODE,
    sizePx: Int = 512,
    onGrey: Boolean = false
): ImageBitmap? {
    if (value.isBlank()) return null
    val fg = AndroidColor.BLACK
    val bg = if (onGrey) AndroidColor.TRANSPARENT else AndroidColor.WHITE
    if (format == TicketBarcodeFormat.PDF_417) {
        return BoardingPassPdf417.encodeHorizontalBitmap(
            value,
            foregroundColor = fg,
            backgroundColor = bg
        )?.let { bmp ->
            try {
                val copy = bmp.copy(Bitmap.Config.ARGB_8888, false) ?: bmp
                if (copy !== bmp) bmp.recycle()
                copy.asImageBitmap()
            } catch (_: Throwable) {
                if (!bmp.isRecycled) bmp.recycle()
                null
            }
        }
    }
    if (format == TicketBarcodeFormat.AZTEC) {
        return encodeAztecBoardingPass(value, sizePx)
    }
    return encodeWithZxing(value, format.toZxing(), sizePx, fg, bg)
}

/**
 * Flight boarding pass only — **Aztec via ZXing, no QR fallback**.
 * Encodes the IATA BCBP payload exactly as on the pass (scanned or rebuilt).
 */
fun encodeAztecBoardingPass(
    bcbpPayload: String,
    sizePx: Int = 512
): ImageBitmap? {
    val raw = bcbpPayload.trim()
    if (raw.isEmpty()) return null
    // Never draw ETKT:/BOARDING: stubs as if they were gate codes
    if (raw.startsWith("ETKT:", ignoreCase = true) ||
        raw.startsWith("BOARDING:", ignoreCase = true) ||
        raw.startsWith("REF:", ignoreCase = true)
    ) {
        Log.w(TAG, "refusing non-BCBP placeholder for Aztec")
        return null
    }

    val sizes = listOf(sizePx, 640, 800, 1024).distinct().sorted()
    for (size in sizes) {
        val matrix = encodeAztecMatrix(raw, size) ?: continue
        return bitMatrixToImage(
            matrix,
            quietZonePx = (size * 0.06f).toInt().coerceIn(8, 24)
        )
    }
    Log.w(TAG, "Aztec encode failed for payload len=${raw.length}")
    return null
}

private fun encodeAztecMatrix(value: String, sizePx: Int): BitMatrix? {
    val hintSets = listOf(
        // ~23% EC — common for airline mobile boarding passes
        mapOf(
            EncodeHintType.CHARACTER_SET to "ISO-8859-1",
            EncodeHintType.ERROR_CORRECTION to 23,
            EncodeHintType.MARGIN to 0
        ),
        mapOf(
            EncodeHintType.CHARACTER_SET to "ISO-8859-1",
            EncodeHintType.MARGIN to 0
        ),
        mapOf(EncodeHintType.MARGIN to 0),
        emptyMap<EncodeHintType, Any>()
    )
    for (hints in hintSets) {
        runCatching {
            return MultiFormatWriter().encode(
                value,
                BarcodeFormat.AZTEC,
                sizePx,
                sizePx,
                hints.ifEmpty { null }
            )
        }.onFailure { t ->
            Log.d(TAG, "Aztec attempt failed size=$sizePx: ${t.message}")
        }
    }
    return null
}

private fun encodeWithZxing(
    value: String,
    format: BarcodeFormat,
    sizePx: Int,
    foregroundColor: Int = AndroidColor.BLACK,
    backgroundColor: Int = AndroidColor.WHITE
): ImageBitmap? {
    return runCatching {
        val height = when (format) {
            BarcodeFormat.CODE_128 -> (sizePx * 0.42f).toInt().coerceAtLeast(120)
            BarcodeFormat.PDF_417 -> (sizePx * 0.55f).toInt().coerceAtLeast(160)
            else -> sizePx
        }
        val matrix = MultiFormatWriter().encode(
            value,
            format,
            sizePx,
            height,
            mapOf(
                EncodeHintType.CHARACTER_SET to "ISO-8859-1",
                EncodeHintType.MARGIN to 1
            )
        )
        bitMatrixToImage(
            matrix,
            quietZonePx = 8,
            foregroundColor = foregroundColor,
            backgroundColor = backgroundColor
        )
    }.onFailure { t ->
        Log.w(TAG, "zxing encode failed format=$format len=${value.length}", t)
    }.getOrNull()
}

private fun bitMatrixToImage(
    matrix: BitMatrix,
    quietZonePx: Int,
    foregroundColor: Int = AndroidColor.BLACK,
    backgroundColor: Int = AndroidColor.WHITE
): ImageBitmap {
    val q = quietZonePx.coerceAtLeast(0)
    val w = matrix.width + q * 2
    val h = matrix.height + q * 2
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(backgroundColor)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = foregroundColor
        style = Paint.Style.FILL
    }
    for (x in 0 until matrix.width) {
        for (y in 0 until matrix.height) {
            if (matrix.get(x, y)) {
                canvas.drawRect(
                    (x + q).toFloat(),
                    (y + q).toFloat(),
                    (x + q + 1).toFloat(),
                    (y + q + 1).toFloat(),
                    paint
                )
            }
        }
    }
    return bitmap.asImageBitmap()
}

@Composable
fun rememberTicketQrBitmap(
    value: String,
    format: TicketBarcodeFormat = TicketBarcodeFormat.QR_CODE,
    sizePx: Int = 512,
    onGrey: Boolean = false
): ImageBitmap? = remember(value, format, sizePx, onGrey) {
    encodeTicketBarcode(value, format, sizePx, onGrey = onGrey)
}

/** Flight boarding — Aztec only (ZXing), never QR. */
@Composable
fun rememberFlightAztecBitmap(
    bcbpPayload: String?,
    sizePx: Int = 512
): ImageBitmap? = remember(bcbpPayload, sizePx) {
    bcbpPayload?.let { encodeAztecBoardingPass(it, sizePx) }
}
