package com.lipabill.app.data.local

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.lipabill.app.resolveRepoFile
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * v15 → v16 on a real (unencrypted) SQLite file built from the exported v15 schema:
 * saved passes survive, and the new tables match what Room expects in v16.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class Migration15To16Test {

    private lateinit var helper: SupportSQLiteOpenHelper
    private lateinit var db: SupportSQLiteDatabase

    private fun schema(version: Int): JSONObject {
        val file = resolveRepoFile("app/schemas/com.lipabill.app.data.local.AppDatabase/$version.json")
            ?: error("schema $version.json not found")
        return JSONObject(file.readText()).getJSONObject("database")
    }

    @Before
    fun openV15() {
        val v15 = schema(15)
        val config = SupportSQLiteOpenHelper.Configuration.builder(RuntimeEnvironment.getApplication())
            .name("migration-test.db")
            .callback(object : SupportSQLiteOpenHelper.Callback(15) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    val entities = v15.getJSONArray("entities")
                    for (i in 0 until entities.length()) {
                        val e = entities.getJSONObject(i)
                        val table = e.getString("tableName")
                        db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", table))
                        val indices = e.optJSONArray("indices") ?: continue
                        for (j in 0 until indices.length()) {
                            db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                        }
                    }
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        helper = FrameworkSQLiteOpenHelperFactory().create(config)
        db = helper.writableDatabase
    }

    @After
    fun close() {
        helper.close()
        RuntimeEnvironment.getApplication().deleteDatabase("migration-test.db")
    }

    @Test
    fun saved_passes_survive_and_new_tables_match_room() {
        db.insert(
            "tickets",
            SQLiteDatabase.CONFLICT_ABORT,
            ContentValues().apply {
                put("title", "Sample Night")
                put("barcodeFormat", "QR_CODE")
                put("barcodeValue", "SAMPLE-CODE-0001")
                put("source", "E_TICKET")
                put("status", "ACTIVE")
                put("createdAtMillis", 1_700_000_000_000L)
                put("expectsBoardingPass", 0)
                put("hasBoardingPass", 1)
                put("hasReturnBoardingPass", 0)
            }
        )

        AppDatabaseMigrations.MIGRATION_15_16.migrate(db)

        db.query("SELECT title, barcodeValue FROM tickets").use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals("Sample Night", c.getString(0))
            assertEquals("SAMPLE-CODE-0001", c.getString(1))
        }

        val v16 = schema(16).getJSONArray("entities")
        for (name in listOf("pass_documents", "document_extractions")) {
            val entity = (0 until v16.length()).map { v16.getJSONObject(it) }
                .firstOrNull { it.getString("tableName") == name }
            assertNotNull("$name missing from v16 schema", entity)
            entity!!
            val expected = entity.getJSONArray("fields").let { f ->
                (0 until f.length()).associate { i ->
                    val field = f.getJSONObject(i)
                    field.getString("columnName") to (field.getString("affinity") to field.optBoolean("notNull", false))
                }
            }
            val actual = mutableMapOf<String, Pair<String, Boolean>>()
            db.query("PRAGMA table_info(`$name`)").use { c ->
                while (c.moveToNext()) {
                    actual[c.getString(c.getColumnIndexOrThrow("name"))] =
                        c.getString(c.getColumnIndexOrThrow("type")) to (c.getInt(c.getColumnIndexOrThrow("notnull")) == 1)
                }
            }
            assertEquals("columns of $name", expected, actual)

            val indices = entity.optJSONArray("indices")
            val expectedIndices = indices?.let { a -> (0 until a.length()).map { a.getJSONObject(it).getString("name") }.toSet() }.orEmpty()
            val actualIndices = mutableSetOf<String>()
            db.query("PRAGMA index_list(`$name`)").use { c ->
                while (c.moveToNext()) {
                    val idx = c.getString(c.getColumnIndexOrThrow("name"))
                    if (!idx.startsWith("sqlite_autoindex")) actualIndices += idx
                }
            }
            assertEquals("indices of $name", expectedIndices, actualIndices)
        }
    }

    @Test
    fun migration_is_registered() {
        assertTrue(AppDatabaseMigrations.ALL.contains(AppDatabaseMigrations.MIGRATION_15_16))
    }
}
