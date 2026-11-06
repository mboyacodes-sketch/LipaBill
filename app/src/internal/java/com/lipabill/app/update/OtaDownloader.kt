package com.lipabill.app.update

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

internal object OtaDownloader {

    private const val CONNECT_MS = 15_000
    private const val READ_MS = 60_000
    private const val MAX_APK_BYTES = 80L * 1024L * 1024L

    fun fetchManifest(url: String): OtaManifest {
        val body = httpGetText(url)
        return parseOtaManifest(body)
    }

    /**
     * Downloads [manifest.apkUrl] into cache and verifies SHA-256.
     * @throws IllegalStateException on hash mismatch or oversized payload
     */
    fun downloadApk(context: Context, manifest: OtaManifest): File {
        val dir = File(context.cacheDir, "ota").apply { mkdirs() }
        val out = File(dir, "LipaBill-${manifest.versionCode}.apk")
        if (out.exists()) out.delete()
        val partial = File(dir, "LipaBill-${manifest.versionCode}.apk.part")
        if (partial.exists()) partial.delete()

        requireTrustedApkUrl(manifest.apkUrl)
        // No redirects: a 3xx Location could leave lipabill.app/ota/.
        val connection = (URL(manifest.apkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_MS
            readTimeout = READ_MS
            instanceFollowRedirects = false
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.android.package-archive,*/*")
        }
        try {
            connection.connect()
            val code = connection.responseCode
            if (code !in 200..299) {
                error("apk_http_$code")
            }
            val length = connection.contentLengthLong
            if (length > MAX_APK_BYTES) error("apk_too_large")
            val digest = MessageDigest.getInstance("SHA-256")
            connection.inputStream.use { input ->
                partial.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > MAX_APK_BYTES) error("apk_too_large")
                        output.write(buf, 0, n)
                        digest.update(buf, 0, n)
                    }
                }
            }
            val actual = digest.digest().joinToString("") { b -> "%02x".format(b) }
            if (actual != manifest.sha256) {
                partial.delete()
                error("apk_hash_mismatch")
            }
            if (!partial.renameTo(out)) {
                partial.copyTo(out, overwrite = true)
                partial.delete()
            }
            return out
        } finally {
            connection.disconnect()
        }
    }

    private fun httpGetText(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_MS
            readTimeout = READ_MS
            instanceFollowRedirects = false
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
        }
        try {
            connection.connect()
            val code = connection.responseCode
            if (code !in 200..299) error("manifest_http_$code")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
