package com.lipabill.app.data.tickets

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.lipabill.app.data.model.TicketBarcodeFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BarcodeSpecTest {

    private val sgrLike = "0908100299823"
    private val bcbpLike = "M1MWANGI/JOHN K       EKRCX1Y NBOMSAE2  0263 020Y042  1"

    /** Simulates the operator's printed code: zxing with chosen EC / version, then a photo-ish pixel grid. */
    private fun operatorCode(text: String, ec: ErrorCorrectionLevel, version: Int? = null, cs: String = "ISO-8859-1"): BitMatrix =
        MultiFormatWriter().encode(
            text, BarcodeFormat.QR_CODE, 420, 420,
            buildMap {
                put(EncodeHintType.ERROR_CORRECTION, ec)
                put(EncodeHintType.CHARACTER_SET, cs)
                put(EncodeHintType.MARGIN, 4)
                if (version != null) put(EncodeHintType.QR_VERSION, version)
            }
        )

    /**
     * Rasterises [m] like the on-screen card: the code fills 224dp with 12dp of white card
     * padding on every side (pad = 12/224 of the code width), plus the optional centre plate.
     */
    private fun pixels(m: BitMatrix, plateFraction: Float = 0f): IntArray {
        val pad = m.width * 12 / 224
        val w = m.width + pad * 2
        val h = m.height + pad * 2
        val plate = (m.width * plateFraction).toInt()
        val x0 = (w - plate) / 2
        val y0 = (h - plate) / 2
        return IntArray(w * h) { i ->
            val x = i % w
            val y = i / w
            val covered = plate > 0 && x in x0 until x0 + plate && y in y0 until y0 + plate
            val inCode = x >= pad && y >= pad && x < pad + m.width && y < pad + m.height
            if (!covered && inCode && m.get(x - pad, y - pad)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
    }

    private fun paddedSize(m: BitMatrix) = m.width + (m.width * 12 / 224) * 2

    private fun read(m: BitMatrix, expected: String?) =
        BarcodeSpecReader.fromArgb(pixels(m), paddedSize(m), paddedSize(m), expected)

    /**
     * Camera-style detection first; zxing's finder search can miss a valid synthetic code at
     * some pixel scales, so fall back to its clean-image (PURE_BARCODE) decode, which still
     * runs full Reed–Solomon correction over the plate-damaged modules.
     */
    private fun decodeText(m: BitMatrix, plateFraction: Float = 0f): String {
        fun bitmap() = BinaryBitmap(
            HybridBinarizer(RGBLuminanceSource(paddedSize(m), paddedSize(m), pixels(m, plateFraction)))
        )
        return runCatching {
            QRCodeReader().decode(bitmap(), mapOf(DecodeHintType.TRY_HARDER to true)).text
        }.getOrElse {
            QRCodeReader().decode(bitmap(), mapOf(DecodeHintType.PURE_BARCODE to true)).text
        }
    }

    @Test
    fun readsEcLevelAndVersionFromOperatorCode() {
        for (ec in ErrorCorrectionLevel.entries) {
            val spec = read(operatorCode(sgrLike, ec, version = 3), sgrLike)
            assertNotNull("ec=$ec", spec)
            assertEquals(TicketBarcodeFormat.QR_CODE, spec!!.format)
            assertEquals(ec.name, spec.ecLevel)
            assertEquals(3, spec.version)
        }
    }

    @Test
    fun reEncodeMatchesOriginalGridAndContent() {
        for (ec in ErrorCorrectionLevel.entries) {
            val original = operatorCode(bcbpLike, ec)
            val spec = read(original, bcbpLike)!!
            val copy = BarcodeSpecEncoder.encodeQr(bcbpLike, spec)!!
            assertEquals("ec=$ec content", bcbpLike, decodeText(copy))
            val again = read(copy, bcbpLike)!!
            assertEquals("ec=$ec", spec.ecLevel, again.ecLevel)
            assertEquals("ec=$ec", BarcodeSpecEncoder.displayVersion(bcbpLike, spec), again.version)
            assertTrue("ec=$ec denser", again.version!! > spec.version!!)
        }
    }

    @Test
    fun keepsLeadingAndTrailingSpaces() {
        val padded = "  KRC 0299823   "
        val spec = read(operatorCode(padded, ErrorCorrectionLevel.M), padded)!!
        val copy = BarcodeSpecEncoder.encodeQr(padded, spec)!!
        assertEquals(padded, decodeText(copy))
    }

    @Test
    fun utf8PayloadKeepsCharset() {
        val text = "Abiria: Wanjikũ · Nairobi→Mombasa"
        val spec = read(operatorCode(text, ErrorCorrectionLevel.M, cs = "UTF-8"), text)!!
        assertEquals("UTF-8", spec.charset)
        assertEquals(text, decodeText(BarcodeSpecEncoder.encodeQr(text, spec)!!))
    }

    @Test
    fun picksTheExpectedCodeWhenTextDiffers() {
        assertNull(read(operatorCode(sgrLike, ErrorCorrectionLevel.L), "something-else"))
    }

    /** The LipaBill plate hides a centred square ~25% of the code's width. */
    @Test
    fun centerMarkOnlyWhereItStillScans() {
        for (ec in listOf(ErrorCorrectionLevel.Q, ErrorCorrectionLevel.H)) {
            for (text in listOf(sgrLike, bcbpLike)) {
                val spec = BarcodeSpec(TicketBarcodeFormat.QR_CODE, ecLevel = ec.name)
                assertTrue(spec.allowsCenterMark)
                val m = BarcodeSpecEncoder.encodeQr(text, spec)!!
                assertEquals("ec=$ec", text, decodeText(m, plateFraction = 56f / 224f))
            }
        }
        assertFalse(BarcodeSpec(TicketBarcodeFormat.QR_CODE, ecLevel = "L").allowsCenterMark)
        assertFalse(BarcodeSpec(TicketBarcodeFormat.QR_CODE, ecLevel = "M").allowsCenterMark)
        assertFalse(BarcodeSpec(TicketBarcodeFormat.PDF_417, ecLevel = "5").allowsCenterMark)
    }

    @Test
    fun serializeRoundTripAndTolerateJunk() {
        val spec = BarcodeSpec(TicketBarcodeFormat.QR_CODE, "Q", 7, "UTF-8")
        assertEquals(spec, BarcodeSpec.parse(spec.serialize()))
        assertEquals(BarcodeSpec(TicketBarcodeFormat.AZTEC, "23"), BarcodeSpec.parse("f=AZTEC;ec=23;future=1"))
        assertNull(BarcodeSpec.parse("ec=M"))
        assertNull(BarcodeSpec.parse(""))
        assertNull(BarcodeSpec.parse("f=QR_CODE;v=99")!!.version)
    }

    @Test
    fun legacyPassesKeepRequestStyle() {
        val legacy = BarcodeSpecEncoder.effectiveSpec(null, TicketBarcodeFormat.QR_CODE)
        assertEquals("H", legacy.ecLevel)
        assertTrue(legacy.allowsCenterMark)
        assertEquals(
            TicketBarcodeFormat.PDF_417,
            BarcodeSpecEncoder.effectiveSpec(null, TicketBarcodeFormat.PDF_417).format
        )
    }

    @Test
    fun denserDisplayStillDecodesAtEveryLevel() {
        val payloads = listOf("KRC1", sgrLike, bcbpLike, bcbpLike + bcbpLike + bcbpLike)
        val failures = mutableListOf<String>()
        for (ec in ErrorCorrectionLevel.entries) {
            for (text in payloads) {
                val spec = BarcodeSpec(TicketBarcodeFormat.QR_CODE, ecLevel = ec.name)
                val natural = BarcodeSpecEncoder.naturalVersion(text, spec)!!
                val shown = BarcodeSpecEncoder.displayVersion(text, spec)!!
                assertTrue("ec=$ec len=${text.length} $shown > $natural", shown > natural || natural >= 15)
                assertTrue(shown >= BarcodeSpecEncoder.MinDisplayVersion)
                val m = BarcodeSpecEncoder.encodeQr(text, spec)!!
                val label = "ec=$ec len=${text.length} v=$shown"
                runCatching { decodeText(m) }.onFailure { failures += "$label plain" }
                    .onSuccess { if (it != text) failures += "$label plain-mismatch" }
                val readBack = read(m, text)
                if (readBack != null && readBack.version != shown) failures += "$label version"
                if (spec.allowsCenterMark) {
                    runCatching { decodeText(m, plateFraction = 56f / 224f) }
                        .onFailure { failures += "$label mark" }
                }
            }
        }
        assertTrue(failures.joinToString(), failures.isEmpty())
    }

    @Test
    fun capturedVersionIsAFloorForDensity() {
        val spec = BarcodeSpec(TicketBarcodeFormat.QR_CODE, ecLevel = "M", version = 9)
        assertEquals(11, BarcodeSpecEncoder.displayVersion(sgrLike, spec))
        val big = BarcodeSpec(TicketBarcodeFormat.QR_CODE, ecLevel = "M", version = 20)
        assertEquals(20, BarcodeSpecEncoder.displayVersion(sgrLike, big))
    }
}
