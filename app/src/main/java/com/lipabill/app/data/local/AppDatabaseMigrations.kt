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

    /**
     * Ledger source (wallet or bank). Existing rows default to M-Pesa.
     * Unique receipt codes are per source so two rails can share a ref.
     */
    val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE `transactions` ADD COLUMN `source` TEXT NOT NULL DEFAULT 'MPESA'"
            )
            db.execSQL("DROP INDEX IF EXISTS `index_transactions_code`")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_transactions_source_code` ON `transactions` (`source`, `code`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_transactions_source` ON `transactions` (`source`)"
            )
        }
    }

    /** Wallet list filter (Fuliza vs rest) + DESC time. */
    val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_transactions_type_timestampMillis` " +
                    "ON `transactions` (`type`, `timestampMillis`)"
            )
        }
    }

    /** Boarding-pass barcode encoding (format / EC level / version / charset) per leg. */
    val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `tickets` ADD COLUMN `boardingBarcodeSpec` TEXT")
            db.execSQL("ALTER TABLE `tickets` ADD COLUMN `returnBoardingBarcodeSpec` TEXT")
        }
    }

    /** Wallet pass colours (background / foreground / label) for event styling. */
    val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `tickets` ADD COLUMN `passColors` TEXT")
        }
    }

    /**
     * Original ticket files and what was read from them. New tables only — `tickets` is untouched,
     * so every saved pass carries over as-is.
     */
    val MIGRATION_15_16 = object : Migration(15, 16) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `pass_documents` (" +
                    "`id` TEXT NOT NULL, `ticketId` INTEGER, `storedName` TEXT NOT NULL, " +
                    "`mimeType` TEXT NOT NULL, `fileSizeBytes` INTEGER NOT NULL, " +
                    "`sha256` TEXT NOT NULL, `importedAtMillis` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`id`))"
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_pass_documents_sha256` " +
                    "ON `pass_documents` (`sha256`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_pass_documents_ticketId` " +
                    "ON `pass_documents` (`ticketId`)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `document_extractions` (" +
                    "`id` TEXT NOT NULL, `documentId` TEXT NOT NULL, `documentType` TEXT NOT NULL, " +
                    "`fieldsJson` TEXT NOT NULL, `confidence` REAL NOT NULL, " +
                    "`extractionVersion` INTEGER NOT NULL, `createdAtMillis` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`id`))"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_document_extractions_documentId` " +
                    "ON `document_extractions` (`documentId`)"
            )
        }
    }

    /**
     * Context Intelligence: pass-derived events and WorkManager reminder rows.
     * Existing passes are back-filled by [com.lipabill.app.tickets.PassReminderScheduler].
     */
    val MIGRATION_16_17 = object : Migration(16, 17) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `context_events` (" +
                    "`id` TEXT NOT NULL, `type` TEXT NOT NULL, `sourceType` TEXT NOT NULL, " +
                    "`sourceId` TEXT NOT NULL, `scheduledAt` INTEGER NOT NULL, " +
                    "`timezoneId` TEXT NOT NULL, `status` TEXT NOT NULL, PRIMARY KEY(`id`))"
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_context_events_sourceType_sourceId_type` " +
                    "ON `context_events` (`sourceType`, `sourceId`, `type`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_context_events_scheduledAt` " +
                    "ON `context_events` (`scheduledAt`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_context_events_status` " +
                    "ON `context_events` (`status`)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `reminders` (" +
                    "`id` TEXT NOT NULL, `eventId` TEXT NOT NULL, `fireAt` INTEGER NOT NULL, " +
                    "`state` TEXT NOT NULL, `workName` TEXT NOT NULL, PRIMARY KEY(`id`))"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_reminders_eventId` ON `reminders` (`eventId`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_reminders_fireAt` ON `reminders` (`fireAt`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_reminders_state` ON `reminders` (`state`)"
            )
        }
    }

    /** Migrations that preserve data. Add new ones at the end of this list. */
    val ALL: Array<Migration> = arrayOf(
        MIGRATION_8_9,
        MIGRATION_9_11,
        MIGRATION_10_11,
        MIGRATION_11_12,
        MIGRATION_12_13,
        MIGRATION_13_14,
        MIGRATION_14_15,
        MIGRATION_15_16,
        MIGRATION_16_17
    )
}
