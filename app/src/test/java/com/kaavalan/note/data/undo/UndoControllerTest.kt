package com.kaavalan.note.data.undo

import com.kaavalan.note.data.local.CaptureDao
import com.kaavalan.note.data.local.InstructionDao
import com.kaavalan.note.data.local.PersonDao
import com.kaavalan.note.data.local.entities.CaptureEntity
import com.kaavalan.note.data.local.entities.InstructionEntity
import com.kaavalan.note.data.local.entities.PersonEntity
import com.kaavalan.note.data.local.entities.SyncStatus
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tier 1.6 (v2.0): the "last action only" undo buffer.
 *
 * Push an action, assert the flow is non-null, undo it,
 * assert the DAO was re-inserted, assert the flow is null
 * again. The "second action replaces the first" semantics
 * is also locked.
 */
class UndoControllerTest {

    private fun person(id: String) = PersonEntity(
        id = id, name = "Test", designation = null, station = null, phone = null,
        userId = "u", createdAt = "2026-08-12T00:00:00+00:00",
        updatedAt = "2026-08-12T00:00:00+00:00", isSensitive = false,
        syncStatus = SyncStatus.SYNCED,
    )

    private fun instruction(id: String) = InstructionEntity(
        id = id, personId = null, direction = "OUTGOING", status = "OPEN",
        source = "TEXT", priority = "NORMAL", title = "title", rawText = "raw",
        dueAt = null, capturedAt = "2026-08-12T00:00:00+00:00",
        createdAt = "2026-08-12T00:00:00+00:00",
        updatedAt = "2026-08-12T00:00:00+00:00", isSensitive = false,
        syncStatus = SyncStatus.SYNCED, completedAt = null, droppedReason = null,
        nextActionAt = null,
    )

    private fun capture(id: String) = CaptureEntity(
        id = id, mode = "TEXT", rawText = "raw", audioUri = null, imageUri = null,
        processed = false, createdAt = "2026-08-12T00:00:00+00:00",
        syncStatus = SyncStatus.SYNCED,
    )

    @Test
    fun `pushed action is observable on the flow`() {
        val personDao = mockk<PersonDao>(relaxed = true)
        val instructionDao = mockk<InstructionDao>(relaxed = true)
        val captureDao = mockk<CaptureDao>(relaxed = true)
        val ctl = UndoController(personDao, instructionDao, captureDao)
        assertNull("no action on init", ctl.last.value)
        ctl.push(UndoableAction.DeletePerson("p1", "Test", person("p1")))
        assertEquals("p1", ctl.last.value?.id)
    }

    @Test
    fun `undoLast re-inserts the deleted person`() = runTest {
        val personDao = mockk<PersonDao>(relaxed = true)
        val instructionDao = mockk<InstructionDao>(relaxed = true)
        val captureDao = mockk<CaptureDao>(relaxed = true)
        val ctl = UndoController(personDao, instructionDao, captureDao)
        ctl.push(UndoableAction.DeletePerson("p1", "Test", person("p1")))
        ctl.undoLast()
        coVerify { personDao.upsert(person("p1")) }
        assertNull(ctl.last.value)
    }

    @Test
    fun `undoLast re-inserts the deleted instruction`() = runTest {
        val personDao = mockk<PersonDao>(relaxed = true)
        val instructionDao = mockk<InstructionDao>(relaxed = true)
        val captureDao = mockk<CaptureDao>(relaxed = true)
        val ctl = UndoController(personDao, instructionDao, captureDao)
        ctl.push(UndoableAction.DeleteInstruction("i1", "title", instruction("i1")))
        ctl.undoLast()
        coVerify { instructionDao.upsert(instruction("i1")) }
    }

    @Test
    fun `undoLast re-inserts the deleted capture`() = runTest {
        val personDao = mockk<PersonDao>(relaxed = true)
        val instructionDao = mockk<InstructionDao>(relaxed = true)
        val captureDao = mockk<CaptureDao>(relaxed = true)
        val ctl = UndoController(personDao, instructionDao, captureDao)
        ctl.push(UndoableAction.DeleteCapture("c1", "raw preview", capture("c1")))
        ctl.undoLast()
        coVerify { captureDao.upsert(capture("c1")) }
    }

    @Test
    fun `second push replaces the first (last-action-only)`() {
        val personDao = mockk<PersonDao>(relaxed = true)
        val instructionDao = mockk<InstructionDao>(relaxed = true)
        val captureDao = mockk<CaptureDao>(relaxed = true)
        val ctl = UndoController(personDao, instructionDao, captureDao)
        ctl.push(UndoableAction.DeletePerson("p1", "First", person("p1")))
        ctl.push(UndoableAction.DeletePerson("p2", "Second", person("p2")))
        assertEquals("p2", ctl.last.value?.id)
    }
}
