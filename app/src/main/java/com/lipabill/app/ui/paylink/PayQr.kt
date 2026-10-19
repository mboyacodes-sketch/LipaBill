package com.lipabill.app.ui.paylink

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** QR with high error correction so a center mark can sit on it and still scan. */
fun encodePayQr(value: String, sizePx: Int = 800): ImageBitmap? {
    if (value.isBlank()) return null
    return runCatching {
        val matrix = MultiFormatWriter().encode(
            value,
            BarcodeFormat.QR_CODE,
            sizePx,
            sizePx,
            mapOf(
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.MARGIN to 1,
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H
            )
        )
        val bitmap = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                bitmap.setPixel(
                    x,
                    y,
                    if (matrix.get(x, y)) AndroidColor.BLACK else AndroidColor.WHITE
                )
            }
        }
        bitmap.asImageBitmap()
    }.getOrNull()
}
