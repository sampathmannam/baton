package com.baton.app.di

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.sqlite.db.SupportSQLiteDatabase
import com.baton.app.data.local.AppDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v1.2.1 regression test (BUG-DATA-009).
 *
 * Locks the property that [DatabaseModule.foreignKeysCallback]
 * (which is wired into [DatabaseModule.provideDatabase]) sets
 * `PRAGMA foreign_keys = ON` on every Room open. SQLite's
 * `PRAGMA foreign_keys` is OFF by default for every connection
 * (it's a runtime, per-connection setting, not a schema flag).
 * Without it, `ON DELETE CASCADE` is silently ignored and a
 * deleted parent leaves orphan rows.
 *
 * The test installs [DatabaseModule.foreignKeysCallback] on an
 * in-memory Room database (no SQLCipher — Robolectric doesn't
 * package the native lib) and asserts that the resulting database
 * reports `PRAGMA foreign_keys = 1` (on).
 *
 * If anyone removes the `execSQL("PRAGMA foreign_keys = ON")`
 * call in [DatabaseModule.foreignKeysCallback], this test fails.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DatabaseModuleTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .addCallback(DatabaseModule.foreignKeysCallback())
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `BUG-DATA-009 PRAGMA foreign_keys is ON after Room open`() {
        val cursor = db.openHelper.readableDatabase.query("PRAGMA foreign_keys")
        cursor.moveToFirst()
        val value = cursor.getInt(0)
        cursor.close()
        // 1 = ON, 0 = OFF. SQLite's foreign_keys PRAGMA returns
        // an integer; we MUST be 1 — otherwise ON DELETE CASCADE
        // is silently ignored.
        assertEquals("PRAGMA foreign_keys must be 1 (ON) — otherwise ON DELETE CASCADE is silently ignored", 1, value)
    }

    @Test
    fun `BUG-DATA-009 PRAGMA foreign_keys is ON for a fresh open too (no first-read required)`() {
        // Close + reopen to confirm the PRAGMA is set on the
        // connection, not just lazily on first query. (PRAGMA
        // foreign_keys is per-connection; it must be set on
        // every open, including subsequent reopens.)
        db.close()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db2 = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .addCallback(DatabaseModule.foreignKeysCallback())
            .build()
        try {
            val cursor = db2.openHelper.readableDatabase.query("PRAGMA foreign_keys")
            cursor.moveToFirst()
            val value = cursor.getInt(0)
            cursor.close()
            assertEquals(1, value)
        } finally {
            db2.close()
        }
    }
}
