package com.lipabill.app.data.tickets.intelligence

/**
 * What a ticket file may be. The type comes from the file's own bytes, not its name or the
 * sender's claimed MIME type, so a renamed file can't slip past.
 */
object DocumentRules {

    /** Ticket PDFs and phone photos sit well under this; larger is almost never a ticket. */
    const val MAX_BYTES: Long = 15L * 1024L * 1024L

    const val PDF = "application/pdf"

    fun sniffMime(head: ByteArray): String? {
        fun at(offset: Int, vararg bytes: Int) =
            head.size >= offset + bytes.size && bytes.indices.all { head[offset + it] == bytes[it].toByte() }
        return when {
            at(0, 0x25, 0x50, 0x44, 0x46) -> PDF // %PDF
            at(0, 0xFF, 0xD8, 0xFF) -> "image/jpeg"
            at(0, 0x89, 0x50, 0x4E, 0x47) -> "image/png"
            at(0, 0x52, 0x49, 0x46, 0x46) && at(8, 0x57, 0x45, 0x42, 0x50) -> "image/webp" // RIFF....WEBP
            at(4, 0x66, 0x74, 0x79, 0x70) && isHeif(head) -> "image/heic" // ....ftyp heic/mif1
            else -> null
        }
    }

    private fun isHeif(head: ByteArray): Boolean {
        if (head.size < 12) return false
        val brand = String(head, 8, 4, Charsets.US_ASCII)
        return brand in setOf("heic", "heix", "hevc", "mif1", "msf1")
    }

    fun isPdf(mime: String?): Boolean = mime == PDF
}
