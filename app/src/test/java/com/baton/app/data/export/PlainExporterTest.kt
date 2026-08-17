package com.baton.app.data.export

import com.baton.app.data.local.InstructionDao
import com.baton.app.data.local.InstructionTagDao
import com.baton.app.data.local.PersonDao
import com.baton.app.data.local.TagDao
import com.baton.app.data.local.entities.InstructionEntity
import com.baton.app.data.local.entities.InstructionTagCrossRef
import com.baton.app.data.local.entities.PersonEntity
import com.baton.app.data.local.entities.SyncStatus
import com.baton.app.data.local.entities.TagEntity
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tier 1.7 (v2.0): round-trip the plain export through a
 * `ByteArrayOutputStream`. We mock the DAOs to seed a known
 * dataset, then assert the CSV / JSON output contains the
 * expected rows.
 *
 * Runs under Robolectric so `org.json.JSONObject` is on
 * the classpath (the JSON export uses `JSONArray` /
 * `JSONObject`, which the AOSP provides).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PlainExporterTest {

    private fun person(id: String, name: String, station: String? = null) = PersonEntity(
        id = id, name = name, designation = "SHO", station = station, phone = null,
        userId = "u", createdAt = "2026-08-12T00:00:00+00:00",
        updatedAt = "2026-08-12T00:00:00+00:00",
        isSensitive = false, syncStatus = SyncStatus.SYNCED,
    )

    private fun instruction(
        id: String, personId: String?, title: String, rawText: String,
    ) = InstructionEntity(
        id = id, personId = personId, direction = "OUTGOING", status = "OPEN",
        source = "TEXT", priority = "NORMAL", title = title, rawText = rawText,
        dueAt = null, capturedAt = "2026-08-12T00:00:00+00:00",
        createdAt = "2026-08-12T00:00:00+00:00",
        updatedAt = "2026-08-12T00:00:00+00:00",
        isSensitive = false, syncStatus = SyncStatus.SYNCED,
        completedAt = null, droppedReason = null, nextActionAt = 1730000000000L,
    )

    private fun tag(id: String, name: String, kind: String) = TagEntity(
        id = id, name = name, kind = kind, color = null, usageCount = 0,
        lastUsedAt = null, userId = "u", createdAt = "2026-08-12T00:00:00+00:00",
        updatedAt = "2026-08-12T00:00:00+00:00", syncStatus = SyncStatus.SYNCED,
    )

    private fun mockExporter(): PlainExporter {
        val personDao = mockk<PersonDao>(relaxed = true)
        val instructionDao = mockk<InstructionDao>(relaxed = true)
        val tagDao = mockk<TagDao>(relaxed = true)
        val xrefDao = mockk<InstructionTagDao>(relaxed = true)
        coEvery { personDao.snapshot() } returns listOf(
            person("p1", "Inspector Ramesh", "Thanjavur Town"),
            person("p2", "DSP Kavitha"),
        )
        coEvery { instructionDao.snapshot() } returns listOf(
            instruction("i1", "p1", "Temple land inquiry", "follow up by Friday"),
            instruction("i2", "p2", "Bandobast plan", "draft a plan and send across"),
        )
        coEvery { tagDao.observeAll() } returns flowOf(
            listOf(tag("t1", "priority", "PRIORITY"), tag("t2", "follow-up", "FREE")),
        )
        coEvery { xrefDao.observeForInstruction(any()) } returns flowOf(emptyList())
        return PlainExporter(personDao, instructionDao, tagDao, xrefDao)
    }

    @Test
    fun `toCsv includes UTF-8 BOM and all sections`() = runBlocking {
        val csv = mockExporter().toCsv(mockExporter().snapshot())
        assertTrue("CSV must start with UTF-8 BOM", csv.startsWith("\uFEFF"))
        assertTrue("CSV must include people header", csv.contains("id,name,designation"))
        assertTrue("CSV must include person row", csv.contains("Inspector Ramesh"))
        assertTrue("CSV must include instructions header", csv.contains("next_action_at"))
        assertTrue("CSV must include instruction row", csv.contains("Temple land inquiry"))
        assertTrue("CSV must include tags header", csv.contains("usage_count"))
        assertTrue("CSV must include tag row", csv.contains("priority"))
    }

    @Test
    fun `toCsv correctly escapes commas in values`() = runBlocking {
        val personDao = mockk<PersonDao>(relaxed = true)
        val instructionDao = mockk<InstructionDao>(relaxed = true)
        val tagDao = mockk<TagDao>(relaxed = true)
        val xrefDao = mockk<InstructionTagDao>(relaxed = true)
        coEvery { personDao.snapshot() } returns listOf(
            person("p1", "Ramesh, Kumar", "Town, North"),
        )
        coEvery { instructionDao.snapshot() } returns emptyList()
        coEvery { tagDao.observeAll() } returns flowOf(emptyList())
        val csv = PlainExporter(personDao, instructionDao, tagDao, xrefDao)
            .toCsv(PlainExporter(personDao, instructionDao, tagDao, xrefDao).snapshot())
        assertTrue("comma in name must be quoted", csv.contains("\"Ramesh, Kumar\""))
        assertTrue("comma in station must be quoted", csv.contains("\"Town, North\""))
    }

    @Test
    fun `toJson round-trips people, instructions, and tags`() = runBlocking {
        val exporter = mockExporter()
        val json = exporter.toJson(exporter.snapshot())
        // The JSON must have a top-level object with the three arrays.
        assertTrue("json must contain 'people'", json.contains("\"people\""))
        assertTrue("json must contain 'instructions'", json.contains("\"instructions\""))
        assertTrue("json must contain 'tags'", json.contains("\"tags\""))
        assertTrue("person row must be present", json.contains("Inspector Ramesh"))
        assertTrue("instruction row must be present", json.contains("Temple land inquiry"))
        assertTrue("tag row must be present", json.contains("priority"))
    }

    @Test
    fun `toCsv empty DB returns BOM + headers only`() = runBlocking {
        val personDao = mockk<PersonDao>(relaxed = true)
        val instructionDao = mockk<InstructionDao>(relaxed = true)
        val tagDao = mockk<TagDao>(relaxed = true)
        val xrefDao = mockk<InstructionTagDao>(relaxed = true)
        coEvery { personDao.snapshot() } returns emptyList()
        coEvery { instructionDao.snapshot() } returns emptyList()
        coEvery { tagDao.observeAll() } returns flowOf(emptyList())
        val exporter = PlainExporter(personDao, instructionDao, tagDao, xrefDao)
        val csv = exporter.toCsv(exporter.snapshot())
        assertTrue(csv.startsWith("\uFEFF"))
        // The three section headers must be present even with no rows.
        assertTrue(csv.contains("id,name,designation"))
        assertTrue(csv.contains("next_action_at"))
        assertTrue(csv.contains("usage_count"))
    }
}
