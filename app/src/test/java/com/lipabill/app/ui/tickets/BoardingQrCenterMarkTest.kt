package com.lipabill.app.ui.tickets

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The boarding QR now uses the Request QR encoder (EC level H, margin 1) with the
 * LipaBill plate on top. The plate is 56dp on a 248dp card with 12dp padding, so it
 * hides a centred square 56/224 of the code's width. The gate payload must still decode.
 */
class BoardingQrCenterMarkTest {

    private fun decodeWithCenterPlate(payload: String, sizePx: Int = 512): String {
        val matrix = MultiFormatWriter().encode(
            payload,
            BarcodeFormat.QR_CODE,
            sizePx,
            sizePx,
            mapOf(
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.MARGIN to 1,
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H
            )
        )
        val w = matrix.width
        val h = matrix.height
        val plate = (w * 56f / 224f).toInt()
        val x0 = (w - plate) / 2
        val y0 = (h - plate) / 2
        val pixels = IntArray(w * h) { i ->
            val x = i % w
            val y = i / w
            val covered = x in x0 until x0 + plate && y in y0 until y0 + plate
            if (!covered && matrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(w, h, pixels)))
        return QRCodeReader().decode(bitmap).text
    }

    @Test
    fun shortReferenceSurvivesCenterPlate() {
        val payload = "KRCX1Y2Z3"
        assertEquals(payload, decodeWithCenterPlate(payload))
    }

    @Test
    fun longBoardingPayloadSurvivesCenterPlate() {
        val payload = "SGR|E2|NBO-MSA|20260920T0800|C5|S42|KRCX1Y2Z3|TRAVELER NAME|STANDARD|0000123456789"
        assertEquals(payload, decodeWithCenterPlate(payload))
    }
}
