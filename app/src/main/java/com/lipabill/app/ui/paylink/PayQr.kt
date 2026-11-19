package com.lipabill.app.ui.paylink

import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.ImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.lipabill.app.ui.components.RoundedQrRenderer

/** QR with high error correction so a center mark can sit on it and still scan. */
fun encodePayQr(value: String, sizePx: Int = 800): ImageBitmap? {
    if (value.isBlank()) return null
    return runCatching {
        // Module-sized matrix (width/height 1 → ZXing expands to modules + quiet zone).
        val matrix = MultiFormatWriter().encode(
            value,
            BarcodeFormat.QR_CODE,
            1,
            1,
            mapOf(
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.MARGIN to 1,
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H
            )
        )
        RoundedQrRenderer.toImageBitmap(
            matrix = matrix,
            sizePx = sizePx,
            foregroundColor = AndroidColor.BLACK,
            backgroundColor = AndroidColor.WHITE
        )
    }.getOrNull()
}
