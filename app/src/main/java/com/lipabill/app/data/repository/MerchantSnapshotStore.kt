package com.lipabill.app.data.repository

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import com.lipabill.app.data.local.entity.MerchantEntity
import com.lipabill.app.data.model.TransactionType
import com.lipabill.app.security.AppAtRestCrypto
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Keystore-sealed mirror of learned paybill / till / Pochi names.
 * The file is not backed up. An older blob sealed with the APK signing
 * certificate is read once, rewritten under the keystore, then deleted.
 */
class MerchantSnapshotStore(context: Context) {

    private val appContext = context.applicationContext
    private val file = File(appContext.filesDir, FILE_NAME)
    private val legacyPlain = File(appContext.filesDir, LEGACY_PLAIN_NAME)

    fun write(merchants: List<MerchantEntity>) {
        val array = JSONArray()
        for (m in merchants) {
            array.put(
                JSONObject()
                    .put("t", m.type.name)
                    .put("i", m.identifier)
                    .put("a", m.accountHint)
                    .put("d", m.displayName)
                    .put("n", m.normalizedName)
                    .put("u", m.lastUsedMillis)
                    .put("c", m.useCount)
            )
        }
        val payload = JSONObject()
            .put("v", VERSION)
            .put("m", array)
            .toString()
            .toByteArray(Charsets.UTF_8)
        if (file.exists()) file.delete()
        encryptedFile().openFileOutput().use { it.write(payload) }
        if (legacyPlain.exists()) legacyPlain.delete()
    }

    fun read(): List<MerchantEntity> {
        migrateLegacyPlainIfNeeded()
        if (!file.exists()) return emptyList()
        readKeystore()?.let { return it }
        val legacy = readCertSealed() ?: return emptyList()
        write(legacy)
        return legacy
    }

    private fun readKeystore(): List<MerchantEntity>? = try {
        val plain = encryptedFile().openFileInput().use { it.readBytes() }
        parseJson(String(plain, Charsets.UTF_8))
    } catch (_: Throwable) {
        null
    }

    private fun readCertSealed(): List<MerchantEntity>? = try {
        val plain = AppAtRestCrypto.open(appContext, file.readBytes()) ?: return null
        parseJson(String(plain, Charsets.UTF_8))
    } catch (t: Throwable) {
        Log.w(TAG, "read_failed", t)
        null
    }

    private fun encryptedFile(): EncryptedFile {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedFile.Builder(
            appContext,
            file,
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()
    }

    private fun migrateLegacyPlainIfNeeded() {
        if (file.exists() || !legacyPlain.exists()) return
        try {
            val merchants = parseJson(legacyPlain.readText())
            if (merchants.isNotEmpty()) {
                write(merchants)
            } else {
                legacyPlain.delete()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "legacy_migrate_failed", t)
        }
    }

    private fun parseJson(text: String): List<MerchantEntity> {
        val root = JSONObject(text)
        // Support sealed v2 short keys and legacy long keys
        val array = root.optJSONArray("m")
            ?: root.optJSONArray("merchants")
            ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                val typeName = o.optString("t").ifBlank { o.optString("type") }
                val type = runCatching { TransactionType.valueOf(typeName) }.getOrNull()
                    ?: continue
                val identifier = o.optString("i").ifBlank { o.optString("identifier") }.trim()
                if (identifier.isEmpty()) continue
                val displayName = o.optString("d").ifBlank {
                    o.optString("displayName")
                }.ifBlank { identifier }
                val accountHint = o.optString("a").ifBlank { o.optString("accountHint") }
                    .takeIf { it.isNotBlank() }
                val normalized = o.optString("n").ifBlank { o.optString("normalizedName") }
                    .ifBlank { displayName.trim().lowercase() }
                val lastUsed = when {
                    o.has("u") -> o.optLong("u", 0L)
                    else -> o.optLong("lastUsedMillis", 0L)
                }
                val useCount = when {
                    o.has("c") -> o.optInt("c", 1)
                    else -> o.optInt("useCount", 1)
                }.coerceAtLeast(1)
                add(
                    MerchantEntity(
                        id = 0,
                        type = type,
                        identifier = identifier,
                        accountHint = accountHint,
                        displayName = displayName,
                        normalizedName = normalized,
                        lastUsedMillis = lastUsed,
                        useCount = useCount
                    )
                )
            }
        }
    }

    companion object {
        private const val TAG = "LipaBill.MerchantSnap"
        /** Opaque name — avoid advertising purpose in APK strings / backups. */
        const val FILE_NAME = ".lb_m"
        private const val LEGACY_PLAIN_NAME = "merchant_directory.json"
        private const val VERSION = 2
    }
}
