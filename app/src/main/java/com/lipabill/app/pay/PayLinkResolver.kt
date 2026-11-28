package com.lipabill.app.pay

import com.lipabill.app.BuildConfig
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Opens sealed (v4) pay links by asking lipabill.app to decrypt them.
 *
 * Sends only the link token. The server is stateless — it does not store or
 * log tokens or results. Every field in the answer is re-validated by
 * [PayLink.fromResolved] before it can reach the pay screen.
 */
object PayLinkResolver {
    sealed interface Result {
        data class Resolved(val target: PayLink.Target) : Result
        /** Expired, tampered, or not a LipaBill link. */
        data object Invalid : Result
        /** No connection, timeout, or server error — worth retrying. */
        data object Unreachable : Result
    }

    private const val TIMEOUT_MS = 8_000
    private const val MAX_RESPONSE = 2_048

    suspend fun resolve(raw: String?): Result = withContext(Dispatchers.IO) {
        val token = PayLink.tokenOf(raw) ?: return@withContext Result.Invalid
        val url = URL(BuildConfig.PAYLINK_RESOLVE_URL)
        if (url.protocol != "https") return@withContext Result.Unreachable

        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            doOutput = true
            useCaches = false
            instanceFollowRedirects = false
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }
        try {
            connection.outputStream.use {
                it.write(JSONObject().put("token", token).toString().toByteArray(Charsets.UTF_8))
            }
            when (connection.responseCode) {
                200 -> {
                    val body = connection.inputStream.use { stream ->
                        String(stream.readNBytesCompat(MAX_RESPONSE), Charsets.UTF_8)
                    }
                    parse(body, token)?.let { Result.Resolved(it) } ?: Result.Invalid
                }
                400, 404 -> Result.Invalid
                else -> Result.Unreachable
            }
        } catch (_: IOException) {
            Result.Unreachable
        } catch (_: org.json.JSONException) {
            Result.Invalid
        } finally {
            connection.disconnect()
        }
    }

    internal fun parse(body: String, token: String): PayLink.Target? {
        val json = JSONObject(body)
        return PayLink.fromResolved(
            kind = json.optString("kind"),
            identifier = json.optString("identifier"),
            account = json.optString("account").takeIf { !json.isNull("account") && it.isNotBlank() },
            amountShillings = if (json.isNull("amountShillings")) null else json.optLong("amountShillings", -1L),
            token = token
        )
    }

    /** readNBytes is API 33+; cap the body ourselves. */
    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(512)
        while (out.size() < limit) {
            val read = read(buffer, 0, minOf(buffer.size, limit - out.size()))
            if (read < 0) break
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }
}
