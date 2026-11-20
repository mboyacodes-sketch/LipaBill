package com.lipabill.app.data.tickets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.lipabill.app.metrics.AppLog
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.lipabill.app.data.model.TicketBarcodeFormat
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Reads a PDF or image e-ticket, extracts barcodes + text, and builds a [TicketImport].
 *
 * For PDFs we prefer embedded text. OCR on a rendered page is the fallback —
 * bitmaps are filled white first so ML Kit can read them.
 */
object ETicketAnalyzer {

    private const val TAG = "LipaBill.ETicket"

    /**
     * ML Kit models are unbundled: Play Services fetches them after install. Until then reads
     * fail with UNAVAILABLE, which should not be reported as a blurry photo.
     */
    @Volatile
    private var modelsUnavailable = false

    private fun noteFailure(t: Throwable) {
        if (t is MlKitException && t.errorCode == MlKitException.UNAVAILABLE) modelsUnavailable = true
    }

    /** Text and barcodes read from a ticket file, before any parsing. */
    data class ExtractedTicket(
        val text: String,
        val barcodes: List<ETicketTextParser.FoundBarcode>,
        val stationHints: List<ETicketTextParser.StationHint>,
        /** Positioned lines: PDF text with font sizes, else OCR boxes (pages stacked). */
        val lines: List<TextLine> = emptyList(),
        /** The PDF's own text layer was usable (exact), rather than OCR alone. */
        val fromPdfText: Boolean = false
    )

    suspend fun analyze(
        context: Context,
        uri: Uri,
        kind: TicketDocumentKind = TicketDocumentKind.AUTO,
        /** Stored MIME when [uri] is a decrypted cache file without a content type. */
        mimeType: String? = null
    ): TicketImport = analyzeDetailed(context, uri, kind, mimeType).draft

    /** A parsed draft plus the raw read behind it, so review can weigh each field. */
    data class AnalyzedTicket(
        val draft: TicketImport,
        val read: ExtractedTicket
    ) {
        /** The draft's code was decoded from a pixel, not typed out in the text. */
        val codeFromPixels: Boolean
            get() = read.barcodes.any { it.value == draft.barcodeValue }
    }

    suspend fun analyzeDetailed(
        context: Context,
        uri: Uri,
        kind: TicketDocumentKind = TicketDocumentKind.AUTO,
        mimeType: String? = null
    ): AnalyzedTicket {
        val read = extract(context, uri, kind, mimeType)
        try {
            val draft = ETicketTextParser.parse(read.text, read.barcodes, read.stationHints, kind, read.lines)
            // Carry the real symbology + encoding of the code the draft points at.
            val source = read.barcodes.firstOrNull { it.value == draft.barcodeValue }
                ?: return AnalyzedTicket(draft, read)
            return AnalyzedTicket(draft.copy(barcodeFormat = source.format, barcodeSpec = source.spec), read)
        } catch (t: IllegalArgumentException) {
            val msg = t.message.orEmpty()
            if (msg.contains("scannable code", ignoreCase = true) && read.text.length > 40) {
                throw IllegalArgumentException(
                    "Couldn’t find a booking Ref / PNR in that file. " +
                        "If this is a boarding pass, choose “Boarding pass” when adding."
                )
            }
            throw t
        }
    }

    /** Reads a PDF or image into text and barcodes (OCR and rotation included), without parsing. */
    suspend fun extract(
        context: Context,
        uri: Uri,
        kind: TicketDocumentKind = TicketDocumentKind.AUTO,
        /** Prefer this when [uri] is `file://` (contentResolver often has no type). */
        mimeType: String? = null
    ): ExtractedTicket = withContext(Dispatchers.IO) {
        val mime = mimeType?.lowercase()?.takeIf { it.isNotBlank() }
            ?: context.contentResolver.getType(uri)?.lowercase().orEmpty()
        val name = displayName(context, uri).orEmpty()
        val isPdf = mime == "application/pdf" ||
            name.endsWith(".pdf", ignoreCase = true) ||
            mime == "application/octet-stream" && name.contains("pdf", ignoreCase = true)

        modelsUnavailable = false
        val pdfRead = if (isPdf) extractPdfText(context, uri) else PdfRead("", emptyList())
        val pdfText = pdfRead.text
        // Lengths / kind only — never log file names (can embed passenger or PNR text).
        AppLog.d(TAG, "kind=$kind pdfTextChars=${pdfText.length} mime=$mime")

        val bitmaps = loadBitmaps(context, uri, preferPdf = isPdf, mimeHint = mime)
        if (bitmaps.isEmpty() && pdfText.length < 40) {
            throw IllegalArgumentException(
                "Couldn’t open that file. Use a PDF or a clear photo of the ticket."
            )
        }

        try {
            val barcodes = mutableListOf<ETicketTextParser.FoundBarcode>()
            val textParts = mutableListOf<String>()
            val stationHints = mutableListOf<ETicketTextParser.StationHint>()
            val ocrLines = mutableListOf<TextLine>()
            var pageTop = 0f

            if (pdfText.length >= 40) {
                textParts += pdfText
            }

            // SGR / event photos: always OCR (+ rotate) even if PDF text exists (rare)
            val forceOcr = kind == TicketDocumentKind.SGR_TICKET ||
                kind == TicketDocumentKind.EVENT ||
                pdfText.length < 40

            for (bitmap in bitmaps) {
                var pageBarcodes = scanBarcodes(bitmap)
                var pageOcr = if (forceOcr) recognizeTextDetailed(bitmap) else null

                if (forceOcr &&
                    (pageBarcodes.isEmpty() || pageOcr?.text?.let { !looksReadableTicket(it) } == true)
                ) {
                    for (degrees in listOf(90f, 270f, 180f)) {
                        val rotated = rotateDegrees(bitmap, degrees) ?: continue
                        try {
                            val rotCodes = scanBarcodes(rotated)
                            val rotOcr = recognizeTextDetailed(rotated)
                            val betterCodes = rotCodes.isNotEmpty() && pageBarcodes.isEmpty()
                            val betterText = (rotOcr.text.length > (pageOcr?.text?.length ?: 0)) ||
                                looksReadableTicket(rotOcr.text)
                            if (betterCodes || betterText) {
                                if (rotCodes.isNotEmpty()) pageBarcodes = rotCodes
                                if (betterText) pageOcr = rotOcr
                            }
                            if (pageBarcodes.isNotEmpty() &&
                                pageOcr?.text?.let { looksReadableTicket(it) } == true
                            ) {
                                break
                            }
                        } finally {
                            if (rotated != bitmap) rotated.recycle()
                        }
                    }
                }

                barcodes += pageBarcodes
                pageOcr?.let {
                    textParts += it.text
                    stationHints += it.stationHints
                    ocrLines += it.lines.map { l -> l.copy(top = l.top + pageTop, bottom = l.bottom + pageTop) }
                }
                pageTop += bitmap.height + 40f
            }

            val combined = textParts.filter { it.isNotBlank() }.joinToString("\n")
            // Lengths only — never log OCR body (names, PNRs, seats).
            AppLog.d(
                TAG,
                "barcodes=${barcodes.size} textChars=${combined.length} hints=${stationHints.size}"
            )
            if (combined.length < 20 && barcodes.isEmpty()) {
                throw IllegalArgumentException(
                    if (modelsUnavailable) {
                        "The ticket reader is still being set up on this phone. Connect to the " +
                            "internet once, then try again. PDF tickets with text work now."
                    } else {
                        "Couldn’t read text from that file. Try a clearer photo or PDF export."
                    }
                )
            }
            // PDF text layer is exact; fall back to OCR boxes for photos / scanned PDFs.
            val lines = pdfRead.lines.takeIf { pdfText.length >= 40 && it.isNotEmpty() } ?: ocrLines
            ExtractedTicket(combined, barcodes, stationHints, lines, fromPdfText = pdfText.length >= 40)
        } finally {
            bitmaps.forEach { it.recycle() }
        }
    }

    private data class PdfRead(val text: String, val lines: List<TextLine>)

    private fun extractPdfText(context: Context, uri: Uri): PdfRead {
        return try {
            PDFBoxResourceLoader.init(context.applicationContext)
            context.contentResolver.openInputStream(uri)?.use { input ->
                PDDocument.load(input).use { doc ->
                    if (doc.numberOfPages < 1) return@use PdfRead("", emptyList())
                    val stripper = PDFTextStripper().apply {
                        startPage = 1
                        endPage = min(doc.numberOfPages, 4)
                        sortByPosition = true
                    }
                    val text = stripper.getText(doc).orEmpty().trim()
                    val lines = runCatching { PdfTextLines.read(doc) }
                        .onFailure { AppLog.w(TAG, "pdf_lines_fail", it) }
                        .getOrDefault(emptyList())
                    PdfRead(text, lines)
                }
            } ?: PdfRead("", emptyList())
        } catch (t: Throwable) {
            AppLog.w(TAG, "pdf_text_fail", t)
            PdfRead("", emptyList())
        }
    }

    private fun loadBitmaps(
        context: Context,
        uri: Uri,
        preferPdf: Boolean,
        mimeHint: String = ""
    ): List<Bitmap> {
        val mime = mimeHint.ifBlank {
            context.contentResolver.getType(uri)?.lowercase().orEmpty()
        }
        val name = displayName(context, uri)?.lowercase().orEmpty()
        return when {
            preferPdf || mime == "application/pdf" || name.endsWith(".pdf") ->
                renderPdf(context, uri)
            mime.startsWith("image/") ||
                name.endsWith(".jpg") || name.endsWith(".jpeg") ||
                name.endsWith(".png") || name.endsWith(".webp") ||
                name.endsWith(".heic") || name.endsWith(".img") -> listOfNotNull(decodeImage(context, uri))
            else -> {
                decodeImage(context, uri)?.let { listOf(it) }
                    ?: renderPdf(context, uri)
            }
        }
    }

    private fun displayName(context: Context, uri: Uri): String? {
        if (uri.scheme == "content") {
            context.contentResolver.query(
                uri,
                arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) return cursor.getString(idx)
                }
            }
        }
        return uri.lastPathSegment
    }

    private fun looksReadableTicket(text: String): Boolean {
        val lower = text.lowercase(Locale.getDefault())
        return lower.contains("boarding") ||
            lower.contains("flight") ||
            lower.contains("madaraka") ||
            lower.contains("booking") ||
            lower.contains("seat") && (lower.contains("gate") || lower.contains("coach"))
    }

    private fun rotateDegrees(src: Bitmap, degrees: Float): Bitmap? {
        return try {
            val matrix = android.graphics.Matrix().apply { postRotate(degrees) }
            Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
        } catch (_: Throwable) {
            null
        }
    }

    private fun decodeImage(context: Context, uri: Uri): Bitmap? {
        return try {
            val exifOrientation = context.contentResolver.openInputStream(uri)?.use { input ->
                androidx.exifinterface.media.ExifInterface(input)
                    .getAttributeInt(
                        androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                        androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
                    )
            } ?: androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL

            val raw = context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input)
            } ?: return null

            val rotated = rotateBitmapForExif(raw, exifOrientation)
            if (rotated != raw) raw.recycle()
            scaleDown(rotated, 2200)
        } catch (t: Throwable) {
            AppLog.w(TAG, "decode_image_fail", t)
            null
        }
    }

    private fun rotateBitmapForExif(src: Bitmap, orientation: Int): Bitmap {
        val degrees = when (orientation) {
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return src
        }
        val matrix = android.graphics.Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }

    private fun scaleDown(src: Bitmap, maxSide: Int): Bitmap {
        val largest = maxOf(src.width, src.height)
        if (largest <= maxSide) return src
        val scale = maxSide.toFloat() / largest
        val w = (src.width * scale).toInt().coerceAtLeast(1)
        val h = (src.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(src, w, h, true)
        if (scaled != src) src.recycle()
        return scaled
    }

    private fun renderPdf(context: Context, uri: Uri): List<Bitmap> {
        val cache = File(context.cacheDir, "eticket-${System.currentTimeMillis()}.pdf")
        try {
            val wrote = context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(cache).use { output ->
                    copyAtMost(input, output, MAX_SHARED_TICKET_BYTES)
                }
            } ?: false
            if (!wrote) {
                cache.delete()
                return emptyList()
            }

            ParcelFileDescriptor.open(cache, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val pageCount = min(renderer.pageCount, 3)
                    val pages = mutableListOf<Bitmap>()
                    for (i in 0 until pageCount) {
                        renderer.openPage(i).use { page ->
                            // ~180 dpi — enough for OCR / barcode when text extract fails
                            val scale = 2.5f
                            val w = (page.width * scale).toInt().coerceAtLeast(1)
                            val h = (page.height * scale).toInt().coerceAtLeast(1)
                            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            // PdfRenderer draws on transparent — white so OCR can read ink
                            bitmap.eraseColor(Color.WHITE)
                            page.render(
                                bitmap,
                                null,
                                null,
                                PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                            )
                            pages += scaleDown(bitmap, 2400)
                        }
                    }
                    return pages
                }
            }
        } catch (t: Throwable) {
            AppLog.w(TAG, "pdf_render_fail", t)
            return emptyList()
        } finally {
            cache.delete()
        }
    }

    private suspend fun scanBarcodes(bitmap: Bitmap): List<ETicketTextParser.FoundBarcode> {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_QR_CODE,
                Barcode.FORMAT_PDF417,
                Barcode.FORMAT_AZTEC,
                Barcode.FORMAT_DATA_MATRIX,
                Barcode.FORMAT_CODE_128,
                Barcode.FORMAT_CODE_39
            )
            .build()
        val scanner = BarcodeScanning.getClient(options)
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val results = scanner.process(image).await()
            results.mapNotNull { b ->
                val value = b.rawValue.orEmpty()
                if (value.isBlank()) return@mapNotNull null
                val format = b.format.toTicketFormat()
                val spec = if (format == TicketBarcodeFormat.QR_CODE ||
                    format == TicketBarcodeFormat.PDF_417 ||
                    format == TicketBarcodeFormat.AZTEC
                ) {
                    readSpec(bitmap, b.boundingBox, value)
                } else {
                    null
                }
                ETicketTextParser.FoundBarcode(value, format, spec)
            }
        } catch (t: Throwable) {
            AppLog.w(TAG, "barcode_fail", t)
            noteFailure(t)
            emptyList()
        } finally {
            scanner.close()
        }
    }

    /** zxing pass over the code ML Kit found, cropped with margin, for EC level / version. */
    private fun readSpec(bitmap: Bitmap, box: Rect?, value: String): BarcodeSpec? = try {
        val region = box?.let {
            val pad = (maxOf(it.width(), it.height()) * 0.2f).toInt()
            Rect(
                (it.left - pad).coerceAtLeast(0),
                (it.top - pad).coerceAtLeast(0),
                (it.right + pad).coerceAtMost(bitmap.width),
                (it.bottom + pad).coerceAtMost(bitmap.height)
            ).takeIf { r -> r.width() > 8 && r.height() > 8 }
        }
        fun inspect(src: Bitmap): BarcodeSpec? {
            val px = IntArray(src.width * src.height)
            src.getPixels(px, 0, src.width, 0, 0, src.width, src.height)
            return BarcodeSpecReader.fromArgb(px, src.width, src.height, value)
        }
        val cropped = region?.let { Bitmap.createBitmap(bitmap, it.left, it.top, it.width(), it.height()) }
        try {
            cropped?.let(::inspect) ?: inspect(bitmap)
        } finally {
            if (cropped != null && cropped != bitmap) cropped.recycle()
        }
    } catch (t: Throwable) {
        AppLog.w(TAG, "barcode_spec_fail", t)
        null
    }

    private data class OcrResult(
        val text: String,
        val stationHints: List<ETicketTextParser.StationHint>,
        val lines: List<TextLine> = emptyList()
    )

    private suspend fun recognizeTextDetailed(bitmap: Bitmap): OcrResult {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val result = recognizer.process(image).await()
            val hints = mutableListOf<ETicketTextParser.StationHint>()
            val stationNames = SGR_STATIONS
            for (block in result.textBlocks) {
                val box = block.boundingBox ?: continue
                val blockText = block.text.orEmpty()
                if (blockText.contains("Sold at", ignoreCase = true)) continue
                for (name in stationNames.sortedByDescending { it.length }) {
                    if (blockText.contains(name, ignoreCase = true)) {
                        val normalized = sgrStationName(name)
                        val lineBox = block.lines
                            .firstOrNull {
                                it.text.contains(name, ignoreCase = true) &&
                                    !it.text.contains("Sold at", ignoreCase = true)
                            }
                            ?.boundingBox
                        val x = (lineBox ?: box).centerX()
                        if (hints.none { it.name == normalized }) {
                            hints += ETicketTextParser.StationHint(normalized, x)
                        }
                        break
                    }
                }
            }
            // Every OCR line with its box; box height stands in for font size.
            val lines = result.textBlocks.flatMap { block ->
                block.lines.mapNotNull { line ->
                    val b = line.boundingBox ?: return@mapNotNull null
                    TextLine(
                        line.text,
                        b.left.toFloat(),
                        b.top.toFloat(),
                        b.right.toFloat(),
                        b.bottom.toFloat(),
                        b.height().toFloat()
                    )
                }
            }
            OcrResult(result.text.orEmpty(), hints, lines)
        } catch (t: Throwable) {
            AppLog.w(TAG, "ocr_fail", t)
            noteFailure(t)
            OcrResult("", emptyList())
        } finally {
            recognizer.close()
        }
    }

    private fun Int.toTicketFormat(): TicketBarcodeFormat = when (this) {
        Barcode.FORMAT_QR_CODE -> TicketBarcodeFormat.QR_CODE
        Barcode.FORMAT_PDF417 -> TicketBarcodeFormat.PDF_417
        Barcode.FORMAT_AZTEC -> TicketBarcodeFormat.AZTEC
        Barcode.FORMAT_CODE_128, Barcode.FORMAT_CODE_39 -> TicketBarcodeFormat.CODE_128
        else -> TicketBarcodeFormat.OTHER
    }
}
