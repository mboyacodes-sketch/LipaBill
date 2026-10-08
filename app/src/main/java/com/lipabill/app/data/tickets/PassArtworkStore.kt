package com.lipabill.app.data.tickets

import android.content.Context
import android.graphics.BitmapFactory
import com.lipabill.app.data.model.Ticket
import java.io.File
import java.security.MessageDigest

data class SavedPassArt(
    val hero: String? = null,
    val logo: String? = null,
    val footer: String? = null
)

/**
 * Copies .pkpass images into app files so the ticket can show them after the zip is gone.
 * Paths are relative to [Context.getFilesDir].
 */
object PassArtworkStore {

    private const val ROOT = "pass-art"

    fun save(context: Context, barcode: String, images: PkPassImages): SavedPassArt {
        if (images.isEmpty() || barcode.isBlank()) return SavedPassArt()
        val dir = File(context.filesDir, "$ROOT/${folderKey(barcode)}")
        dir.mkdirs()
        return SavedPassArt(
            hero = write(dir, "hero", images.hero),
            logo = write(dir, "logo", images.logo),
            footer = write(dir, "footer", images.footer)
        )
    }

    fun decode(context: Context, relativePath: String?): android.graphics.Bitmap? {
        val file = fileFor(context, relativePath) ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val sample = sampleSize(bounds.outWidth, bounds.outHeight)
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(file.absolutePath, opts)
    }

    fun delete(context: Context, ticket: Ticket) {
        listOf(ticket.passHeroPath, ticket.passLogoPath, ticket.passFooterPath)
            .mapNotNull { fileFor(context, it)?.parentFile }
            .distinct()
            .forEach { dir ->
                if (dir.parentFile?.name == ROOT) dir.deleteRecursively()
            }
    }

    private fun write(dir: File, name: String, bytes: ByteArray?): String? {
        if (bytes == null || bytes.isEmpty()) return null
        val file = File(dir, "$name.png")
        file.writeBytes(bytes)
        return file.relativeTo(dir.parentFile!!.parentFile!!).path
    }

    private fun fileFor(context: Context, relativePath: String?): File? {
        val relative = relativePath?.trim().orEmpty()
        if (relative.isEmpty() || relative.contains("..")) return null
        val file = File(context.filesDir, relative)
        val root = File(context.filesDir, ROOT).canonicalFile
        if (!file.canonicalFile.path.startsWith(root.path)) return null
        return file.takeIf { it.isFile }
    }

    private fun folderKey(barcode: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(barcode.toByteArray())
        return digest.take(8).joinToString("") { "%02x".format(it) }
    }

    private fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        val longest = maxOf(width, height)
        while (longest / sample > 1600) sample *= 2
        return sample
    }
}
