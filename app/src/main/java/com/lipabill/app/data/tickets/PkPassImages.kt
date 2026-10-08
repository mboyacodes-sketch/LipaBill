package com.lipabill.app.data.tickets

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipInputStream

/**
 * Artwork Apple Wallet ships inside a .pkpass.
 * [hero] is the strip, otherwise the background, otherwise the thumbnail.
 */
data class PkPassImages(
    val hero: ByteArray? = null,
    val logo: ByteArray? = null,
    val footer: ByteArray? = null
) {
    fun isEmpty(): Boolean = hero == null && logo == null && footer == null
}

internal data class PkPassZip(
    val passJson: String?,
    val images: PkPassImages
)

private val imageName = Regex(
    """^(strip|background|thumbnail|logo|icon|footer)(?:@([23])x)?\.(png|jpe?g|webp)$""",
    RegexOption.IGNORE_CASE
)

private const val MAX_IMAGE_BYTES = 2_000_000
internal const val MAX_PASS_JSON_BYTES = 256 * 1024
internal const val MAX_SHARED_TICKET_BYTES = 20 * 1024 * 1024

/** Copies until [maxBytes]. Returns false when the stream is empty or larger than the cap. */
internal fun copyAtMost(input: InputStream, output: OutputStream, maxBytes: Int): Boolean {
    if (maxBytes <= 0) return false
    val buf = ByteArray(8192)
    var total = 0
    while (true) {
        val n = input.read(buf)
        if (n < 0) break
        if (n > maxBytes - total) return false
        output.write(buf, 0, n)
        total += n
    }
    return total > 0
}

internal fun readAtMost(input: InputStream, maxBytes: Int): ByteArray? {
    val out = ByteArrayOutputStream()
    return if (copyAtMost(input, out, maxBytes)) out.toByteArray() else null
}

internal fun readPkPassZip(input: InputStream): PkPassZip {
    var passJson: String? = null
    val best = HashMap<String, Pair<Int, ByteArray>>()
    ZipInputStream(BufferedInputStream(input)).use { zip ->
        var entry = zip.nextEntry
        while (entry != null) {
            val name = entry.name.replace('\\', '/').substringAfterLast('/')
            val entryName = entry.name.replace('\\', '/')
            val safeName = !entryName.startsWith("/") && !entryName.split('/').contains("..")
            if (!entry.isDirectory && safeName && !entryName.contains("__MACOSX")) {
                if (name.equals("pass.json", ignoreCase = true) && passJson == null) {
                    passJson = readAtMost(zip, MAX_PASS_JSON_BYTES)?.toString(Charsets.UTF_8)
                } else {
                    val match = imageName.matchEntire(name)
                    val declared = entry.size
                    if (match != null && (declared < 0 || declared <= MAX_IMAGE_BYTES)) {
                        val role = match.groupValues[1].lowercase()
                        val scale = match.groupValues[2].toIntOrNull() ?: 1
                        val bytes = readAtMost(zip, MAX_IMAGE_BYTES)
                        if (bytes != null) {
                            val current = best[role]
                            if (current == null || scale > current.first) {
                                best[role] = scale to bytes
                            }
                        }
                    }
                }
            }
            zip.closeEntry()
            entry = zip.nextEntry
        }
    }
    fun role(key: String): ByteArray? = best[key]?.second
    return PkPassZip(
        passJson = passJson,
        images = PkPassImages(
            hero = role("strip") ?: role("background") ?: role("thumbnail"),
            logo = role("logo") ?: role("icon"),
            footer = role("footer")
        )
    )
}
