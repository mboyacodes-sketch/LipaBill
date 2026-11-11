package com.lipabill.app.ui.tickets

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.lipabill.app.data.model.TicketBarcodeFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "LipaBill.Barcode"

fun TicketBarcodeFormat.toZxing(): BarcodeFormat = when (this) {
    TicketBarcodeFormat.QR_CODE -> BarcodeFormat.QR_CODE
    TicketBarcodeFormat.CODE_128 -> BarcodeFormat.CODE_128
    TicketBarcodeFormat.PDF_417 -> BarcodeFormat.PDF_417
    TicketBarcodeFormat.AZTEC -> BarcodeFormat.AZTEC
    TicketBarcodeFormat.OTHER -> BarcodeFormat.QR_CODE
}

/** Small LRU so ticket detail reuses bitmaps instead of re-encoding on the UI thread. */
internal object TicketBarcodeCache {
    private const val MaxEntries = 24
    private val lock = Any()
    private val map = object : LinkedHashMap<String, ImageBitmap>(MaxEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean =
            size > MaxEntries
    }

    private fun key(
        value: String,
        format: TicketBarcodeFormat,
        sizePx: Int,
        onGrey: Boolean
    ): String = "$format|$sizePx|$onGrey|$value"

    fun getOrNull(
        value: String,
        format: TicketBarcodeFormat,
        sizePx: Int,
        onGrey: Boolean
    ): ImageBitmap? = synchronized(lock) { map[key(value, format, sizePx, onGrey)] }

    fun get(
        value: String,
        format: TicketBarcodeFormat,
        sizePx: Int,
        onGrey: Boolean
    ): ImageBitmap? {
        getOrNull(value, format, sizePx, onGrey)?.let { return it }
        val encoded = encodeTicketBarcode(value, format, sizePx, onGrey = onGrey) ?: return null
        synchronized(lock) { map[key(value, format, sizePx, onGrey)] = encoded }
        return encoded
    }
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
    return encodeWithZxing(value, format.toZxing(), sizePx, fg, bg)
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

/**
 * Encodes off the main thread. Pass a null/blank [value] to skip encoding without
 * skipping [remember] (Compose slot rules).
 */
@Composable
fun rememberTicketQrBitmap(
    value: String?,
    format: TicketBarcodeFormat = TicketBarcodeFormat.QR_CODE,
    sizePx: Int = 512,
    onGrey: Boolean = false
): ImageBitmap? {
    val code = value?.takeIf { it.isNotBlank() }
    var bitmap by remember(code, format, sizePx, onGrey) {
        mutableStateOf(
            code?.let { TicketBarcodeCache.getOrNull(it, format, sizePx, onGrey) }
        )
    }
    LaunchedEffect(code, format, sizePx, onGrey) {
        if (code == null) {
            bitmap = null
            return@LaunchedEffect
        }
        if (bitmap != null) return@LaunchedEffect
        bitmap = withContext(Dispatchers.Default) {
            TicketBarcodeCache.get(code, format, sizePx, onGrey)
        }
    }
    return bitmap
}
