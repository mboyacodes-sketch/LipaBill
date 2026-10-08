package com.lipabill.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Additive Room migrations. Never wipe user data for schema bumps from
 * [AppDatabase] version 8 onward — learned merchants, tickets, and SMS rows
 * live in the same encrypted DB.
 *
 * When changing entities:
 * 1. Bump `@Database(version = N)`
 * 2. Add `Migration(N-1, N)` here that ALTER/CREATE as needed
 * 3. Register it in [ALL]
 *
 * Prefer `ALTER TABLE … ADD COLUMN` with defaults over recreate-and-copy.
 */
object AppDatabaseMigrations {

    /**
     * Date index for challenge and check-in windows, plus the engagement tables.
     * Existing transaction rows are left in place.
     */
    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_transactions_timestampMillis` ON `transactions` (`timestampMillis`)"
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `challenges` (
                  `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                  `templateId` TEXT NOT NULL,
                  `startAt` INTEGER NOT NULL,
                  `endAt` INTEGER NOT NULL,
                  `zoneId` TEXT NOT NULL,
                  `status` TEXT NOT NULL,
                  `breakingTransactionId` INTEGER,
                  `completedAt` INTEGER,
                  `brokenAt` INTEGER
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `challenge_achievements` (
                  `id` TEXT NOT NULL PRIMARY KEY,
                  `challengeId` INTEGER NOT NULL,
                  `grantedAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `weekly_checkins` (
                  `weekId` TEXT NOT NULL PRIMARY KEY,
                  `answers` TEXT NOT NULL,
                  `completedAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `checkin_streak` (
                  `id` INTEGER NOT NULL PRIMARY KEY,
                  `count` INTEGER NOT NULL,
                  `freezeUsed` INTEGER NOT NULL,
                  `lastWeekId` TEXT
                )
                """.trimIndent()
            )
            db.execSQL(
                "INSERT OR IGNORE INTO `checkin_streak` (`id`, `count`, `freezeUsed`, `lastWeekId`) VALUES (1, 0, 0, NULL)"
            )
        }
    }

    /** Artwork paths copied out of a .pkpass. Null on tickets saved before this. */
    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `tickets` ADD COLUMN `passHeroPath` TEXT")
            db.execSQL("ALTER TABLE `tickets` ADD COLUMN `passLogoPath` TEXT")
            db.execSQL("ALTER TABLE `tickets` ADD COLUMN `passFooterPath` TEXT")
        }
    }

    /** Migrations that preserve data. Add new ones at the end of this list. */
    val ALL: Array<Migration> = arrayOf(MIGRATION_8_9, MIGRATION_9_10)
}
