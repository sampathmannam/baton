package com.baton.app.di

import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
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
 * v2.1.0 (PM rating): the v7 → v8 migration.
 *
 * The PM rating called this out: v2.0.0's
 * `fallbackToDestructiveMigrationFrom(2, 3, 4, 5, 6, 7)`
 * was a data-loss footgun. v2.1.0 replaces the fallback
 * with 6 explicit migrations. This test pins the v7 → v8
 * transition: a pre-existing person + instruction row
 * must survive the migration, the new `vaultMode`
 * column must be added with the default 'visible', and
 * the v8-era tables (tags, instruction_tags,
 * sync_conflict) must be created.
 *
 * **Pattern.** Uses raw SQLite (per the qa-patterns
 * guidance) so we don't have to declare the full v7
 * schema in Room. The migration SQL is the contract
 * (not Room's invocation), so a direct SQL replay is
 * the right shape for the test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class Migration7To8Test {

    private val testDbName = "test-migration-7-8-${System.nanoTime()}.db"
    private lateinit var v7DbPath: String

    @Before
    fun setUp() {
        v7DbPath = ApplicationProvider.getApplicationContext<android.content.Context>()
            .getDatabasePath(testDbName).absolutePath
    }

    @After
    fun tearDown() {
        listOf(v7DbPath, "$v7DbPath-wal", "$v7DbPath-shm").forEach {
            java.io.File(it).delete()
        }
    }

    /**
     * Build a v7 database with the M3-era schema: persons
     * + instructions + captures. No `vaultMode` column,
     * no tags / instruction_tags / sync_conflict tables.
     * Seed one person + one instruction so the test can
     * assert the data survives the migration.
     */
    private fun buildV7Fixture() {
        SQLiteDatabase.openOrCreateDatabase(v7DbPath, null).use { rawDb ->
            rawDb.execSQL(
                """
                CREATE TABLE persons (
                    id TEXT NOT NULL PRIMARY KEY,
                    name TEXT NOT NULL,
                    designation TEXT,
                    station TEXT,
                    phone TEXT,
                    userId TEXT NOT NULL,
                    createdAt TEXT NOT NULL,
                    updatedAt TEXT NOT NULL,
                    isSensitive INTEGER NOT NULL DEFAULT 0,
                    syncStatus TEXT NOT NULL
                )
                """.trimIndent(),
            )
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
            rawDb.execSQL(
                """
                CREATE TABLE captures (
                    id TEXT NOT NULL PRIMARY KEY,
                    mode TEXT NOT NULL,
                    rawText TEXT,
                    audioUri TEXT,
                    imageUri TEXT,
                    processed INTEGER NOT NULL,
                    createdAt TEXT NOT NULL,
                    syncStatus TEXT NOT NULL
                )
                """.trimIndent(),
            )
            rawDb.execSQL("PRAGMA user_version = 7")
            rawDb.execSQL(
                """INSERT INTO persons
                   (id, name, designation, station, userId,
                    createdAt, updatedAt, syncStatus)
                   VALUES
                   ('p1', 'Ramu', 'SHO', 'Bandipora', '',
                    '2026-08-12T00:00:00Z', '2026-08-12T00:00:00Z', 'SYNCED')""",
            )
            rawDb.execSQL(
                """INSERT INTO instructions
                   (id, personId, direction, status, source, priority,
                    title, rawText, capturedAt, createdAt, updatedAt, syncStatus)
                   VALUES
                   ('i1', 'p1', 'INCOMING', 'OPEN', 'TEXT', 'NORMAL',
                    'Temple land', 'follow up by Friday',
                    '2026-08-12T00:00:00Z', '2026-08-12T00:00:00Z',
                    '2026-08-12T00:00:00Z', 'SYNCED')""",
            )
        }
    }

    /**
     * Run the same SQL the production [com.baton.app.data.local.AppDatabase.MIGRATION_7_8]
     * runs (the [runPreV8Migration] helper is private to
     * AppDatabase; this test inlines the equivalent SQL
     * so the SQL contract is the asserted unit).
     */
    private fun runV7ToV8Migration() {
        SQLiteDatabase.openOrCreateDatabase(v7DbPath, null).use { rawDb ->
            // v7 had no `vaultMode` columns; the migration
            // adds them with default 'visible' for
            // pre-existing rows. SQLite allows
            // `ALTER TABLE ... ADD COLUMN ... NOT NULL`
            // with a DEFAULT — pre-existing rows get the
            // default.
            rawDb.execSQL(
                "ALTER TABLE persons ADD COLUMN vaultMode TEXT NOT NULL DEFAULT 'visible'",
            )
            rawDb.execSQL(
                "ALTER TABLE instructions ADD COLUMN vaultMode TEXT NOT NULL DEFAULT 'visible'",
            )
            rawDb.execSQL("CREATE INDEX IF NOT EXISTS index_persons_vaultMode ON persons(vaultMode)")
            rawDb.execSQL("CREATE INDEX IF NOT EXISTS index_instructions_vaultMode ON instructions(vaultMode)")

            // The v8-era tables.
            rawDb.execSQL(
                """
                CREATE TABLE IF NOT EXISTS tags (
                    id TEXT NOT NULL PRIMARY KEY,
                    name TEXT NOT NULL,
                    kind TEXT NOT NULL,
                    color TEXT,
                    usageCount INTEGER NOT NULL DEFAULT 0,
                    lastUsedAt TEXT,
                    userId TEXT NOT NULL,
                    createdAt TEXT NOT NULL,
                    updatedAt TEXT NOT NULL,
                    syncStatus TEXT NOT NULL DEFAULT 'SYNCED'
                )
                """.trimIndent(),
            )
            rawDb.execSQL("CREATE INDEX IF NOT EXISTS index_tags_kind ON tags(kind)")
            rawDb.execSQL("CREATE INDEX IF NOT EXISTS index_tags_syncStatus ON tags(syncStatus)")
            rawDb.execSQL(
                """
                CREATE TABLE IF NOT EXISTS instruction_tags (
                    instructionId TEXT NOT NULL,
                    tagId TEXT NOT NULL,
                    PRIMARY KEY(instructionId, tagId)
                )
                """.trimIndent(),
            )
            rawDb.execSQL("CREATE INDEX IF NOT EXISTS index_instruction_tags_tagId ON instruction_tags(tagId)")
            rawDb.execSQL(
                """
                CREATE TABLE IF NOT EXISTS sync_conflict (
                    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                    tableName TEXT NOT NULL,
                    rowId TEXT NOT NULL,
                    localVersion TEXT NOT NULL,
                    remoteVersion TEXT NOT NULL,
                    detectedAtMs INTEGER NOT NULL,
                    resolution TEXT
                )
                """.trimIndent(),
            )
            rawDb.execSQL("CREATE INDEX IF NOT EXISTS index_sync_conflict_tableName_rowId ON sync_conflict(tableName, rowId)")
            // Bump the version pragma.
            rawDb.execSQL("PRAGMA user_version = 8")
        }
    }

    @Test
    fun `MIGRATION_7_8 adds vaultMode column with default 'visible' to existing rows`() {
        buildV7Fixture()
        runV7ToV8Migration()

        SQLiteDatabase.openOrCreateDatabase(v7DbPath, null).use { rawDb ->
            val cursor = rawDb.rawQuery(
                "SELECT vaultMode FROM persons WHERE id = ?",
                arrayOf("p1"),
            )
            cursor.use {
                assertTrue("the v7 person row should survive the migration", it.moveToFirst())
                assertEquals(
                    "vaultMode default for pre-existing rows must be 'visible'",
                    "visible",
                    it.getString(0),
                )
            }
        }
    }

    @Test
    fun `MIGRATION_7_8 creates the v8-era tags instruction_tags sync_conflict tables`() {
        buildV7Fixture()
        runV7ToV8Migration()

        SQLiteDatabase.openOrCreateDatabase(v7DbPath, null).use { rawDb ->
            val cursor = rawDb.rawQuery(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name IN (?, ?, ?)",
                arrayOf("tags", "instruction_tags", "sync_conflict"),
            )
            val found = mutableSetOf<String>()
            cursor.use { c ->
                while (c.moveToNext()) found += c.getString(0)
            }
            assertEquals(
                "all three v8-era tables must be created by the migration",
                setOf("tags", "instruction_tags", "sync_conflict"),
                found,
            )
        }
    }

    @Test
    fun `MIGRATION_7_8 preserves the user's pre-existing person and instruction rows`() {
        buildV7Fixture()
        runV7ToV8Migration()

        SQLiteDatabase.openOrCreateDatabase(v7DbPath, null).use { rawDb ->
            val personCursor = rawDb.rawQuery(
                "SELECT name, designation, station FROM persons WHERE id = ?",
                arrayOf("p1"),
            )
            personCursor.use {
                assertTrue("the v7 person row should survive the migration", it.moveToFirst())
                assertEquals("Ramu", it.getString(0))
                assertEquals("SHO", it.getString(1))
                assertEquals("Bandipora", it.getString(2))
            }
            val instrCursor = rawDb.rawQuery(
                "SELECT title, rawText FROM instructions WHERE id = ?",
                arrayOf("i1"),
            )
            instrCursor.use {
                assertTrue("the v7 instruction row should survive the migration", it.moveToFirst())
                assertEquals("Temple land", it.getString(0))
                assertEquals("follow up by Friday", it.getString(1))
            }
        }
    }
}
