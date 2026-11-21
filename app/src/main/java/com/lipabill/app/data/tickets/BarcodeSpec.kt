package com.lipabill.app.data.tickets

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.MultiFormatWriter
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.Result
import com.google.zxing.ResultMetadataType
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.multi.GenericMultipleBarcodeReader
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.decoder.Version
import com.google.zxing.qrcode.encoder.Encoder
import com.lipabill.app.data.model.TicketBarcodeFormat

/**
 * How the operator's code was actually encoded, read off the original pass at import.
 *
 * Gate scanners only care about the decoded content, but re-rendering with the same
 * symbology, error-correction level, symbol version and charset keeps our copy as close to
 * the printed one as zxing allows (same module grid size and density; the mask may differ).
 *
 * Stored as a short `key=value;…` string so new keys can be added without a migration.
 */
data class BarcodeSpec(
    val format: TicketBarcodeFormat,
    /** QR: L / M / Q / H. PDF417: level digit. Aztec: EC percent. Null when unknown. */
    val ecLevel: String? = null,
    /** QR symbol version 1–40 (module grid = 17 + 4·version). */
    val version: Int? = null,
    /** Byte-mode charset that reproduces the payload bytes: "UTF-8" or "ISO-8859-1". */
    val charset: String? = null
) {
    /** QR at Q or H tolerates the centre LipaBill plate; L / M must stay clean. */
    val allowsCenterMark: Boolean
        get() = format == TicketBarcodeFormat.QR_CODE && (ecLevel == "Q" || ecLevel == "H")

    fun serialize(): String = buildList {
        add("f=${format.name}")
        ecLevel?.let { add("ec=$it") }
        version?.let { add("v=$it") }
        charset?.let { add("cs=$it") }
    }.joinToString(";")

    companion object {
        fun parse(raw: String?): BarcodeSpec? {
            if (raw.isNullOrBlank()) return null
            val map = raw.split(';').mapNotNull { part ->
                val i = part.indexOf('=')
                if (i <= 0) null else part.substring(0, i).trim() to part.substring(i + 1).trim()
            }.toMap()
            val format = map["f"]?.let { f ->
                TicketBarcodeFormat.entries.firstOrNull { it.name == f }
            } ?: return null
            return BarcodeSpec(
                format = format,
                ecLevel = map["ec"]?.takeIf { it.isNotEmpty() },
                version = map["v"]?.toIntOrNull()?.takeIf { it in 1..40 },
                charset = map["cs"]?.takeIf { it == "UTF-8" || it == "ISO-8859-1" }
            )
        }
    }
}

/** Reads a [BarcodeSpec] from pixels with zxing (ML Kit does not expose EC level or version). */
object BarcodeSpecReader {

    private val readableFormats = listOf(
        BarcodeFormat.QR_CODE,
        BarcodeFormat.PDF_417,
        BarcodeFormat.AZTEC,
        BarcodeFormat.DATA_MATRIX,
        BarcodeFormat.CODE_128,
        BarcodeFormat.CODE_39
    )

    /** ARGB pixels, e.g. from `Bitmap.getPixels`. */
    fun fromArgb(pixels: IntArray, width: Int, height: Int, expectedText: String?): BarcodeSpec? =
        inspect(RGBLuminanceSource(width, height, pixels), expectedText)

    /** Camera Y plane (luminance). [rowStride] may exceed [width]. */
    fun fromLuminance(
        y: ByteArray,
        rowStride: Int,
        width: Int,
        height: Int,
        expectedText: String?
    ): BarcodeSpec? = inspect(
        PlanarYUVLuminanceSource(y, rowStride, height, 0, 0, width, height, false),
        expectedText
    )

    fun inspect(source: LuminanceSource, expectedText: String?): BarcodeSpec? {
        val hints = mapOf(
            DecodeHintType.POSSIBLE_FORMATS to readableFormats,
            DecodeHintType.TRY_HARDER to true
        )
        val results = buildList {
            for (bitmap in listOf(
                BinaryBitmap(HybridBinarizer(source)),
                BinaryBitmap(HybridBinarizer(source.invert()))
            )) {
                val found = runCatching {
                    GenericMultipleBarcodeReader(MultiFormatReader())
                        .decodeMultiple(bitmap, hints).toList()
                }.getOrDefault(emptyList())
                addAll(found)
                if (found.isNotEmpty()) break
            }
        }
        val match = results.firstOrNull { expectedText == null || it.text == expectedText }
            ?: results.firstOrNull { expectedText != null && it.text.trim() == expectedText.trim() }
            ?: return null
        return specOf(match)
    }

    internal fun specOf(result: Result): BarcodeSpec? {
        val format = when (result.barcodeFormat) {
            BarcodeFormat.QR_CODE -> TicketBarcodeFormat.QR_CODE
            BarcodeFormat.PDF_417 -> TicketBarcodeFormat.PDF_417
            BarcodeFormat.AZTEC -> TicketBarcodeFormat.AZTEC
            BarcodeFormat.DATA_MATRIX -> TicketBarcodeFormat.DATA_MATRIX
            BarcodeFormat.CODE_128 -> TicketBarcodeFormat.CODE_128
            BarcodeFormat.CODE_39 -> TicketBarcodeFormat.CODE_39
            else -> return null
        }
        val ec = result.resultMetadata?.get(ResultMetadataType.ERROR_CORRECTION_LEVEL)?.toString()
        val version = if (format == TicketBarcodeFormat.QR_CODE) {
            qrVersion(result.rawBytes?.size, ec)
        } else {
            null
        }
        return BarcodeSpec(
            format = format,
            ecLevel = ec,
            version = version,
            charset = byteCharset(result)
        )
    }

    /** Data-codeword count + EC level pins the QR version uniquely. */
    internal fun qrVersion(dataCodewords: Int?, ec: String?): Int? {
        if (dataCodewords == null || ec == null) return null
        val level = runCatching { ErrorCorrectionLevel.valueOf(ec) }.getOrNull() ?: return null
        return (1..40).firstOrNull { n ->
            val v = Version.getVersionForNumber(n)
            v.totalCodewords - v.getECBlocksForLevel(level).totalECCodewords == dataCodewords
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun byteCharset(result: Result): String? {
        val segments = result.resultMetadata?.get(ResultMetadataType.BYTE_SEGMENTS) as? List<ByteArray>
            ?: return null
        val bytes = segments.fold(ByteArray(0)) { acc, b -> acc + b }
        return when {
            bytes.all { it >= 0 } -> null // plain ASCII: any charset reproduces it
            String(bytes, Charsets.UTF_8) == result.text -> "UTF-8"
            String(bytes, Charsets.ISO_8859_1) == result.text -> "ISO-8859-1"
            else -> null
        }
    }
}

/** Re-encodes a gate code following the [BarcodeSpec] captured from the original. */
object BarcodeSpecEncoder {

    /** Request-QR defaults, used when no spec was captured (passes added before this). */
    private val legacyQr = BarcodeSpec(TicketBarcodeFormat.QR_CODE, ecLevel = "H")

    fun effectiveSpec(spec: BarcodeSpec?, fallbackFormat: TicketBarcodeFormat?): BarcodeSpec =
        when {
            spec != null -> spec
            fallbackFormat == null ||
                fallbackFormat == TicketBarcodeFormat.QR_CODE ||
                fallbackFormat == TicketBarcodeFormat.OTHER ||
                fallbackFormat == TicketBarcodeFormat.TEXT -> legacyQr
            else -> BarcodeSpec(fallbackFormat)
        }

    /**
     * Display density: the drawn QR uses at least this many more versions than the
     * original (4 extra modules per side each), and never fewer than [MinDisplayVersion].
     * Extra capacity is filled with standard pad codewords, so the decoded content and
     * EC level are unchanged.
     */
    const val DensityBoostVersions = 2
    const val MinDisplayVersion = 6

    /** Cap so modules stay ≥ ~2.9dp on the 224dp drawable area (v15 = 77 modules). */
    const val MaxDisplayVersion = 15

    /** Smallest version that fits [value] at [spec]'s EC level and charset. */
    fun naturalVersion(value: String, spec: BarcodeSpec): Int? = runCatching {
        Encoder.encode(
            value,
            levelOf(spec),
            mapOf(EncodeHintType.CHARACTER_SET to (spec.charset ?: "ISO-8859-1"))
        ).version.versionNumber
    }.getOrNull()

    /** The version actually drawn: original (or natural) + boost, within the display bounds. */
    fun displayVersion(value: String, spec: BarcodeSpec): Int? {
        val base = maxOf(spec.version ?: 0, naturalVersion(value, spec) ?: return spec.version)
        if (base >= MaxDisplayVersion) return base
        return (base + DensityBoostVersions).coerceAtLeast(MinDisplayVersion).coerceAtMost(MaxDisplayVersion)
    }

    private fun levelOf(spec: BarcodeSpec): ErrorCorrectionLevel = spec.ecLevel
        ?.let { runCatching { ErrorCorrectionLevel.valueOf(it) }.getOrNull() }
        ?: ErrorCorrectionLevel.H

    /** Square QR [BitMatrix] for [value]; null for non-QR specs (use the ticket barcode encoder). */
    fun encodeQr(value: String, spec: BarcodeSpec, sizePx: Int = 512): BitMatrix? {
        if (value.isEmpty() || spec.format != TicketBarcodeFormat.QR_CODE) return null
        val level = levelOf(spec)
        val charset = spec.charset ?: "ISO-8859-1"
        val target = displayVersion(value, spec)
        fun attempt(version: Int?, cs: String): BitMatrix? = runCatching {
            val hints = buildMap<EncodeHintType, Any> {
                put(EncodeHintType.ERROR_CORRECTION, level)
                put(EncodeHintType.CHARACTER_SET, cs)
                put(EncodeHintType.MARGIN, 1)
                if (version != null) put(EncodeHintType.QR_VERSION, version)
            }
            MultiFormatWriter().encode(value, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        }.getOrNull()
        return attempt(target, charset)
            ?: attempt(spec.version, charset)
            ?: attempt(null, charset)
            ?: attempt(null, "UTF-8")
    }
}
