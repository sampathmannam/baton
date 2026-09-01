package com.kaavalan.note.data.export

import android.app.Application
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kaavalan.note.data.local.AppDatabase
import com.kaavalan.note.data.local.InstructionDao
import com.kaavalan.note.data.local.PersonDao
import com.kaavalan.note.data.local.TagDao
import com.kaavalan.note.data.local.entities.InstructionTagCrossRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.json.JSONArray
import org.json.JSONObject

/**
 * v2.0.1 (PM rating): the CSV/JSON importer. Round-trips a
 * snapshot the [PlainExporter] wrote back into the same
 * DB. Re-importing the same file is idempotent (upsert
 * by id).
 *
 * **Why Robolectric.** The [PlainImporter] needs an
 * [Application] context to open the input URI via
 * [android.content.ContentResolver]. Robolectric provides
 * a real Application with a real ContentResolver (the
 * SAF openInputStream is stubbed via the test URI scheme
 * below).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PlainImporterTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var db: AppDatabase
    private lateinit var personDao: PersonDao
    private lateinit var instructionDao: InstructionDao
    private lateinit var tagDao: TagDao
    private lateinit var exporter: PlainExporter
    private lateinit var importer: PlainImporter

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(testDispatcher.asExecutor())
            .setTransactionExecutor(testDispatcher.asExecutor())
            .build()
        personDao = db.personDao()
        instructionDao = db.instructionDao()
        tagDao = db.tagDao()
        exporter = PlainExporter(personDao, instructionDao, tagDao, db.instructionTagDao())
        importer = PlainImporter(context, personDao, instructionDao, tagDao)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `csv round-trip exports and re-imports the same rows`() = runTest {
        // Seed the DB
        personDao.upsert(
            com.kaavalan.note.data.local.entities.PersonEntity(
                id = "p1", name = "Ramu", designation = "SHO", station = "Bandipora",
                phone = null, userId = "",
                createdAt = "2026-08-24T00:00:00Z", updatedAt = "2026-08-24T00:00:00Z",
                syncStatus = com.kaavalan.note.data.local.entities.SyncStatus.SYNCED,
            ),
        )
        instructionDao.upsert(
            com.kaavalan.note.data.local.entities.InstructionEntity(
                id = "i1", personId = "p1", direction = "INCOMING", status = "OPEN",
                source = "TEXT", priority = "NORMAL", title = "Buy milk",
                rawText = "Buy milk on the way home", dueAt = null,
                capturedAt = "2026-08-24T00:00:00Z",
                createdAt = "2026-08-24T00:00:00Z", updatedAt = "2026-08-24T00:00:00Z",
            ),
        )
        tagDao.upsert(
            com.kaavalan.note.data.local.entities.TagEntity(
                id = "t1", name = "urgent", kind = "FREE", color = null,
                userId = "", usageCount = 1, lastUsedAt = null,
                createdAt = "2026-08-24T00:00:00Z", updatedAt = "2026-08-24T00:00:00Z",
                syncStatus = com.kaavalan.note.data.local.entities.SyncStatus.SYNCED,
            ),
        )
        advanceUntilIdle()

        // Export → CSV
        val snap = exporter.snapshot()
        val csv = exporter.toCsv(snap)

        // Wipe the DB
        db.clearAllTables()

        // Re-import via a fake URI (in-memory bytes via a
        // custom file:// path that Robolectric's
        // ContentResolver can read).
        val uri = writeTestFile("roundtrip.csv", csv)
        val report = importer.importFromUri(uri).getOrThrow()
        advanceUntilIdle()

        assertEquals(1, report.peopleInserted)
        assertEquals(1, report.instructionsInserted)
        assertEquals(1, report.tagsInserted)
        assertEquals(0, report.peopleUpdated)

        val reloaded = exporter.snapshot()
        assertEquals(1, reloaded.people.size)
        assertEquals("Ramu", reloaded.people[0].name)
        assertEquals(1, reloaded.instructions.size)
        assertEquals("Buy milk", reloaded.instructions[0].title)
        assertEquals(1, reloaded.tags.size)
        assertEquals("urgent", reloaded.tags[0].name)
    }

    @Test
    fun `re-importing the same file is idempotent (no new inserts)`() = runTest {
        personDao.upsert(
            com.kaavalan.note.data.local.entities.PersonEntity(
                id = "p1", name = "Ramu", designation = null, station = null,
                phone = null, userId = "",
                createdAt = "2026-08-24T00:00:00Z", updatedAt = "2026-08-24T00:00:00Z",
                syncStatus = com.kaavalan.note.data.local.entities.SyncStatus.SYNCED,
            ),
        )
        advanceUntilIdle()

        val snap = exporter.snapshot()
        val csv = exporter.toCsv(snap)
        val uri = writeTestFile("reimport.csv", csv)

        // First import: row already exists, so it counts
        // as updated.
        val first = importer.importFromUri(uri).getOrThrow()
        advanceUntilIdle()
        assertEquals(0, first.peopleInserted)
        assertEquals(1, first.peopleUpdated)
    }

    @Test
    fun `json round-trip preserves the same fields as csv`() = runTest {
        personDao.upsert(
            com.kaavalan.note.data.local.entities.PersonEntity(
                id = "p1", name = "Priya", designation = "DSP", station = "Srinagar",
                phone = null, userId = "",
                createdAt = "2026-08-24T00:00:00Z", updatedAt = "2026-08-24T00:00:00Z",
                syncStatus = com.kaavalan.note.data.local.entities.SyncStatus.SYNCED,
            ),
        )
        advanceUntilIdle()

        val snap = exporter.snapshot()
        val json = exporter.toJson(snap)
        val uri = writeTestFile("roundtrip.json", json)
        db.clearAllTables()

        val report = importer.importFromUri(uri).getOrThrow()
        advanceUntilIdle()
        assertEquals(1, report.peopleInserted)

        val reloaded = exporter.snapshot().people
        assertEquals(1, reloaded.size)
        assertEquals("Priya", reloaded[0].name)
        assertEquals("DSP", reloaded[0].designation)
        assertEquals("Srinagar", reloaded[0].station)
    }

    @Test
    fun `csv round-trip preserves Stage 1 instruction fields and normalizes legacy enums`() = runTest {
        instructionDao.upsert(stage1Instruction())
        val csv = exporter.toCsv(exporter.snapshot())
        db.clearAllTables()

        importer.importFromUri(writeTestFile("stage1.csv", csv)).getOrThrow()
        advanceUntilIdle()

        assertStage1Instruction(instructionDao.snapshot().single())
    }

    @Test
    fun `json round-trip preserves Stage 1 instruction fields and normalizes legacy enums`() = runTest {
        instructionDao.upsert(stage1Instruction())
        val json = exporter.toJson(exporter.snapshot())
        db.clearAllTables()

        importer.importFromUri(writeTestFile("stage1.json", json)).getOrThrow()
        advanceUntilIdle()

        assertStage1Instruction(instructionDao.snapshot().single())
    }

    @Test
    fun `csv and json updates preserve existing instruction tag links`() = runTest {
        instructionDao.upsert(stage1Instruction())
        tagDao.upsert(
            com.kaavalan.note.data.local.entities.TagEntity(
                id = "tag-existing",
                name = "existing",
                kind = "FREE",
                color = null,
                usageCount = 0,
                lastUsedAt = null,
                userId = "test",
                createdAt = "2026-08-30T10:00:00Z",
                updatedAt = "2026-08-30T10:00:00Z",
            ),
        )
        val link = InstructionTagCrossRef("stage1", "tag-existing")
        db.instructionTagDao().attach(link)
        val snapshot = exporter.snapshot()
        val csv = exporter.toCsv(snapshot)
        val json = exporter.toJson(snapshot)

        importer.importFromUri(writeTestFile("existing-stage1.csv", csv)).getOrThrow()
        assertEquals(listOf(link), db.instructionTagDao().snapshotAll())

        importer.importFromUri(writeTestFile("existing-stage1.json", json)).getOrThrow()
        assertEquals(listOf(link), db.instructionTagDao().snapshotAll())
    }

    @Test
    fun `legacy imports use direction aware status archive dropped rows and recover due date`() = runTest {
        val instructions = JSONArray()
            .put(legacyInstructionJson("incoming", "INCOMING", "IN_PROGRESS"))
            .put(legacyInstructionJson("outgoing", "OUTGOING", "IN_PROGRESS"))
            .put(legacyInstructionJson("dropped", "SELF", "DROPPED"))
            .put(legacyInstructionJson("ambiguous", "UNKNOWN", "ACK_PENDING"))
        val root = JSONObject()
            .put("people", JSONArray())
            .put("instructions", instructions)
            .put("tags", JSONArray())

        importer.importFromUri(writeTestFile("legacy.json", root.toString())).getOrThrow()

        val restored = instructionDao.snapshot().associateBy { it.id }
        assertEquals("TO_DO", restored.getValue("incoming").status)
        assertEquals("WAITING", restored.getValue("outgoing").status)
        assertEquals("TO_DO", restored.getValue("dropped").status)
        assertEquals(1_788_330_600_000L, restored.getValue("dropped").archivedAtEpochMs)
        assertEquals("legacy_status=DROPPED", restored.getValue("dropped").migrationMetadata)
        assertTrue(restored.getValue("ambiguous").migrationReviewRequired)
        restored.values.forEach {
            assertEquals(1_788_429_600_000L, it.hardDeadlineAtEpochMs)
        }
    }

    private fun legacyInstructionJson(id: String, direction: String, status: String) = JSONObject()
        .put("id", id)
        .put("person_id", JSONObject.NULL)
        .put("direction", direction)
        .put("status", status)
        .put("source", "TEXT")
        .put("priority", "LOW")
        .put("title", "Legacy $id")
        .put("raw_text", "Legacy $id raw")
        .put("due_at", "2026-09-03T10:00:00Z")
        .put("captured_at", "2026-08-31T09:00:00Z")
        .put("created_at", "2026-08-31T09:00:00Z")
        .put("updated_at", "2026-09-02T06:30:00Z")

    private fun stage1Instruction() = com.kaavalan.note.data.local.entities.InstructionEntity(
        id = "stage1",
        personId = "person-1",
        direction = "OUTGOING",
        status = "ACK_PENDING",
        source = "TEXT",
        priority = "HIGH",
        title = "Legacy title",
        rawText = "Original capture",
        dueAt = "2026-09-03T10:00:00Z",
        capturedAt = "2026-08-30T10:00:00Z",
        createdAt = "2026-08-30T10:00:00Z",
        updatedAt = "2026-08-31T10:00:00Z",
        actionSummary = "Stage 1 action summary",
        hardDeadlineAtEpochMs = 1_788_429_600_000L,
        followUpAtEpochMs = 1_788_091_200_000L,
        archivedAtEpochMs = 1_788_516_000_000L,
        responsiblePersonId = "person-2",
        groupLabel = "Station writers",
        localRevision = 9,
        migrationReviewRequired = true,
        migrationMetadata = "legacy_status=ACK_PENDING",
    )

    private fun assertStage1Instruction(
        restored: com.kaavalan.note.data.local.entities.InstructionEntity,
    ) {
        assertEquals("WAITING", restored.status)
        assertEquals("URGENT", restored.priority)
        assertEquals("Original capture", restored.rawText)
        assertEquals("Stage 1 action summary", restored.actionSummary)
        assertEquals(1_788_429_600_000L, restored.hardDeadlineAtEpochMs)
        assertEquals(1_788_091_200_000L, restored.followUpAtEpochMs)
        assertEquals(1_788_516_000_000L, restored.archivedAtEpochMs)
        assertEquals("person-2", restored.responsiblePersonId)
        assertEquals("Station writers", restored.groupLabel)
        assertEquals(9L, restored.localRevision)
        assertTrue(restored.migrationReviewRequired)
        assertEquals("legacy_status=ACK_PENDING", restored.migrationMetadata)
    }

    /**
     * Write a file into the app's cache dir and return
     * a `file://` URI. Robolectric's ContentResolver
     * can read these via FileProvider-equivalent paths.
     */
    private fun writeTestFile(name: String, contents: String): Uri {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val dir = java.io.File(context.cacheDir, "importer-test").apply { mkdirs() }
        val file = java.io.File(dir, name)
        file.writeText(contents)
        return Uri.fromFile(file)
    }
}
