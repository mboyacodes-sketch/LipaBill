package com.lipabill.app.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.net.Uri
import androidx.room.withTransaction
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import com.lipabill.app.data.local.AppDatabase
import com.lipabill.app.data.local.entity.DocumentExtractionEntity
import com.lipabill.app.data.local.entity.PassDocumentEntity
import com.lipabill.app.data.tickets.intelligence.DocumentRules
import com.lipabill.app.data.tickets.intelligence.PassExtraction
import com.lipabill.app.metrics.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/**
 * Keeps the original ticket file, encrypted under app-private storage, and what was read from it.
 * The file is written before any row, and removed again if the row can't be saved, so a crash
 * leaves at worst an orphan file (swept by [pruneDrafts]), never a row without its file.
 */
class PassDocumentRepository(
    private val context: Context,
    private val db: AppDatabase
) {
    private val dao = db.passDocumentDao()
    private val dir: File get() = File(context.filesDir, DIR).apply { mkdirs() }

    data class Stored(
        val document: PassDocumentEntity,
        /** Same bytes were imported before. [document] is that earlier copy. */
        val duplicate: Boolean
    )

    /** Copies [uri] in. Throws [IllegalArgumentException] with a user-facing reason when it can't. */
    suspend fun store(uri: Uri, nowMillis: Long = System.currentTimeMillis()): Stored =
        withContext(Dispatchers.IO) {
            val bytes = readCapped(uri)
            val mime = DocumentRules.sniffMime(bytes)
                ?: throw IllegalArgumentException("Use a PDF or a photo of the ticket.")
            val sha = sha256(bytes)
            dao.getBySha256(sha)?.let { return@withContext Stored(it, duplicate = true) }

            val id = UUID.randomUUID().toString()
            val storedName = "$id.bin"
            val file = File(dir, storedName)
            encrypted(file).openFileOutput().use { it.write(bytes) }
            val row = PassDocumentEntity(
                id = id,
                storedName = storedName,
                mimeType = mime,
                fileSizeBytes = bytes.size.toLong(),
                sha256 = sha,
                importedAtMillis = nowMillis
            )
            try {
                dao.insert(row)
            } catch (t: Throwable) {
                file.delete()
                throw t
            }
            // Sizes and type only — never names or contents.
            AppLog.d(TAG, "stored mime=$mime bytes=${bytes.size}")
            Stored(row, duplicate = false)
        }

    suspend fun recordExtraction(
        documentId: String,
        extraction: PassExtraction,
        nowMillis: Long = System.currentTimeMillis()
    ) = withContext(Dispatchers.IO) {
        dao.insertExtraction(extraction.toEntity(documentId, nowMillis))
    }

    suspend fun latestExtraction(documentId: String): PassExtraction? = withContext(Dispatchers.IO) {
        val row = dao.latestExtraction(documentId) ?: return@withContext null
        // Corrupt / future provenance → null; review falls back to the ticket draft.
        PassExtraction.fromJson(row.fieldsJson)
            ?: run {
                AppLog.w(TAG, "extraction_json_unreadable chars=${row.fieldsJson.length}")
                null
            }
    }

    suspend fun get(documentId: String): PassDocumentEntity? = withContext(Dispatchers.IO) {
        dao.getById(documentId)
    }

    /**
     * Saves a pass and links its document and reading in one transaction: either all three
     * land or none do. [save] returns the pass id, or null when nothing was saved.
     */
    suspend fun saveWithDocument(
        documentId: String?,
        extraction: PassExtraction?,
        nowMillis: Long = System.currentTimeMillis(),
        save: suspend () -> Long?
    ): Long? = db.withTransaction {
        val ticketId = save() ?: return@withTransaction null
        if (documentId != null && dao.getById(documentId) != null) {
            dao.linkToTicket(documentId, ticketId)
            extraction?.let { dao.insertExtraction(it.toEntity(documentId, nowMillis)) }
        }
        ticketId
    }

    /** Ties a stored file to a pass saved outside [saveWithDocument] (e.g. an attached boarding pass). */
    suspend fun linkToTicket(documentId: String, ticketId: Long) = withContext(Dispatchers.IO) {
        dao.linkToTicket(documentId, ticketId)
    }

    /** First page or the photo itself, scaled to [maxWidth]. Null when it can't be drawn. */
    suspend fun thumbnail(documentId: String, maxWidth: Int): Bitmap? = withContext(Dispatchers.IO) {
        val row = dao.getById(documentId) ?: return@withContext null
        val copy = openCopy(documentId) ?: return@withContext null
        try {
            if (DocumentRules.isPdf(row.mimeType)) renderFirstPage(copy, maxWidth) else decodeScaled(copy, maxWidth)
        } catch (t: Throwable) {
            AppLog.w(TAG, "thumbnail_fail", t)
            null
        } finally {
            copy.delete()
        }
    }

    private fun renderFirstPage(file: File, maxWidth: Int): Bitmap? =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { pdf ->
                if (pdf.pageCount < 1) return null
                pdf.openPage(0).use { page ->
                    val width = maxWidth.coerceAtMost(page.width * 2)
                    val height = (width.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bmp ->
                        bmp.eraseColor(android.graphics.Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                }
            }
        }

    private fun decodeScaled(file: File, maxWidth: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxWidth) sample *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /** Decrypted copy in the cache, for preview or a re-read. Caller deletes it when done. */
    suspend fun openCopy(documentId: String): File? = withContext(Dispatchers.IO) {
        val row = dao.getById(documentId) ?: return@withContext null
        val source = File(dir, row.storedName).takeIf { it.exists() } ?: return@withContext null
        val ext = if (DocumentRules.isPdf(row.mimeType)) ".pdf" else ".img"
        val out = File(File(context.cacheDir, CACHE_DIR).apply { mkdirs() }, "${row.id}$ext")
        encrypted(source).openFileInput().use { input -> out.outputStream().use { input.copyTo(it) } }
        out
    }

    /** First stored original for a pass, or null. */
    suspend fun firstForTicket(ticketId: Long): PassDocumentEntity? = withContext(Dispatchers.IO) {
        dao.forTicket(ticketId).firstOrNull()
    }

    /**
     * Decrypted cache file + MIME for a stored original. Caller deletes [file] when done
     * (or after an external viewer returns).
     */
    data class OpenedOriginal(val file: File, val mimeType: String, val documentId: String)

    suspend fun openDocumentCopy(documentId: String): OpenedOriginal? = withContext(Dispatchers.IO) {
        val row = dao.getById(documentId) ?: return@withContext null
        val copy = openCopy(documentId) ?: return@withContext null
        OpenedOriginal(copy, row.mimeType, row.id)
    }

    suspend fun openOriginalForTicket(ticketId: Long): OpenedOriginal? = withContext(Dispatchers.IO) {
        val row = dao.forTicket(ticketId).firstOrNull() ?: return@withContext null
        openDocumentCopy(row.id)
    }

    /** A pass was deleted: its documents and readings go with it. */
    suspend fun deleteForTicket(ticketId: Long) = withContext(Dispatchers.IO) {
        dao.forTicket(ticketId).forEach { remove(it) }
    }

    /** A review was cancelled: drop the draft's file unless a pass already uses it. */
    suspend fun discardDraft(documentId: String) = withContext(Dispatchers.IO) {
        val row = dao.getById(documentId) ?: return@withContext
        if (row.ticketId == null) remove(row)
    }

    /** Drafts left behind by a crash or an abandoned review. */
    suspend fun pruneDrafts(nowMillis: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        dao.unlinkedBefore(nowMillis - DRAFT_TTL_MS).forEach { remove(it) }
        File(context.cacheDir, CACHE_DIR).listFiles()?.forEach { it.delete() }
    }

    private suspend fun remove(row: PassDocumentEntity) {
        dao.deleteExtractions(row.id)
        dao.delete(row.id)
        File(dir, row.storedName).delete()
        File(File(context.cacheDir, CACHE_DIR), row.id + ".pdf").delete()
        File(File(context.cacheDir, CACHE_DIR), row.id + ".img").delete()
    }

    private fun readCapped(uri: Uri): ByteArray {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Couldn’t open that file.")
        return input.use { stream ->
            val out = ByteArrayOutputStream()
            val buf = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val n = stream.read(buf)
                if (n < 0) break
                total += n
                if (total > DocumentRules.MAX_BYTES) {
                    throw IllegalArgumentException("That file is too big. Tickets must be under 15 MB.")
                }
                out.write(buf, 0, n)
            }
            if (total == 0L) throw IllegalArgumentException("That file is empty.")
            out.toByteArray()
        }
    }

    private fun encrypted(file: File): EncryptedFile = EncryptedFile.Builder(
        context,
        file,
        masterKey(),
        EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
    ).build()

    private fun masterKey(): MasterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private fun PassExtraction.toEntity(documentId: String, nowMillis: Long) = DocumentExtractionEntity(
        id = UUID.randomUUID().toString(),
        documentId = documentId,
        documentType = classification.kind.name,
        fieldsJson = toJson(),
        confidence = confidence,
        extractionVersion = PassExtraction.VERSION,
        createdAtMillis = nowMillis
    )

    companion object {
        private const val TAG = "LipaBill.PassDocs"
        private const val DIR = "pass_documents"
        private const val CACHE_DIR = "pass_previews"
        private const val DRAFT_TTL_MS = 7L * 24L * 60L * 60L * 1000L

        fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
