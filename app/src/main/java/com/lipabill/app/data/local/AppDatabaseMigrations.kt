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

    /**
     * Version 10 briefly stored pass artwork paths. Installs that never had those
     * columns are already on the version 11 ticket table.
     */
    val MIGRATION_9_11 = object : Migration(9, 11) {
        override fun migrate(db: SupportSQLiteDatabase) = Unit
    }

    /** Drops the pass artwork columns added in version 10. Ticket rows stay. */
    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `tickets_new` (
                  `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                  `title` TEXT NOT NULL,
                  `venue` TEXT,
                  `startsAtMillis` INTEGER,
                  `seatOrTier` TEXT,
                  `barcodeFormat` TEXT NOT NULL,
                  `barcodeValue` TEXT NOT NULL,
                  `orderId` TEXT,
                  `source` TEXT NOT NULL,
                  `status` TEXT NOT NULL,
                  `createdAtMillis` INTEGER NOT NULL,
                  `notes` TEXT,
                  `expectsBoardingPass` INTEGER NOT NULL,
                  `hasBoardingPass` INTEGER NOT NULL,
                  `boardingBarcodeValue` TEXT,
                  `boardingBarcodeFormat` TEXT,
                  `boardingTitle` TEXT,
                  `boardingVenue` TEXT,
                  `boardingStartsAtMillis` INTEGER,
                  `boardingSeatOrTier` TEXT,
                  `boardingNotes` TEXT,
                  `hasReturnBoardingPass` INTEGER NOT NULL,
                  `returnBoardingBarcodeValue` TEXT,
                  `returnBoardingBarcodeFormat` TEXT,
                  `returnBoardingTitle` TEXT,
                  `returnBoardingVenue` TEXT,
                  `returnBoardingStartsAtMillis` INTEGER,
                  `returnBoardingSeatOrTier` TEXT,
                  `returnBoardingNotes` TEXT
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO `tickets_new` (
                  `id`, `title`, `venue`, `startsAtMillis`, `seatOrTier`, `barcodeFormat`,
                  `barcodeValue`, `orderId`, `source`, `status`, `createdAtMillis`, `notes`,
                  `expectsBoardingPass`, `hasBoardingPass`, `boardingBarcodeValue`,
                  `boardingBarcodeFormat`, `boardingTitle`, `boardingVenue`,
                  `boardingStartsAtMillis`, `boardingSeatOrTier`, `boardingNotes`,
                  `hasReturnBoardingPass`, `returnBoardingBarcodeValue`,
                  `returnBoardingBarcodeFormat`, `returnBoardingTitle`, `returnBoardingVenue`,
                  `returnBoardingStartsAtMillis`, `returnBoardingSeatOrTier`, `returnBoardingNotes`
                )
                SELECT
                  `id`, `title`, `venue`, `startsAtMillis`, `seatOrTier`, `barcodeFormat`,
                  `barcodeValue`, `orderId`, `source`, `status`, `createdAtMillis`, `notes`,
                  `expectsBoardingPass`, `hasBoardingPass`, `boardingBarcodeValue`,
                  `boardingBarcodeFormat`, `boardingTitle`, `boardingVenue`,
                  `boardingStartsAtMillis`, `boardingSeatOrTier`, `boardingNotes`,
                  `hasReturnBoardingPass`, `returnBoardingBarcodeValue`,
                  `returnBoardingBarcodeFormat`, `returnBoardingTitle`, `returnBoardingVenue`,
                  `returnBoardingStartsAtMillis`, `returnBoardingSeatOrTier`, `returnBoardingNotes`
                FROM `tickets`
                """.trimIndent()
            )
            db.execSQL("DROP TABLE `tickets`")
            db.execSQL("ALTER TABLE `tickets_new` RENAME TO `tickets`")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_tickets_barcodeValue` ON `tickets` (`barcodeValue`)"
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_tickets_boardingBarcodeValue` ON `tickets` (`boardingBarcodeValue`)"
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_tickets_returnBoardingBarcodeValue` ON `tickets` (`returnBoardingBarcodeValue`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_tickets_orderId` ON `tickets` (`orderId`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_tickets_createdAtMillis` ON `tickets` (`createdAtMillis`)"
            )
        }
    }

    /** Migrations that preserve data. Add new ones at the end of this list. */
    val ALL: Array<Migration> = arrayOf(MIGRATION_8_9, MIGRATION_9_11, MIGRATION_10_11)
}
