package com.lipabill.app.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class DeferredCipherOpenHelperTest {

    private val db = Proxy.newProxyInstance(
        SupportSQLiteDatabase::class.java.classLoader,
        arrayOf(SupportSQLiteDatabase::class.java)
    ) { _, _, _ -> null } as SupportSQLiteDatabase

    private val configuration = SupportSQLiteOpenHelper.Configuration.builder(
        RuntimeEnvironment.getApplication()
    )
        .name("test.db")
        .callback(object : SupportSQLiteOpenHelper.Callback(1) {
            override fun onCreate(db: SupportSQLiteDatabase) = Unit
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        })
        .build()

    /** Each create() pops the next outcome: true opens, false throws. */
    private class Harness(outcomes: List<Boolean>, val fresh: Boolean) {
        private val queue = ArrayDeque(outcomes)
        var passphraseReads = 0
        var created = 0
        var wipes = 0
        var closes = 0
        val walSettings = mutableListOf<Boolean>()

        fun factory(db: SupportSQLiteDatabase) = DeferredCipherFactory(
            passphrase = { passphraseReads++; ByteArray(32) },
            isFreshInstall = { fresh },
            wipeCipherArtifacts = { wipes++ },
            cipherFactory = {
                SupportSQLiteOpenHelper.Factory {
                    created++
                    val opens = queue.removeFirst()
                    object : SupportSQLiteOpenHelper {
                        override val databaseName: String? = "test.db"
                        override fun setWriteAheadLoggingEnabled(enabled: Boolean) {
                            walSettings += enabled
                        }
                        override val writableDatabase: SupportSQLiteDatabase
                            get() = if (opens) db else throw IllegalStateException("bad key")
                        override val readableDatabase: SupportSQLiteDatabase
                            get() = writableDatabase
                        override fun close() {
                            closes++
                        }
                    }
                }
            }
        )
    }

    @Test
    fun buildingDoesNotTouchPassphraseOrOpen() {
        val h = Harness(listOf(true), fresh = false)
        h.factory(db).create(configuration)
        assertEquals(0, h.passphraseReads)
        assertEquals(0, h.created)
    }

    @Test
    fun opensOnceAndReusesDelegate() {
        val h = Harness(listOf(true), fresh = false)
        val helper = h.factory(db).create(configuration)
        assertSame(db, helper.writableDatabase)
        assertSame(db, helper.readableDatabase)
        assertEquals(1, h.created)
        assertEquals(1, h.passphraseReads)
    }

    @Test
    fun updateRetriesOnceWithoutWiping() {
        val h = Harness(listOf(false, true), fresh = false)
        val helper = h.factory(db).create(configuration)
        assertSame(db, helper.writableDatabase)
        assertEquals(0, h.wipes)
        assertEquals(2, h.created)
        assertEquals(1, h.closes)
    }

    @Test
    fun updateSurfacesSecondFailureAndNeverWipes() {
        val h = Harness(listOf(false, false), fresh = false)
        val helper = h.factory(db).create(configuration)
        try {
            helper.writableDatabase
            fail("expected the open failure to surface")
        } catch (e: IllegalStateException) {
            assertEquals("bad key", e.message)
        }
        assertEquals(0, h.wipes)
    }

    @Test
    fun freshInstallWipesAndRecreates() {
        val h = Harness(listOf(false, true), fresh = true)
        val helper = h.factory(db).create(configuration)
        assertSame(db, helper.writableDatabase)
        assertEquals(1, h.wipes)
        assertEquals(2, h.passphraseReads)
    }

    @Test
    fun writeAheadLoggingSetBeforeOpenReachesDelegate() {
        val h = Harness(listOf(true), fresh = false)
        val helper = h.factory(db).create(configuration)
        helper.setWriteAheadLoggingEnabled(true)
        helper.writableDatabase
        assertTrue(h.walSettings == listOf(true))
    }
}
