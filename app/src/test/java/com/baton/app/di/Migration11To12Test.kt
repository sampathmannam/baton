package com.baton.app.di

import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.baton.app.data.local.AppDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v2.0 Tier 2 (data layer) regression test for the v11 -> v12
 * migration. We exercise the same SQL strings the production
 * migration runs (mirrored from the [com.baton.app.data.local.AppDatabase.MIGRATION_11_12]
 * block), then assert the on-disk schema.
 *
 * The raw-SQLite pattern is the same as
 * [com.baton.app.data.local.SyncQueueDaoDedupTest] and
 * `Migration8To9Test`; Room's post-migration schema check
 * would require us to stand up the full v11 entity graph
 * (12 entities + cross-refs) just to assert the migration SQL,
 * which is noise. Mirroring the SQL here keeps the test
 * focused.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class Migration11To12Test {

    private val testDbName = "test-migration-11-12-${System.nanoTime()}.db"
    private lateinit var v11DbPath: String

    @Before
    fun setUp() {
        v11DbPath = ApplicationProvider.getApplicationContext<android.content.Context>()
            .getDatabasePath(testDbName).absolutePath
    }

    @After
    fun tearDown() {
        listOf(v11DbPath, "$v11DbPath-wal", "$v11DbPath-shm").forEach {
            java.io.File(it).delete()
        }
    }

    @Test
    fun `migration SQL adds Tier 2 columns to persons`() {
        buildMinimalV11AndApplyMigration()
        SQLiteDatabase.openOrCreateDatabase(v11DbPath, null).use { db ->
            val cols = queryColumns(db, "persons")
            listOf("tier", "cadenceOverrideDays", "lastInteractionAt").forEach { col ->
                assertTrue("persons must have column '$col' after migration; got: $cols",
                    col in cols)
            }
        }
    }

    @Test
    fun `migration SQL adds Tier 2 columns to instructions`() {
        buildMinimalV11AndApplyMigration()
        SQLiteDatabase.openOrCreateDatabase(v11DbPath, null).use { db ->
            val cols = queryColumns(db, "instructions")
            listOf("caseType", "urgency", "reviewAtEpochDay").forEach { col ->
                assertTrue("instructions must have column '$col' after migration; got: $cols",
                    col in cols)
            }
        }
    }

    @Test
    fun `migration SQL adds Tier 2 columns to captures`() {
        buildMinimalV11AndApplyMigration()
        SQLiteDatabase.openOrCreateDatabase(v11DbPath, null).use { db ->
            val cols = queryColumns(db, "captures")
            listOf("ocrText", "calendarEventId", "urgency", "reviewAtEpochDay").forEach { col ->
                assertTrue("captures must have column '$col' after migration; got: $cols",
                    col in cols)
            }
        }
    }

    @Test
    fun `migration SQL creates important_date table`() {
        buildMinimalV11AndApplyMigration()
        SQLiteDatabase.openOrCreateDatabase(v11DbPath, null).use { db ->
            val c = db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='important_date'",
                null,
            )
            assertTrue("important_date table must exist after migration", c.moveToFirst())
            c.close()
        }
    }

    @Test
    fun `migration SQL creates person_link table`() {
        buildMinimalV11AndApplyMigration()
        SQLiteDatabase.openOrCreateDatabase(v11DbPath, null).use { db ->
            val c = db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='person_link'",
                null,
            )
            assertTrue("person_link table must exist after migration", c.moveToFirst())
            c.close()
        }
    }

    @Test
    fun `migration SQL urgency defaults to normal on existing instructions`() {
        SQLiteDatabase.openOrCreateDatabase(v11DbPath, null).use { db ->
            seedV11PersonsAndInstructionsAndCaptures(db)
            db.execSQL(
                "INSERT INTO instructions (id, personId, direction, status, source, priority, " +
                    "title, rawText, capturedAt, createdAt, updatedAt, syncStatus) " +
                    "VALUES ('ins-1', 'p1', 'INCOMING', 'OPEN', 'TEXT', 'NORMAL', 't', 'r', " +
                    "'2026-08-12T00:00:00Z', '2026-08-12T00:00:00Z', '2026-08-12T00:00:00Z', 'SYNCED')",
            )
        }
        applyMigrationSql()
        SQLiteDatabase.openOrCreateDatabase(v11DbPath, null).use { db ->
            val c = db.rawQuery(
                "SELECT urgency FROM instructions WHERE id='ins-1'",
                null,
            )
            assertTrue("Instruction must still exist", c.moveToFirst())
            assertEquals("normal", c.getString(0))
            c.close()
        }
    }

    @Test
    fun `migration SQL tier defaults to Active on existing persons`() {
        SQLiteDatabase.openOrCreateDatabase(v11DbPath, null).use { db ->
            seedV11PersonsAndInstructionsAndCaptures(db)
            db.execSQL(
                "INSERT INTO persons (id, name, userId, createdAt, updatedAt, syncStatus) " +
                    "VALUES ('p1', 'Inspector Kavitha', 'u1', " +
                    "'2026-08-12T00:00:00Z', '2026-08-12T00:00:00Z', 'SYNCED')",
            )
        }
        applyMigrationSql()
        SQLiteDatabase.openOrCreateDatabase(v11DbPath, null).use { db ->
            val c = db.rawQuery(
                "SELECT tier FROM persons WHERE id='p1'",
                null,
            )
            assertTrue(c.moveToFirst())
            assertEquals("Active", c.getString(0))
            c.close()
        }
    }

    private fun seedV11PersonsAndInstructionsAndCaptures(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE persons (
                id TEXT NOT NULL,
                name TEXT NOT NULL,
                designation TEXT, station TEXT, phone TEXT,
                userId TEXT NOT NULL,
                createdAt TEXT NOT NULL, updatedAt TEXT NOT NULL,
                isSensitive INTEGER NOT NULL DEFAULT 0,
                syncStatus TEXT NOT NULL,
                PRIMARY KEY(id)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE instructions (
                id TEXT NOT NULL,
                personId TEXT,
                direction TEXT NOT NULL, status TEXT NOT NULL,
                source TEXT NOT NULL, priority TEXT NOT NULL,
                title TEXT NOT NULL, rawText TEXT NOT NULL,
                dueAt TEXT,
                capturedAt TEXT NOT NULL, createdAt TEXT NOT NULL, updatedAt TEXT NOT NULL,
                isSensitive INTEGER NOT NULL DEFAULT 0,
                syncStatus TEXT NOT NULL,
                completedAt TEXT, droppedReason TEXT,
                nextActionAt INTEGER,
                PRIMARY KEY(id)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE captures (
                id TEXT NOT NULL,
                mode TEXT NOT NULL,
                rawText TEXT, audioUri TEXT, imageUri TEXT,
                processed INTEGER NOT NULL DEFAULT 0,
                createdAt TEXT NOT NULL,
                syncStatus TEXT NOT NULL,
                PRIMARY KEY(id)
            )
            """.trimIndent(),
        )
    }

    // --- helpers ---

    private fun queryColumns(db: SQLiteDatabase, table: String): List<String> {
        val c = db.rawQuery("PRAGMA table_info($table)", null)
        val out = mutableListOf<String>()
        while (c.moveToNext()) out.add(c.getString(1))
        c.close()
        return out
    }

    /**
     * Build a minimal v11 schema covering persons, instructions,
     * captures, then run the migration SQL directly.
     */
    private fun buildMinimalV11AndApplyMigration() {
        SQLiteDatabase.openOrCreateDatabase(v11DbPath, null).use { db ->
            seedV11PersonsAndInstructionsAndCaptures(db)
        }
        applyMigrationSql()
    }

    /**
     * Mirror the production migration SQL exactly. The production
     * version is a [androidx.room.migration.Migration]; here we
     * run the same `execSQL` statements so we test the SQL
     * without the SupportSQLiteDatabase shim.
     */
    private fun applyMigrationSql() {
        SQLiteDatabase.openOrCreateDatabase(v11DbPath, null).use { db ->
            db.execSQL("ALTER TABLE persons ADD COLUMN tier TEXT NOT NULL DEFAULT 'Active'")
            db.execSQL("ALTER TABLE persons ADD COLUMN cadenceOverrideDays INTEGER")
            db.execSQL("ALTER TABLE persons ADD COLUMN lastInteractionAt INTEGER")
            db.execSQL("ALTER TABLE instructions ADD COLUMN caseType TEXT")
            db.execSQL("ALTER TABLE instructions ADD COLUMN urgency TEXT NOT NULL DEFAULT 'normal'")
            db.execSQL("ALTER TABLE instructions ADD COLUMN reviewAtEpochDay INTEGER")
            db.execSQL("ALTER TABLE captures ADD COLUMN ocrText TEXT")
            db.execSQL("ALTER TABLE captures ADD COLUMN calendarEventId TEXT")
            db.execSQL("ALTER TABLE captures ADD COLUMN urgency TEXT NOT NULL DEFAULT 'normal'")
            db.execSQL("ALTER TABLE captures ADD COLUMN reviewAtEpochDay INTEGER")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `important_date` (
                    `id` TEXT NOT NULL,
                    `personId` TEXT NOT NULL,
                    `label` TEXT NOT NULL,
                    `dateEpochDay` INTEGER NOT NULL,
                    `recurring` INTEGER NOT NULL,
                    `createdAt` TEXT NOT NULL,
                    `updatedAt` TEXT NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`personId`) REFERENCES `persons`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_important_date_personId` ON `important_date`(`personId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_important_date_dateEpochDay` ON `important_date`(`dateEpochDay`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `person_link` (
                    `fromId` TEXT NOT NULL,
                    `toId` TEXT NOT NULL,
                    `relation` TEXT NOT NULL,
                    `createdAt` TEXT NOT NULL,
                    PRIMARY KEY(`fromId`, `toId`, `relation`),
                    FOREIGN KEY(`fromId`) REFERENCES `persons`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`toId`) REFERENCES `persons`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_person_link_toId` ON `person_link`(`toId`)")
        }
    }
}
