package com.kaavalan.note.di

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.kaavalan.note.data.local.AppDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class Migration16To17Test {

    private lateinit var helper: SupportSQLiteOpenHelper
    private lateinit var db: SupportSQLiteDatabase
    private val databaseName = "migration-16-17-${System.nanoTime()}.db"

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(object : SupportSQLiteOpenHelper.Callback(16) {
                    override fun onCreate(db: SupportSQLiteDatabase) = createV16Fixture(db)
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        db = helper.writableDatabase
    }

    @After
    fun tearDown() {
        helper.close()
        ApplicationProvider.getApplicationContext<Context>().deleteDatabase(databaseName)
    }

    @Test
    fun `migration maps every legacy status and priority deterministically`() {
        AppDatabase.MIGRATION_16_17.migrate(db)

        val rows = db.query(
            "SELECT id, status, priority, archivedAtEpochMs, migrationReviewRequired, migrationMetadata " +
                "FROM instructions ORDER BY id",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        MigratedRow(
                            id = cursor.getString(0),
                            status = cursor.getString(1),
                            priority = cursor.getString(2),
                            archivedAt = if (cursor.isNull(3)) null else cursor.getLong(3),
                            review = cursor.getInt(4) != 0,
                            metadata = if (cursor.isNull(5)) null else cursor.getString(5),
                        ),
                    )
                }
            }
        }.associateBy { it.id }

        assertEquals(8, rows.size)
        assertEquals("TO_DO", rows.getValue("1-open").status)
        assertEquals("WAITING", rows.getValue("2-ack").status)
        assertEquals("TO_DO", rows.getValue("3-progress").status)
        assertEquals("WAITING", rows.getValue("4-waiting").status)
        assertEquals("DONE", rows.getValue("5-done").status)
        assertEquals("TO_DO", rows.getValue("6-carried").status)
        assertEquals("TO_DO", rows.getValue("7-dropped").status)
        assertEquals("TO_DO", rows.getValue("8-ambiguous").status)
        assertFalse(rows.getValue("3-progress").review)
        assertTrue(rows.getValue("8-ambiguous").review)
        assertEquals("URGENT", rows.getValue("3-progress").priority)
        assertEquals("NORMAL", rows.getValue("6-carried").priority)
        assertEquals(1_788_170_400_000L, rows.getValue("7-dropped").archivedAt)
        assertEquals("legacy_status=DROPPED", rows.getValue("7-dropped").metadata)
    }

    @Test
    fun `migration preserves original fields person links and recoverable attachments`() {
        AppDatabase.MIGRATION_16_17.migrate(db)

        db.query(
            "SELECT rawText, title, dueAt, capturedAt, createdAt, updatedAt, personId, " +
                "actionSummary, hardDeadlineAtEpochMs, followUpAtEpochMs, groupLabel, localRevision " +
                "FROM instructions WHERE id = '1-open'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("raw-1-open", cursor.getString(0))
            assertEquals("title-1-open", cursor.getString(1))
            assertEquals("2026-09-03T10:00:00Z", cursor.getString(2))
            assertEquals("2026-08-30T10:00:00Z", cursor.getString(3))
            assertEquals("2026-08-30T10:00:00Z", cursor.getString(4))
            assertEquals("2026-08-31T10:00:00Z", cursor.getString(5))
            assertEquals("person-1-open", cursor.getString(6))
            assertEquals("title-1-open", cursor.getString(7))
            assertEquals(1_788_134_400_000L, cursor.getLong(8))
            assertEquals(1_788_091_200_000L, cursor.getLong(9))
            assertEquals("Legacy group", cursor.getString(10))
            assertEquals(1L, cursor.getLong(11))
        }
        db.query("SELECT instructionId, localPath FROM attachments").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("1-open", cursor.getString(0))
            assertEquals("files/report.pdf", cursor.getString(1))
        }
    }

    private fun createV16Fixture(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE instructions (
                id TEXT NOT NULL PRIMARY KEY, personId TEXT, direction TEXT NOT NULL,
                status TEXT NOT NULL, source TEXT NOT NULL, priority TEXT NOT NULL,
                title TEXT NOT NULL, rawText TEXT NOT NULL, dueAt TEXT,
                capturedAt TEXT NOT NULL, createdAt TEXT NOT NULL, updatedAt TEXT NOT NULL,
                isSensitive INTEGER NOT NULL DEFAULT 0, syncStatus TEXT NOT NULL,
                completedAt TEXT, droppedReason TEXT, nextActionAt INTEGER,
                caseType TEXT, urgency TEXT NOT NULL DEFAULT 'normal', reviewAtEpochDay INTEGER,
                audienceKind TEXT, audienceTarget TEXT, audienceLabel TEXT,
                audienceIsBroadcast INTEGER NOT NULL DEFAULT 0, dueAtMs INTEGER, channel TEXT
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE TABLE attachments (id TEXT NOT NULL PRIMARY KEY, instructionId TEXT NOT NULL, localPath TEXT NOT NULL)")
        val fixtures = listOf(
            arrayOf("1-open", "SELF", "OPEN", "LOW"),
            arrayOf("2-ack", "OUTGOING", "ACK_PENDING", "NORMAL"),
            arrayOf("3-progress", "INCOMING", "IN_PROGRESS", "HIGH"),
            arrayOf("4-waiting", "OUTGOING", "WAITING_ON_OTHER", "URGENT"),
            arrayOf("5-done", "SELF", "DONE", "NORMAL"),
            arrayOf("6-carried", "SELF", "CARRIED_OVER", "LOW"),
            arrayOf("7-dropped", "SELF", "DROPPED", "HIGH"),
            arrayOf("8-ambiguous", "UNKNOWN", "IN_PROGRESS", "NORMAL"),
        )
        fixtures.forEach { (id, direction, status, priority) ->
            db.execSQL(
                """INSERT INTO instructions (
                    id, personId, direction, status, source, priority, title, rawText, dueAt,
                    capturedAt, createdAt, updatedAt, syncStatus, nextActionAt,
                    audienceKind, audienceTarget, audienceLabel, audienceIsBroadcast, dueAtMs
                ) VALUES (?, ?, ?, ?, 'TEXT', ?, ?, ?, '2026-09-03T10:00:00Z',
                    '2026-08-30T10:00:00Z', '2026-08-30T10:00:00Z', '2026-08-31T10:00:00Z',
                    'SYNCED', 1788091200000, 'STATION', 'station-1', 'Legacy group', 1, 1788134400000)
                """.trimIndent(),
                arrayOf(id, "person-$id", direction, status, priority, "title-$id", "raw-$id"),
            )
        }
        db.execSQL("INSERT INTO attachments VALUES ('attachment-1', '1-open', 'files/report.pdf')")
    }

    private data class MigratedRow(
        val id: String,
        val status: String,
        val priority: String,
        val archivedAt: Long?,
        val review: Boolean,
        val metadata: String?,
    )
}
