package com.lipabill.app.data.tickets

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.util.zip.ZipFile

/**
 * Resolves a ticket document URI from VIEW / SEND intents (.pkpass, PDF, images).
 */
object PkPassIntents {

    /**
     * Copy an opened pass into cache. A zip that only wraps a .pkpass is unpacked.
     * A .pkpass (zip with pass.json) is returned as itself.
     */
    fun preparePkPasses(context: Context, intent: Intent?): List<Uri> {
        val incoming = describe(context, intent) ?: return emptyList()
        if (!incoming.isPass) return emptyList()
        val copied = copyToCache(context, incoming, ".pkpass") ?: return emptyList()
        val unwrapped = unwrapPkPasses(copied)
        if (unwrapped.isEmpty()) copied.path?.let { File(it).delete() }
        return unwrapped
    }

    /** PDF or image opened from another app. Passes are handled by [preparePkPasses]. */
    fun capture(context: Context, intent: Intent?): Uri? {
        val incoming = describe(context, intent) ?: return null
        if (incoming.isPass || !incoming.isOtherTicket) return null
        val ext = when {
            incoming.mime == "application/pdf" || incoming.name.endsWith(".pdf", true) -> ".pdf"
            incoming.mime == "image/png" || incoming.name.endsWith(".png", true) -> ".png"
            incoming.mime == "image/webp" || incoming.name.endsWith(".webp", true) -> ".webp"
            incoming.mime == "image/jpeg" ||
                incoming.name.endsWith(".jpg", true) ||
                incoming.name.endsWith(".jpeg", true) -> ".jpg"
            else -> "." + incoming.mime.substringAfter('/', "img").substringBefore('+')
        }
        return copyToCache(context, incoming, ext)
    }

    /** Shares must be content:// grants. file:// is not a permission. */
    internal fun isSharedContentScheme(scheme: String?): Boolean =
        scheme.equals("content", ignoreCase = true)

    /**
     * Zip-like types WhatsApp and mail use for a .pkpass.
     * A blank type or wildcard is not enough — the name has to say .pkpass.
     */
    internal fun mimeCanWrapPkPass(mime: String): Boolean {
        val type = mime.lowercase()
        return type == "application/zip" ||
            type == "application/x-zip" ||
            type == "application/x-zip-compressed" ||
            type == "application/octet-stream"
    }

    private class Incoming(
        val source: Uri,
        val mime: String,
        val name: String
    ) {
        val isPass: Boolean
            get() = !isOtherTicket &&
                (PkPassParser.isPkPassUri(source, mime, name) || mimeCanWrapPkPass(mime))
        val isOtherTicket: Boolean
            get() = mime == "application/pdf" ||
                name.endsWith(".pdf", true) ||
                mime.startsWith("image/")
    }

    private fun describe(context: Context, intent: Intent?): Incoming? {
        val source = candidateUri(intent) ?: return null
        val mime = (intent?.type ?: context.contentResolver.getType(source)).orEmpty().lowercase()
        val name = listOfNotNull(
            displayName(context, source),
            source.lastPathSegment,
            intent?.clipData?.description?.label?.toString()
        ).joinToString(" ")
        return Incoming(source, mime, name)
    }

    private fun candidateUri(intent: Intent?): Uri? {
        if (intent == null) return null
        // Receipt notifications use lipabill://receipt/… and are not ticket files.
        if (intent.data?.scheme == "lipabill") return null
        val data = intent.data
        val stream = intent.streamUri()
        val uri = when (intent.action) {
            Intent.ACTION_VIEW -> data ?: stream
            Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> stream ?: data
            else -> null
        }
        return uri?.takeIf { isSharedContentScheme(it.scheme) }
    }

    private fun unwrapPkPasses(uri: Uri): List<Uri> {
        val file = uri.path?.let(::File) ?: return emptyList()
        if (!file.isFile) return emptyList()
        val entries = runCatching {
            ZipFile(file).use { zip -> zip.entries().toList().map { it.name } }
        }.getOrNull() ?: return emptyList()
        val usable = entries.filter { name ->
            val normalized = name.replace('\\', '/')
            !normalized.contains("__MACOSX") &&
                !normalized.endsWith("/") &&
                !normalized.startsWith("/") &&
                !normalized.split('/').contains("..")
        }
        fun base(name: String) = name.substringAfterLast('/').substringAfterLast('\\')
        if (usable.any { base(it).equals("pass.json", ignoreCase = true) }) return listOf(uri)
        val nested = usable.filter { base(it).endsWith(".pkpass", ignoreCase = true) }
        if (nested.isEmpty()) return emptyList()
        val dir = file.parentFile ?: return emptyList()
        val extracted = nested.mapIndexedNotNull { index, entryName ->
            val dest = File(dir, "unpacked-${System.currentTimeMillis()}-$index.pkpass")
            val wrote = runCatching {
                ZipFile(file).use { zip ->
                    val entry = zip.getEntry(entryName) ?: return@use false
                    zip.getInputStream(entry).use { input ->
                        dest.outputStream().use { output ->
                            copyAtMost(input, output, MAX_SHARED_TICKET_BYTES)
                        }
                    }
                }
            }.getOrDefault(false)
            if (!wrote) {
                dest.delete()
                null
            } else {
                Uri.fromFile(dest)
            }
        }
        if (extracted.isNotEmpty()) file.delete()
        return extracted
    }

    private fun copyToCache(context: Context, incoming: Incoming, ext: String): Uri? {
        return runCatching {
            val dir = File(context.cacheDir, "pkpass-inbox").apply { mkdirs() }
            val dest = File(dir, "pass-${System.currentTimeMillis()}$ext")
            val wrote = context.contentResolver.openInputStream(incoming.source)?.use { input ->
                dest.outputStream().use { output ->
                    copyAtMost(input, output, MAX_SHARED_TICKET_BYTES)
                }
            } ?: false
            if (!wrote) {
                dest.delete()
                return null
            }
            Uri.fromFile(dest)
        }.getOrNull()
    }

    private fun displayName(context: Context, uri: Uri): String? {
        if (uri.scheme != "content") return null
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index < 0) null else cursor.getString(index)
            }
        }.getOrNull()
    }

    private fun Intent.streamUri(): Uri? {
        getParcelableExtraCompat(Intent.EXTRA_STREAM)?.let { return it }
        val clip = clipData ?: return null
        if (clip.itemCount <= 0) return null
        return clip.getItemAt(0)?.uri
    }

    @Suppress("DEPRECATION")
    private fun Intent.getParcelableExtraCompat(key: String): Uri? {
        return if (android.os.Build.VERSION.SDK_INT >= 33) {
            getParcelableExtra(key, Uri::class.java)
        } else {
            getParcelableExtra(key) as? Uri
        }
    }
}
