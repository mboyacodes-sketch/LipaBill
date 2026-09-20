package com.lipabill.app.data.tickets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
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
 * For PDFs we prefer embedded text (airline receipts are text-based). OCR on a
 * rendered page is the fallback — bitmaps are filled white first so ML Kit can read them.
 */
object ETicketAnalyzer {

    private const val TAG = "LipaBill.ETicket"

    suspend fun analyze(
        context: Context,
        uri: Uri,
        kind: TicketDocumentKind = TicketDocumentKind.AUTO
    ): TicketImport = withContext(Dispatchers.IO) {
        val mime = context.contentResolver.getType(uri)?.lowercase().orEmpty()
        val name = displayName(context, uri).orEmpty()
        val isPdf = mime == "application/pdf" ||
            name.endsWith(".pdf", ignoreCase = true) ||
            mime == "application/octet-stream" && name.contains("pdf", ignoreCase = true)

        val pdfText = if (isPdf) extractPdfText(context, uri) else ""
        Log.d(TAG, "kind=$kind pdfTextChars=${pdfText.length} mime=$mime name=$name")

        val bitmaps = loadBitmaps(context, uri, preferPdf = isPdf)
        if (bitmaps.isEmpty() && pdfText.length < 40) {
            throw IllegalArgumentException(
                "Couldn’t open that file. Use a PDF or a clear photo of the ticket."
            )
        }

        try {
            val barcodes = mutableListOf<ETicketTextParser.FoundBarcode>()
            val textParts = mutableListOf<String>()
            val stationHints = mutableListOf<ETicketTextParser.StationHint>()

            if (pdfText.length >= 40) {
                textParts += pdfText
            }

            // Boarding-pass photos: always OCR (+ rotate) even if PDF text exists (rare)
            val forceOcr = kind == TicketDocumentKind.BOARDING_PASS ||
                kind == TicketDocumentKind.SGR_TICKET ||
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
                            val betterCodes = rotCodes.isNotEmpty() &&
                                (pageBarcodes.isEmpty() || kind == TicketDocumentKind.BOARDING_PASS)
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
                }
            }

            val combined = textParts.filter { it.isNotBlank() }.joinToString("\n")
            Log.d(
                TAG,
                "barcodes=${barcodes.size} textChars=${combined.length} hints=${stationHints.size}"
            )
            if (combined.length < 20 && barcodes.isEmpty()) {
                throw IllegalArgumentException(
                    "Couldn’t read text from that file. Try a clearer photo or PDF export."
                )
            }
            try {
                ETicketTextParser.parse(combined, barcodes, stationHints, kind)
            } catch (t: IllegalArgumentException) {
                val msg = t.message.orEmpty()
                if (msg.contains("scannable code", ignoreCase = true) && combined.length > 40) {
                    throw IllegalArgumentException(
                        "Couldn’t find a booking Ref / PNR in that file. " +
                            "If this is a boarding pass, choose “Boarding pass” when adding."
                    )
                }
                throw t
            }
        } finally {
            bitmaps.forEach { it.recycle() }
        }
    }

    private fun extractPdfText(context: Context, uri: Uri): String {
        return try {
            PDFBoxResourceLoader.init(context.applicationContext)
            context.contentResolver.openInputStream(uri)?.use { input ->
                PDDocument.load(input).use { doc ->
                    if (doc.numberOfPages < 1) return@use ""
                    val stripper = PDFTextStripper().apply {
                        startPage = 1
                        endPage = min(doc.numberOfPages, 4)
                        sortByPosition = true
                    }
                    stripper.getText(doc).orEmpty().trim()
                }
            }.orEmpty()
        } catch (t: Throwable) {
            Log.w(TAG, "pdf_text_fail", t)
            ""
        }
    }

    private fun loadBitmaps(context: Context, uri: Uri, preferPdf: Boolean): List<Bitmap> {
        val mime = context.contentResolver.getType(uri)?.lowercase().orEmpty()
        val name = displayName(context, uri)?.lowercase().orEmpty()
        return when {
            preferPdf || mime == "application/pdf" || name.endsWith(".pdf") ->
                renderPdf(context, uri)
            mime.startsWith("image/") ||
                name.endsWith(".jpg") || name.endsWith(".jpeg") ||
                name.endsWith(".png") || name.endsWith(".webp") ||
                name.endsWith(".heic") -> listOfNotNull(decodeImage(context, uri))
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
            Log.w(TAG, "decode_image_fail", t)
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
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(cache).use { output -> input.copyTo(output) }
            } ?: return emptyList()

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
            Log.w(TAG, "pdf_render_fail", t)
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
                val value = b.rawValue?.trim().orEmpty()
                if (value.isEmpty()) return@mapNotNull null
                ETicketTextParser.FoundBarcode(value, b.format.toTicketFormat())
            }
        } catch (t: Throwable) {
            Log.w(TAG, "barcode_fail", t)
            emptyList()
        } finally {
            scanner.close()
        }
    }

    private data class OcrResult(
        val text: String,
        val stationHints: List<ETicketTextParser.StationHint>
    )

    private suspend fun recognizeTextDetailed(bitmap: Bitmap): OcrResult {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val result = recognizer.process(image).await()
            val hints = mutableListOf<ETicketTextParser.StationHint>()
            val stationNames = listOf(
                "Nairobi Terminus", "Mombasa Terminus", "Syokimau", "Athi River", "Emali",
                "Mtito Andei", "Voi", "Miasenyi", "Mariakani", "Suswa", "Naivasha", "Mai Mahiu",
                "Nairobi", "Mombasa"
            )
            for (block in result.textBlocks) {
                val box = block.boundingBox ?: continue
                val blockText = block.text.orEmpty()
                if (blockText.contains("Sold at", ignoreCase = true)) continue
                for (name in stationNames.sortedByDescending { it.length }) {
                    if (blockText.contains(name, ignoreCase = true)) {
                        val normalized = when {
                            name.equals("Nairobi", true) -> "Nairobi Terminus"
                            name.equals("Mombasa", true) -> "Mombasa Terminus"
                            else -> name
                        }
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
            OcrResult(result.text.orEmpty(), hints)
        } catch (t: Throwable) {
            Log.w(TAG, "ocr_fail", t)
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
