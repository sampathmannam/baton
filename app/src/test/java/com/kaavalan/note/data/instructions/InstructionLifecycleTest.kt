package com.kaavalan.note.data.instructions

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kaavalan.note.data.audit.AuditChainWriter
import com.kaavalan.note.data.local.AppDatabase
import com.kaavalan.note.data.local.TouchPersonOnActivity
import com.kaavalan.note.data.local.entities.InstructionTagCrossRef
import com.kaavalan.note.data.local.entities.TagEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class InstructionLifecycleTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: RoomInstructionRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = RoomInstructionRepository(
            db = db,
            dao = db.instructionDao(),
            ftsDao = db.instructionFtsDao(),
            syncQueueDao = db.syncQueueDao(),
            touchOnActivity = TouchPersonOnActivity(db.personDao()),
            appScope = CoroutineScope(UnconfinedTestDispatcher()),
            auditChainWriter = AuditChainWriter(db.auditChainEventDao()) { "test-device" },
        )
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `domain exposes only the approved status and priority values`() {
        assertEquals(listOf("TO_DO", "WAITING", "DONE"), Status.entries.map { it.name })
        assertEquals(listOf("NORMAL", "URGENT"), Priority.entries.map { it.name })
    }

    @Test
    fun `create preserves original capture and explicit instruction fields`() = runTest {
        val created = repository.create(
            InstructionDraft(
                rawText = "Original mixed-language capture - மாற்ற வேண்டாம்",
                actionSummary = "Collect the signed report",
                personId = "person-1",
                responsiblePersonId = "person-2",
                groupLabel = "Station writers",
                priority = Priority.URGENT,
                hardDeadlineAtEpochMs = 1_788_134_400_000L,
                followUpAtEpochMs = 1_788_091_200_000L,
                confirmedAiProposal = true,
            ),
        )

        assertEquals("Original mixed-language capture - மாற்ற வேண்டாம்", created.rawText)
        assertEquals("Collect the signed report", created.actionSummary)
        assertEquals("person-1", created.personId)
        assertEquals("person-2", created.responsiblePersonId)
        assertEquals("Station writers", created.groupLabel)
        assertEquals(1_788_134_400_000L, created.hardDeadlineAtEpochMs)
        assertEquals(1_788_091_200_000L, created.followUpAtEpochMs)
        assertEquals(1L, created.localRevision)
        assertEquals(created, repository.observeForPerson("person-1").first().single())

        assertEquals(
            listOf("CREATED", "AI_PROPOSAL_CONFIRMED"),
            db.auditChainEventDao().eventsForRow("instructions", created.id).map { it.kind },
        )
    }

    @Test
    fun `update rejects stale revision and lifecycle writes audit events`() = runTest {
        val created = repository.create(
            InstructionDraft(rawText = "raw", actionSummary = "Call the station"),
        )
        val updateResult = repository.update(
                id = created.id,
                expectedUpdatedAt = created.updatedAt,
                patch = InstructionPatch(
                    actionSummary = "Wait for the station reply",
                    status = Status.WAITING,
                    priority = Priority.NORMAL,
                    hardDeadlineAtEpochMs = null,
                    followUpAtEpochMs = 1_788_091_200_000L,
                    personId = null,
                    responsiblePersonId = null,
                    groupLabel = null,
                ),
            )
        assertTrue(updateResult is UpdateResult.Updated)
        val updated = (updateResult as UpdateResult.Updated).instruction

        assertEquals(2L, updated.localRevision)
        val conflict = repository.update(
                id = created.id,
                expectedUpdatedAt = created.updatedAt,
                patch = InstructionPatch(
                    actionSummary = "stale overwrite",
                    status = Status.TO_DO,
                    priority = Priority.NORMAL,
                    hardDeadlineAtEpochMs = null,
                    followUpAtEpochMs = null,
                    personId = null,
                    responsiblePersonId = null,
                    groupLabel = null,
                ),
            )
        assertTrue(conflict is UpdateResult.Conflict)

        repository.markDone(created.id, 1_788_177_600_000L)
        repository.archive(created.id, 1_788_264_000_000L)
        repository.restore(created.id, 1_788_350_400_000L)

        val restored = db.instructionDao().getById(created.id)!!
        assertEquals(Status.DONE.name, restored.status)
        assertNull(restored.archivedAtEpochMs)
        assertEquals(5L, restored.localRevision)
        assertEquals(
            listOf("CREATED", "FIELD_CHANGED", "STATUS_CHANGED", "STATUS_CHANGED", "ARCHIVED", "RESTORED"),
            db.auditChainEventDao().eventsForRow("instructions", created.id).map { it.kind },
        )
    }

    @Test
    fun `legacy audience updates keep responsible person and group label synchronized`() = runTest {
        val created = repository.create(
            InstructionDraft(rawText = "raw", actionSummary = "Coordinate response"),
        )

        repository.setAudience(created.id, AudienceRef.ByPerson("person-2", "Inspector Devi"))
        var row = db.instructionDao().getById(created.id)!!
        assertEquals("person-2", row.responsiblePersonId)
        assertNull(row.groupLabel)

        repository.setAudience(created.id, AudienceRef.ByAll("all", "Station writers"))
        row = db.instructionDao().getById(created.id)!!
        assertNull(row.responsiblePersonId)
        assertEquals("Station writers", row.groupLabel)
    }

    @Test
    fun `observe for person includes instructions assigned by responsible person`() = runTest {
        val created = repository.create(
            InstructionDraft(
                rawText = "raw",
                actionSummary = "Responsible person action",
                responsiblePersonId = "person-responsible",
            ),
        )

        assertEquals(
            created.id,
            repository.observeForPerson("person-responsible").first().single().id,
        )
    }

    @Test
    fun `ordinary lifecycle mutations preserve instruction tag relationships`() = runTest {
        val created = repository.create(
            InstructionDraft(rawText = "raw", actionSummary = "Preserve linked tag"),
        )
        db.tagDao().upsert(tag("tag-1"))
        val link = InstructionTagCrossRef(created.id, "tag-1")
        db.instructionTagDao().attach(link)

        val update = repository.update(
            created.id,
            created.updatedAt,
            InstructionPatch(
                actionSummary = "Updated without losing tag",
                status = Status.WAITING,
                priority = Priority.NORMAL,
                hardDeadlineAtEpochMs = null,
                followUpAtEpochMs = null,
                personId = null,
                responsiblePersonId = null,
                groupLabel = null,
            ),
        ) as UpdateResult.Updated
        assertEquals(listOf(link), db.instructionTagDao().snapshotAll())

        repository.markDone(created.id, 1_788_177_600_000L)
        repository.archive(created.id, 1_788_264_000_000L)
        repository.restore(created.id, 1_788_350_400_000L)

        assertEquals(5L, update.instruction.localRevision + 3)
        assertEquals(listOf(link), db.instructionTagDao().snapshotAll())
    }

    @Test
    fun `permanent delete removes instruction search row and dependent tag links`() = runTest {
        val created = repository.create(
            InstructionDraft(rawText = "delete raw", actionSummary = "Delete permanently"),
        )
        db.tagDao().upsert(tag("tag-delete"))
        db.instructionTagDao().attach(InstructionTagCrossRef(created.id, "tag-delete"))

        repository.deletePermanently(created.id)

        assertNull(db.instructionDao().getById(created.id))
        assertTrue(db.instructionTagDao().snapshotAll().none { it.instructionId == created.id })
        assertTrue(db.instructionFtsDao().searchOnce("Delete*").isEmpty())
    }

    @Test
    fun `compatibility archive and reopen preserve simplified status`() = runTest {
        val created = repository.create(
            InstructionDraft(
                rawText = "raw",
                actionSummary = "Wait for response",
                status = Status.WAITING,
            ),
        )

        repository.markDropped(created.id, "Handled offline", "2026-09-01T10:00:00Z")
        var row = db.instructionDao().getById(created.id)!!
        assertEquals(Status.WAITING.name, row.status)
        assertEquals(1_788_256_800_000L, row.archivedAtEpochMs)

        repository.reopen(created.id)
        row = db.instructionDao().getById(created.id)!!
        assertEquals(Status.WAITING.name, row.status)
        assertNull(row.archivedAtEpochMs)

        repository.markDropped(created.id, reason = null)
        row = db.instructionDao().getById(created.id)!!
        assertEquals(Status.WAITING.name, row.status)
        assertTrue(row.archivedAtEpochMs != null)
    }

    private fun tag(id: String) = TagEntity(
        id = id,
        name = id,
        kind = "FREE",
        color = null,
        usageCount = 0,
        lastUsedAt = null,
        userId = "test",
        createdAt = "2026-08-31T00:00:00Z",
        updatedAt = "2026-08-31T00:00:00Z",
    )
}
