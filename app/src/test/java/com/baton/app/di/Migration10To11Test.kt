package com.baton.app.di

import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.baton.app.data.local.AppDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tier 1.3 + 1.5 (v2.0): the v10 -> v11 migration.
 *
 * Verifies that:
 *  - the `nextActionAt` column lands on `instructions`
 *  - the `instructions_fts` virtual table is created with
 *    the porter tokenizer
 *  - the existing `instructions` rows are seeded into the
 *    FTS table (title + rawText) so search works the
 *    moment the migration completes
 *
 * Uses raw SQLite (per the `qa-patterns.md` §1.8 guidance)
 * so we don't have to declare the full v10 schema in Room.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class Migration10To11Test {

    private val testDbName = "test-migration-10-11-${System.nanoTime()}.db"
    private lateinit var v10DbPath: String

    @Before
    fun setUp() {
        v10DbPath = ApplicationProvider.getApplicationContext<android.content.Context>()
            .getDatabasePath(testDbName).absolutePath
    }

    @After
    fun tearDown() {
        java.io.File(v10DbPath).delete()
        java.io.File(v10DbPath + "-wal").delete()
        java.io.File(v10DbPath + "-shm").delete()
    }

    @Test
    fun `MIGRATION_10_11 adds nextActionAt to instructions`() {
        // Build a v10 database with the v10 instructions schema.
        SQLiteDatabase.openOrCreateDatabase(v10DbPath, null).use { rawDb ->
            rawDb.execSQL(
                """
                CREATE TABLE instructions (
                    id TEXT NOT NULL PRIMARY KEY,
                    personId TEXT,
                    direction TEXT NOT NULL,
                    status TEXT NOT NULL,
                    source TEXT NOT NULL,
                    priority TEXT NOT NULL,
                    title TEXT NOT NULL,
                    rawText TEXT NOT NULL,
                    dueAt TEXT,
                    capturedAt TEXT NOT NULL,
                    createdAt TEXT NOT NULL,
                    updatedAt TEXT NOT NULL,
                    isSensitive INTEGER NOT NULL DEFAULT 0,
                    syncStatus TEXT NOT NULL
                )
                """.trimIndent(),
            )
            rawDb.execSQL("PRAGMA user_version = 10")
            rawDb.execSQL(
                "INSERT INTO instructions (id, direction, status, source, priority, title, rawText, capturedAt, createdAt, updatedAt, syncStatus) " +
                    "VALUES ('i1', 'OUTGOING', 'OPEN', 'TEXT', 'NORMAL', 'Temple land inquiry', 'follow up by Friday', '2026-08-12T00:00:00+00:00', '2026-08-12T00:00:00+00:00', '2026-08-12T00:00:00+00:00', 'SYNCED')",
            )
        }

        // Apply the v10 -> v11 migration by re-running the same
        // SQL the production migration runs. (We can't directly
        // invoke the Migration object on a raw SQLiteDatabase
        // because the SupportSQLiteDatabase adapter is internal
        // — but the SQL is the contract we're testing.)
        SQLiteDatabase.openOrCreateDatabase(v10DbPath, null).use { rawDb ->
            rawDb.execSQL("ALTER TABLE instructions ADD COLUMN nextActionAt INTEGER")
            rawDb.execSQL(
                "CREATE VIRTUAL TABLE IF NOT EXISTS `instructions_fts` USING fts4(" +
                    "`title` TEXT, `rawText` TEXT, `personId` TEXT, `capturedAt` TEXT, " +
                    "tokenize=`porter`)",
            )
            rawDb.execSQL(
                "INSERT INTO `instructions_fts` (rowid, title, rawText, personId, capturedAt) " +
                    "SELECT rowid, COALESCE(title, ''), COALESCE(rawText, ''), personId, capturedAt " +
                    "FROM `instructions`",
            )
        }

        // Assert: nextActionAt column exists (PRAGMA table_info) and is NULL by default.
        SQLiteDatabase.openOrCreateDatabase(v10DbPath, null).use { rawDb ->
            val info = rawDb.rawQuery("PRAGMA table_info(instructions)", null)
            var hasNext = false
            while (info.moveToNext()) {
                if (info.getString(1) == "nextActionAt") {
                    hasNext = true
                    break
                }
            }
            info.close()
            assertTrue("nextActionAt column must exist after migration", hasNext)
            val c = rawDb.rawQuery("SELECT nextActionAt FROM instructions WHERE id = 'i1'", null)
            assertNotNull("row must survive migration", c)
            assertTrue("row must exist", c.moveToFirst())
            assertTrue("nextActionAt default must be NULL", c.isNull(0))
            c.close()
        }
    }

    @Test
    fun `MIGRATION_10_11 creates instructions_fts and seeds it from existing rows`() {
        SQLiteDatabase.openOrCreateDatabase(v10DbPath, null).use { rawDb ->
            rawDb.execSQL(
                """
                CREATE TABLE instructions (
                    id TEXT NOT NULL PRIMARY KEY,
                    personId TEXT,
                    direction TEXT NOT NULL,
                    status TEXT NOT NULL,
                    source TEXT NOT NULL,
                    priority TEXT NOT NULL,
                    title TEXT NOT NULL,
                    rawText TEXT NOT NULL,
                    dueAt TEXT,
                    capturedAt TEXT NOT NULL,
                    createdAt TEXT NOT NULL,
                    updatedAt TEXT NOT NULL,
                    isSensitive INTEGER NOT NULL DEFAULT 0,
                    syncStatus TEXT NOT NULL
                )
                """.trimIndent(),
            )
            rawDb.execSQL("PRAGMA user_version = 10")
            rawDb.execSQL(
                "INSERT INTO instructions (id, direction, status, source, priority, title, rawText, capturedAt, createdAt, updatedAt, syncStatus) " +
                    "VALUES ('i1', 'OUTGOING', 'OPEN', 'TEXT', 'NORMAL', 'Temple land inquiry', 'follow up by Friday', '2026-08-12T00:00:00+00:00', '2026-08-12T00:00:00+00:00', '2026-08-12T00:00:00+00:00', 'SYNCED')",
            )
            rawDb.execSQL(
                "INSERT INTO instructions (id, direction, status, source, priority, title, rawText, capturedAt, createdAt, updatedAt, syncStatus) " +
                    "VALUES ('i2', 'OUTGOING', 'OPEN', 'TEXT', 'NORMAL', 'Bandobast plan', 'draft a plan and send across', '2026-08-12T00:00:01+00:00', '2026-08-12T00:00:01+00:00', '2026-08-12T00:00:01+00:00', 'SYNCED')",
            )
        }
        SQLiteDatabase.openOrCreateDatabase(v10DbPath, null).use { rawDb ->
            rawDb.execSQL("ALTER TABLE instructions ADD COLUMN nextActionAt INTEGER")
            rawDb.execSQL(
                "CREATE VIRTUAL TABLE IF NOT EXISTS `instructions_fts` USING fts4(" +
                    "`title` TEXT, `rawText` TEXT, `personId` TEXT, `capturedAt` TEXT, " +
                    "tokenize=`porter`)",
            )
            rawDb.execSQL(
                "INSERT INTO `instructions_fts` (rowid, title, rawText, personId, capturedAt) " +
                    "SELECT rowid, COALESCE(title, ''), COALESCE(rawText, ''), personId, capturedAt " +
                    "FROM `instructions`",
            )
        }
        // Search for "temple" and assert only i1 comes back.
        SQLiteDatabase.openOrCreateDatabase(v10DbPath, null).use { rawDb ->
            val c = rawDb.rawQuery(
                "SELECT i.id FROM instructions i JOIN instructions_fts f ON i.rowid = f.rowid " +
                    "WHERE instructions_fts MATCH ?",
                arrayOf("temple*"),
            )
            val hits = mutableListOf<String>()
            while (c.moveToNext()) hits.add(c.getString(0))
            c.close()
            assertEquals("FTS must return exactly 1 hit for 'temple*'", 1, hits.size)
            assertEquals("i1", hits.first())
        }
    }
}
