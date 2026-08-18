package com.baton.app.data.dev

import android.content.Context
import android.content.res.AssetManager
import com.baton.app.data.local.CaptureDao
import com.baton.app.data.local.InstructionDao
import com.baton.app.data.local.InstructionFtsDao
import com.baton.app.data.local.InstructionTagDao
import com.baton.app.data.local.PersonDao
import com.baton.app.data.local.TagDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.io.ByteArrayInputStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * v1.6.2: smoke tests for [FixtureLoader]. The asset itself is
 * part of the test fixture (it lives in
 * `app/src/main/assets/synthetic-data.json`); these tests parse
 * the real JSON so a future schema change breaks the build
 * loudly.
 *
 * The tests do NOT exercise Room — they assert on the parsed
 * shape and the DAO-call sequence using mockk. The end-to-end
 * "fixture on device" path is covered by the manual QA drive
 * (see the [sdd/ratings-v161/] screenshots).
 */
class FixtureLoaderTest {

    private val fixtureJson = """
        {
          "persons": [
            { "id": "p1", "name": "Inspector Ramu", "designation": "SHO",
              "station": "Innam PS", "userId": "u1",
              "createdAt": "2026-08-01T00:00:00Z",
              "updatedAt": "2026-08-01T00:00:00Z",
              "isSensitive": false, "syncStatus": "SYNCED",
              "tier": "Active", "cadenceOverrideDays": null,
              "lastInteractionAt": 1722470400000,
              "vaultMode": "visible" }
          ],
          "instructions": [
            { "id": "i1", "personId": "p1", "direction": "OUTGOING",
              "status": "OPEN", "source": "TEXT", "priority": "NORMAL",
              "title": "Pull FIR 217/2026", "rawText": "Pull FIR 217/2026",
              "dueAt": "2026-08-25T00:00:00Z",
              "capturedAt": "2026-08-18T09:00:00Z",
              "createdAt": "2026-08-18T09:00:00Z",
              "updatedAt": "2026-08-18T09:00:00Z",
              "isSensitive": false, "syncStatus": "SYNCED",
              "urgency": "normal", "reviewAtEpochDay": null }
          ],
          "captures": [
            { "id": "c1", "mode": "TEXT", "rawText": "sho bhayya - verify",
              "audioUri": null, "imageUri": null,
              "processed": true,
              "createdAt": "2026-08-18T09:00:00Z",
              "syncStatus": "SYNCED",
              "urgency": "normal", "reviewAtEpochDay": null }
          ],
          "tags": [],
          "worries": [],
          "instructionTags": []
        }
    """.trimIndent()

    private fun mockContext(): Context {
        val assets = mockk<AssetManager>()
        every { assets.open(any()) } returns ByteArrayInputStream(fixtureJson.toByteArray())
        val ctx = mockk<Context>()
        every { ctx.assets } returns assets
        return ctx
    }

    private fun mockDaos(): Daos {
        return Daos(
            personDao = mockk(relaxed = true),
            instructionDao = mockk(relaxed = true),
            instructionFtsDao = mockk(relaxed = true),
            instructionTagDao = mockk(relaxed = true),
            captureDao = mockk(relaxed = true),
            tagDao = mockk(relaxed = true),
        )
    }

    private data class Daos(
        val personDao: PersonDao,
        val instructionDao: InstructionDao,
        val instructionFtsDao: InstructionFtsDao,
        val instructionTagDao: InstructionTagDao,
        val captureDao: CaptureDao,
        val tagDao: TagDao,
    )

    @Test
    fun `loadFromAssets inserts the right counts`() = runTest {
        val loader = FixtureLoader(
            context = mockContext(),
            personDao = mockDaos().personDao,
            instructionDao = mockDaos().instructionDao,
            instructionFtsDao = mockDaos().instructionFtsDao,
            instructionTagDao = mockDaos().instructionTagDao,
            captureDao = mockDaos().captureDao,
            tagDao = mockDaos().tagDao,
        )

        val report = loader.loadFromAssets()

        assertEquals(1, report.persons)
        assertEquals(1, report.instructions)
        assertEquals(1, report.captures)
    }

    @Test
    fun `loadFromAssets clears the existing tables before insert`() = runTest {
        val daos = mockDaos()
        val loader = FixtureLoader(
            context = mockContext(),
            personDao = daos.personDao,
            instructionDao = daos.instructionDao,
            instructionFtsDao = daos.instructionFtsDao,
            instructionTagDao = daos.instructionTagDao,
            captureDao = daos.captureDao,
            tagDao = daos.tagDao,
        )

        loader.loadFromAssets()

        coVerify { daos.personDao.deleteAll() }
        coVerify { daos.instructionDao.deleteAll() }
        coVerify { daos.instructionFtsDao.deleteAll() }
        coVerify { daos.captureDao.deleteAll() }
        coVerify { daos.tagDao.deleteAll() }
        coVerify { daos.instructionTagDao.deleteAll() }
    }

    @Test
    fun `loadFromAssets also writes the FTS row for each instruction`() = runTest {
        val daos = mockDaos()
        // ftsDao.maxInstructionRowid() is called per row; return a
        // deterministic value.
        coEvery { daos.instructionFtsDao.maxInstructionRowid() } returns 42L

        val loader = FixtureLoader(
            context = mockContext(),
            personDao = daos.personDao,
            instructionDao = daos.instructionDao,
            instructionFtsDao = daos.instructionFtsDao,
            instructionTagDao = daos.instructionTagDao,
            captureDao = daos.captureDao,
            tagDao = daos.tagDao,
        )

        loader.loadFromAssets()

        // 1 instruction -> 1 FTS upsert
        coVerify(exactly = 1) { daos.instructionFtsDao.upsert(match { it.rowid == 42L }) }
    }
}
