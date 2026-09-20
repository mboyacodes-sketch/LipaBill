package com.lipabill.app.data.local

import androidx.room.migration.Migration

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

    /** Migrations that preserve data. Add new ones at the end of this list. */
    val ALL: Array<Migration> = emptyArray()
}
