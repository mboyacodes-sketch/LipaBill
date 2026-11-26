package com.lipabill.app.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.lipabill.app.metrics.AppLog

/**
 * Defers the SQLCipher open — Keystore passphrase unwrap, PBKDF2 key derivation (~600 ms on a
 * Galaxy S22) and migrations — from [AppDatabase.getInstance] to the first query, which Room
 * runs on its own executor. Application.onCreate stays off that cost.
 *
 * Owns the open-failure policy: on an app update a failed open is retried once and then
 * surfaced, never wiped; on a fresh install (Auto Backup restored cipher files without the
 * Keystore wrap key) the files and passphrase are wiped and a new database is created.
 */
internal class DeferredCipherFactory(
    private val passphrase: () -> ByteArray,
    private val isFreshInstall: () -> Boolean,
    private val wipeCipherArtifacts: () -> Unit,
    private val cipherFactory: (ByteArray) -> SupportSQLiteOpenHelper.Factory
) : SupportSQLiteOpenHelper.Factory {
    override fun create(
        configuration: SupportSQLiteOpenHelper.Configuration
    ): SupportSQLiteOpenHelper = DeferredCipherOpenHelper(configuration, this)

    internal fun newDelegate(
        configuration: SupportSQLiteOpenHelper.Configuration
    ): SupportSQLiteOpenHelper = cipherFactory(passphrase()).create(configuration)

    internal fun freshInstall(): Boolean = isFreshInstall()

    internal fun wipe() = wipeCipherArtifacts()
}

private class DeferredCipherOpenHelper(
    private val configuration: SupportSQLiteOpenHelper.Configuration,
    private val factory: DeferredCipherFactory
) : SupportSQLiteOpenHelper {
    private val lock = Any()
    private var delegate: SupportSQLiteOpenHelper? = null
    private var writeAheadLogging: Boolean? = null

    override val databaseName: String? get() = configuration.name

    override fun setWriteAheadLoggingEnabled(enabled: Boolean) {
        synchronized(lock) {
            writeAheadLogging = enabled
            delegate?.setWriteAheadLoggingEnabled(enabled)
        }
    }

    override val writableDatabase: SupportSQLiteDatabase
        get() = open { it.writableDatabase }

    override val readableDatabase: SupportSQLiteDatabase
        get() = open { it.readableDatabase }

    override fun close() {
        synchronized(lock) {
            delegate?.close()
            delegate = null
        }
    }

    private fun open(get: (SupportSQLiteOpenHelper) -> SupportSQLiteDatabase): SupportSQLiteDatabase =
        synchronized(lock) {
            delegate?.let { return get(it) }
            val first = newDelegate()
            try {
                get(first).also { delegate = first }
            } catch (t: Throwable) {
                AppLog.w(TAG, "cipher_open_failed", t)
                runCatching { first.close() }
                if (!factory.freshInstall()) {
                    AppLog.e(
                        TAG,
                        "cipher_open_failed_on_update — preserving DB/passphrase " +
                            "(uninstall or Clear storage is required to reset)"
                    )
                    // Retry once without rotating passphrase; then surface the failure.
                    val retry = newDelegate()
                    return get(retry).also { delegate = retry }
                }
                AppLog.w(TAG, "cipher_open_failed_on_fresh_install_recreating", t)
                factory.wipe()
                val fresh = newDelegate()
                get(fresh).also { delegate = fresh }
            }
        }

    private fun newDelegate(): SupportSQLiteOpenHelper =
        factory.newDelegate(configuration).also { helper ->
            writeAheadLogging?.let { helper.setWriteAheadLoggingEnabled(it) }
        }

    private companion object {
        const val TAG = "LipaBill.Db"
    }
}
