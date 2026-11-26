package com.lipabill.app.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.lipabill.app.resolveRepoFile
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * v18 (withdrawn invoice attachments, debug installs only) → v19 drops the table;
 * v17 → v19 is a no-op. Both must land on the same schema, which equals v17.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class Migration18To19Test {

    private lateinit var helper: SupportSQLiteOpenHelper
    private lateinit var db: SupportSQLiteDatabase

    @Before
    fun open() {
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("m18.db")
        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name("m18.db")
                .callback(object : SupportSQLiteOpenHelper.Callback(18) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        db = helper.writableDatabase
    }

    @After
    fun close() = helper.close()

    private fun hasTable(name: String): Boolean =
        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name=?", arrayOf(name))
            .use { it.moveToFirst() }

    @Test
    fun drops_invoice_table_from_v18() {
        db.execSQL(
            "CREATE TABLE `transaction_documents` (`id` TEXT NOT NULL, `transactionId` INTEGER, PRIMARY KEY(`id`))"
        )
        db.execSQL("CREATE INDEX `index_transaction_documents_transactionId` ON `transaction_documents` (`transactionId`)")
        AppDatabaseMigrations.MIGRATION_18_19.migrate(db)
        assertFalse(hasTable("transaction_documents"))
        // Safe to re-run (e.g. table already gone).
        AppDatabaseMigrations.MIGRATION_18_19.migrate(db)
    }

    @Test
    fun both_paths_registered_and_v19_schema_equals_v17() {
        val all = AppDatabaseMigrations.ALL
        assertTrue(all.any { it.startVersion == 17 && it.endVersion == 19 })
        assertTrue(all.any { it.startVersion == 18 && it.endVersion == 19 })

        fun tables(version: Int): Map<String, String> {
            val file = resolveRepoFile("app/schemas/com.lipabill.app.data.local.AppDatabase/$version.json")
                ?: error("schema $version.json not found")
            val entities = JSONObject(file.readText()).getJSONObject("database").getJSONArray("entities")
            return (0 until entities.length()).associate { i ->
                val e = entities.getJSONObject(i)
                e.getString("tableName") to e.getString("createSql")
            }
        }
        assertEquals(tables(17), tables(19))
    }
}
