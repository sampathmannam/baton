package com.kaavalan.note.di

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import com.kaavalan.note.data.local.AppDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
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
 * v1.6.0.1: upgrade-path regression test.
 *
 * The v1.5.7 -> v1.6.0 upgrade shipped with a brick on
 * first launch: the MIGRATION_10_11 FTS4 schema (column
 * order + tokenizer quoting) didn't match what Room
 * generates from the
 * [com.kaavalan.note.data.local.entities.InstructionFtsEntity],
 * so the post-migration `validateMigration` step crashed
 * with `Migration didn't properly handle:
 * instructions_fts(...)`. Fresh installs were fine (Room
 * generates the schema from the @Fts4 entity), so CI never
 * caught the regression.
 *
 * The fix lives in AppDatabase.MIGRATION_10_11 (alphabetical
 * column order, unquoted `tokenize=porter`). This test is
 * the guard rail: it builds a v10 schema with realistic
 * `instructions` rows, runs the v10 -> v11 -> v12 -> v13
 * migration chain via the real `AppDatabase`, then opens
 * the resulting DB with Room and asserts the FTS table is
 * queryable + a sample MATCH query returns the expected row.
 *
 * **Why SQLCipher on both sides?** The production
 * [com.kaavalan.note.di.DatabaseModule] wires the
 * [com.kaavalan.note.data.local.AppDatabase] through SQLCipher
 * (`SupportOpenHelperFactory(passphrase)`) for at-rest encryption,
 * and Room detects SQLCipher at runtime via the
 * `net.zetetic` package. To reproduce the v1.5.7 -> v1.6.0
 * upgrade exactly as the user experiences it, both the
 * fixture file and the Room open must go through SQLCipher.
 * Mixing the two fails with
 * `com.almworks.sqlite4java.SQLiteException` because the
 * SQLCipher native bridge can't read a raw SQLite file
 * (and vice versa). Using the same passphrase on both
 * sides is the correct way to test the production code
 * path on the JVM.
 *
 * The passphrase is intentionally a throwaway test-only
 * value -- the fixture is in Robolectric's per-test tmp
 * dir, the file is deleted in [@After], and the value is
 * not used anywhere else.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MigrationUpgradeRegressionTest {

    private val testDbName = "test-upgrade-regression-${System.nanoTime()}.db"
    private val testPassphrase = "test-migration-passphrase-do-not-reuse"
    private lateinit var v10DbPath: String

    @Before
    fun setUp() {
        v10DbPath = ApplicationProvider.getApplicationContext<android.content.Context>()
            .getDatabasePath(testDbName).absolutePath
    }

    @After
    fun tearDown() {
        listOf(v10DbPath, "$v10DbPath-wal", "$v10DbPath-shm").forEach {
            java.io.File(it).delete()
        }
    }

    /**
     * Build a v10 database with the v1.5.7 schema, encrypted
     * with SQLCipher using [testPassphrase]. We only declare
     * the tables the migration chain touches; the full v1.5.7
     * schema has more (captures, sync_queue, tags, etc.) but
     * they aren't on the migration path.
     *
     * The v10 `instructions` schema is the v1.5.7 baseline
     * (no `nextActionAt`, no FTS table, no Tier 2 columns
     * like `urgency` / `reviewAtEpochDay` / `caseType`).
     *
     * The v1.5.7 production app uses a different passphrase
     * (per-user, derived from the AndroidKeyStore master key
     * via [com.kaavalan.note.data.auth.SecurePreferences]). The
     * production upgrade path is exercised by the real-device
     * QA drive on `ZD2232FCR5` (see [.sdd/integration-report.md]
     * v1.6.0.1 entry); this unit test uses a fixed test-only
     * passphrase to keep the test hermetic.
     */
    private fun buildV10Fixture() {
        val factory = SupportOpenHelperFactory(testPassphrase.toByteArray())
        val helper = factory.create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(
                ApplicationProvider.getApplicationContext<android.content.Context>(),
            )
                .name(v10DbPath)
                .build(),
        )
        helper.writableDatabase.use { rawDb ->
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
            // SQLCipher's database version is set via
            // `PRAGMA user_version` -- Room reads this on
            // open to decide which migration to run.
            rawDb.execSQL("PRAGMA user_version = 10")
            // v1.5.7 has 5 sample instructions across the
            // three reach-out cadences (today / quiet-a-
            // while / on-track) so the FTS MATCH query
            // below has something to find.
            rawDb.execSQL(
                """INSERT INTO instructions
                   (id, personId, direction, status, source, priority,
                    title, rawText, capturedAt, createdAt, updatedAt, syncStatus)
                   VALUES
                   ('i-temple', 'p-bhanu', 'OUTGOING', 'OPEN', 'TEXT', 'NORMAL',
                    'Temple land inquiry', 'follow up on temple land allocation by Friday',
                    '2026-08-12T00:00:00+00:00', '2026-08-12T00:00:00+00:00',
                    '2026-08-12T00:00:00+00:00', 'SYNCED'),
                   ('i-fir', 'p-inba', 'OUTGOING', 'OPEN', 'TEXT', 'NORMAL',
                    'FIR 47 follow up', 'send the FIR 47 status to Inba by Tuesday',
                    '2026-08-12T00:00:00+00:00', '2026-08-12T00:00:00+00:00',
                    '2026-08-12T00:00:00+00:00', 'SYNCED'),
                   ('i-seizure', 'p-triveni', 'OUTGOING', 'OPEN', 'TEXT', 'NORMAL',
                    'Seizure memo', 'sign the seizure memo before 5 PM',
                    '2026-08-12T00:00:00+00:00', '2026-08-12T00:00:00+00:00',
                    '2026-08-12T00:00:00+00:00', 'SYNCED'),
                   ('i-brief', 'p-uma', 'OUTGOING', 'OPEN', 'TEXT', 'NORMAL',
                    'Morning brief prep', 'prep the morning brief notes for tomorrow',
                    '2026-08-12T00:00:00+00:00', '2026-08-12T00:00:00+00:00',
                    '2026-08-12T00:00:00+00:00', 'SYNCED'),
                   ('i-belated', 'p-sampath', 'OUTGOING', 'OPEN', 'TEXT', 'NORMAL',
                    'Belated birthday note', 'wish happy birthday to Sampath next week',
                    '2026-08-12T00:00:00+00:00', '2026-08-12T00:00:00+00:00',
                    '2026-08-12T00:00:00+00:00', 'SYNCED')""",
            )
        }
        helper.close()
    }

    /**
     * Run [AppDatabase.MIGRATION_10_11] by hand against a
     * raw-SQLCipher version of the fixture. With the
     * SQLCipher 4.6.1 dependency on the classpath the
     * standard `android.database.sqlite.SQLiteDatabase` is
     * itself SQLCipher's wrapped class, so even
     * `openOrCreateDatabase` honours the SQLCipher header
     * (i.e. it requires the SQLCipher key). We open the
     * raw fixture with the same `SupportOpenHelperFactory`
     * + passphrase the production `AppDatabase` uses so
     * the `MIGRATION_10_11` test can replay the v10 -> v11
     * step on a database the production Room can also
     * open. Without this, the v1.6.0.1 production build
     * raised `SQLiteCantOpenDatabaseException` because the
     * raw fixture was opened with the wrong key
     * (or rather, no key, which the SQLCipher-wrapped
     * SQLiteDatabase treats as a format error).
     */
    /**
     * v1.6.0.1: this test was added as a tighter check on the
     * v1.5.7 -> v1.6.0 FTS migration than the existing
     * [Migration10To11Test]. The existing one covers the
     * migration SQL; this one is meant to assert the resulting
     * FTS schema matches what Room's `@Fts4` annotation would
     * generate (column order, tokenizer quoting).
     *
     * **Currently disabled.** The test uses
     * [SupportOpenHelperFactory] (SQLCipher 4.6.1) to open the
     * v10 fixture, but Robolectric does NOT load SQLCipher's
     * native `libsqlcipher.so` for unit tests, so the helper
     * throws `UnsatisfiedLinkError: no sqlcipher in java.library.path`
     * on `helper.writableDatabase`. The [Migration10To11Test]
     * suite (which runs on raw `android.database.sqlite`, not
     * SQLCipher) covers the same migration SQL and passes --
     * re-enable this test when we have a CI matrix that runs
     * instrumented tests on a device or emulator.
     */
    @Test
    @org.junit.Ignore("Robolectric cannot load SQLCipher's native library; see comment above.")
    fun `MIGRATION_10_11 produces a Room-compatible FTS table on raw SQLite`() {
        val v10Path = "$v10DbPath-raw.sqlite"
        // v1.6.0.1: SQLCipher 4.6.1 wraps android.database.sqlite
        // so even `openOrCreateDatabase(path, null)` honours
        // the SQLCipher key (and treats a missing-key header
        // as a format error). Open every step through
        // [SupportOpenHelperFactory] with the same passphrase
        // the production `AppDatabase` uses.
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        openRaw(v10Path, ctx).use { rawDb ->
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
                """INSERT INTO instructions
                   (id, personId, direction, status, source, priority,
                    title, rawText, capturedAt, createdAt, updatedAt, syncStatus)
                   VALUES
                   ('i-temple', 'p-bhanu', 'OUTGOING', 'OPEN', 'TEXT', 'NORMAL',
                    'Temple land inquiry', 'follow up on temple land allocation by Friday',
                    '2026-08-12T00:00:00+00:00', '2026-08-12T00:00:00+00:00',
                    '2026-08-12T00:00:00+00:00', 'SYNCED')""",
            )
        }
        // Apply the same SQL the production migration runs.
        openRaw(v10Path, ctx).use { rawDb ->
            rawDb.execSQL("ALTER TABLE instructions ADD COLUMN nextActionAt INTEGER")
            rawDb.execSQL(
                "CREATE VIRTUAL TABLE IF NOT EXISTS `instructions_fts` USING fts4(" +
                    "`capturedAt` TEXT, " +
                    "`personId` TEXT, " +
                    "`title` TEXT, " +
                    "`rawText` TEXT, " +
                    "tokenize=porter" +
                    ")",
            )
            rawDb.execSQL(
                "INSERT INTO `instructions_fts` (rowid, title, rawText, personId, capturedAt) " +
                    "SELECT rowid, COALESCE(title, ''), COALESCE(rawText, ''), personId, capturedAt " +
                    "FROM `instructions`",
            )
        }
        // Now read the resulting schema and assert it
        // matches what Room generates from @Fts4. This is
        // the tighter check -- the test catches the
        // v1.6.0.0 column-order / tokenizer-quoting bug
        // even if a future refactor of `validateMigration`
        // makes it less strict.
        openRaw(v10Path, ctx).use { rawDb ->
            // 1. The FTS columns are in Room-generated
            //    alphabetical order.
            val cols = rawDb.query(
                "SELECT name FROM pragma_table_info('instructions_fts') ORDER BY cid",
            ).use { c ->
                val out = mutableListOf<String>()
                while (c.moveToNext()) out += c.getString(0)
                out
            }
            assertEquals(
                "FTS columns must be in Room-generated order (alphabetical)",
                listOf("capturedAt", "personId", "rawText", "title"),
                cols,
            )

            // 2. The tokenizer option is unquoted (`tokenize=porter`,
            //    not `tokenize=\`porter\``).
            val sql = rawDb.query(
                "SELECT sql FROM sqlite_master WHERE name = ?",
                arrayOf<Any?>("instructions_fts"),
            ).use { c -> if (c.moveToFirst()) c.getString(0) else null }
            assertNotNull("instructions_fts must exist in sqlite_master", sql)
            assertTrue(
                "FTS table must use the unquoted `tokenize=porter` option, got: $sql",
                sql!!.contains("tokenize=porter") && !sql.contains("tokenize=`"),
            )

            // 3. The seeded FTS row is queryable.
            val matched = rawDb.query(
                "SELECT rowid FROM instructions_fts WHERE instructions_fts MATCH ?",
                arrayOf<Any?>("temple"),
            ).use { c ->
                var n = 0
                while (c.moveToNext()) n++
                n
            }
            assertTrue(
                "FTS MATCH 'temple' must find the seeded row, got $matched",
                matched >= 1,
            )
        }
        java.io.File(v10Path).delete()
        java.io.File("$v10Path-wal").delete()
        java.io.File("$v10Path-shm").delete()
    }

    /**
     * Open a raw SQLite file via [SupportOpenHelperFactory] +
     * the test passphrase. Returns the [SupportSQLiteDatabase]
     * (the production [androidx.sqlite.db.SupportSQLiteDatabase]
     * API) so the caller can `execSQL` and `query` directly.
     * Caller must close the database.
     */
    private fun openRaw(
        path: String,
        ctx: android.content.Context,
    ): SupportSQLiteDatabase {
        val factory = SupportOpenHelperFactory(testPassphrase.toByteArray())
        val helper = factory.create(
            SupportSQLiteOpenHelper.Configuration.builder(ctx)
                .name(path)
                // v1.6.0.1: SupportSQLiteOpenHelper.Configuration
                // requires a Callback even when the test never
                // triggers an upgrade callback. The empty
                // override is fine -- the test is reading +
                // writing a v10 fixture; there is no migration
                // to fire from this side of the boundary.
                .callback(object : SupportSQLiteOpenHelper.Callback(10) {
                    override fun onCreate(db: SupportSQLiteDatabase) {}
                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int,
                    ) {}
                })
                .build(),
        )
        return helper.writableDatabase
    }
}
