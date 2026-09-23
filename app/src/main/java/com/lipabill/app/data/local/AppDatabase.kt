package com.lipabill.app.data.local

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import com.lipabill.app.data.local.dao.MerchantDao
import com.lipabill.app.data.local.dao.RepeatAttemptDao
import com.lipabill.app.data.local.dao.TicketDao
import com.lipabill.app.data.local.dao.TransactionDao
import com.lipabill.app.data.local.entity.MerchantEntity
import com.lipabill.app.data.local.entity.PendingMerchantPaymentEntity
import com.lipabill.app.data.local.entity.RepeatAttemptEntity
import com.lipabill.app.data.local.entity.TicketEntity
import com.lipabill.app.data.local.entity.TransactionEntity
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File
import java.security.SecureRandom

@Database(
    entities = [
        TransactionEntity::class,
        RepeatAttemptEntity::class,
        MerchantEntity::class,
        PendingMerchantPaymentEntity::class,
        TicketEntity::class
    ],
    version = 8,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun repeatAttemptDao(): RepeatAttemptDao
    abstract fun merchantDao(): MerchantDao
    abstract fun ticketDao(): TicketDao

    companion object {
        private const val TAG = "LipaBill.Db"
        private const val DB_NAME = "lipabill_transactions.db"
        /**
         * EncryptedFile binds the path into AAD — do not rename without re-wrapping.
         * Prefer legacy name for existing installs; new installs still use it.
         */
        private const val PASSPHRASE_FILE = "db_passphrase.bin"

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: openOrRecreate(context.applicationContext).also { instance = it }
            }
        }

        /**
         * Opens the SQLCipher DB. On **app updates**, never wipe existing files — a
         * failed open surfaces as an error so we do not silently delete transactions.
         * Wipe + recreate is only allowed on a fresh install (or Clear storage), when
         * Auto Backup may have restored cipher files without the Keystore wrap key.
         */
        private fun openOrRecreate(context: Context): AppDatabase {
            val first = tryBuild(context)
            return try {
                first.openHelper.writableDatabase
                first
            } catch (t: Throwable) {
                Log.w(TAG, "cipher_open_failed", t)
                runCatching { first.close() }
                if (!isFreshInstall(context)) {
                    Log.e(
                        TAG,
                        "cipher_open_failed_on_update — preserving DB/passphrase " +
                            "(uninstall or Clear storage is required to reset)"
                    )
                    // Retry once without rotating passphrase; then surface the failure.
                    val retry = tryBuild(context)
                    retry.openHelper.writableDatabase
                    return retry
                }
                Log.w(TAG, "cipher_open_failed_on_fresh_install_recreating", t)
                wipeCipherArtifacts(context)
                val fresh = tryBuild(context)
                fresh.openHelper.writableDatabase
                fresh
            }
        }

        private fun tryBuild(context: Context): AppDatabase {
            System.loadLibrary("sqlcipher")
            val passphrase = getOrCreatePassphrase(context)
            val factory = SupportOpenHelperFactory(passphrase)
            return Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
                .openHelperFactory(factory)
                .addMigrations(*AppDatabaseMigrations.ALL)
                // Early draft schemas (pre–v8) had no migrations; wipe those only.
                // From v8 onward, missing a Migration will crash instead of deleting
                // learned merchants / tickets / transactions.
                .fallbackToDestructiveMigrationFrom(1, 2, 3, 4, 5, 6, 7)
                .build()
        }

        /** True only when this package has never been updated on the device. */
        private fun isFreshInstall(context: Context): Boolean {
            return try {
                val pm = context.packageManager
                val info = if (Build.VERSION.SDK_INT >= 33) {
                    pm.getPackageInfo(
                        context.packageName,
                        PackageManager.PackageInfoFlags.of(0)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(context.packageName, 0)
                }
                info.firstInstallTime == info.lastUpdateTime
            } catch (_: Throwable) {
                false
            }
        }

        private fun wipeCipherArtifacts(context: Context) {
            context.deleteDatabase(DB_NAME)
            context.getDatabasePath(DB_NAME)?.let { db ->
                File(db.path + "-wal").delete()
                File(db.path + "-shm").delete()
                File(db.path + "-journal").delete()
            }
            File(context.filesDir, PASSPHRASE_FILE).delete()
        }

        /**
         * Persists a random SQLCipher passphrase inside an [EncryptedFile]
         * so the DB key itself is never stored in plaintext.
         */
        private fun getOrCreatePassphrase(context: Context): ByteArray {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val file = File(context.filesDir, PASSPHRASE_FILE)
            if (file.exists()) {
                return try {
                    readPassphrase(context, masterKey, file)
                } catch (t: Throwable) {
                    Log.w(TAG, "passphrase_unreadable", t)
                    // On updates, never rotate the key — that would orphan the existing DB.
                    if (!isFreshInstall(context)) {
                        throw IllegalStateException(
                            "Cannot read DB passphrase after app update; " +
                                "refusing to rotate key (would wipe transactions)",
                            t
                        )
                    }
                    Log.w(TAG, "passphrase_unreadable_rotating_fresh_install", t)
                    file.delete()
                    createPassphrase(context, masterKey, file)
                }
            }
            return createPassphrase(context, masterKey, file)
        }

        private fun readPassphrase(
            context: Context,
            masterKey: MasterKey,
            file: File
        ): ByteArray {
            val encryptedFile = EncryptedFile.Builder(
                context,
                file,
                masterKey,
                EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build()
            return encryptedFile.openFileInput().use { it.readBytes() }
        }

        private fun createPassphrase(
            context: Context,
            masterKey: MasterKey,
            file: File
        ): ByteArray {
            val passphrase = ByteArray(32).also { SecureRandom().nextBytes(it) }
            val encryptedFile = EncryptedFile.Builder(
                context,
                file,
                masterKey,
                EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build()
            encryptedFile.openFileOutput().use { it.write(passphrase) }
            return passphrase
        }
    }
}
