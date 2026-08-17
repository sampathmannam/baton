package com.baton.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.baton.app.data.local.entities.InstructionEntity
import com.baton.app.data.local.entities.InstructionFtsEntity
import com.baton.app.data.local.entities.SyncStatus
import com.baton.app.data.search.SearchQuery
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tier 1.3 (v2.0): round-trip 5 people + 20 instructions through
 * the FTS4 search DAO. We build an in-memory Room (no
 * SQLCipher) so the test is hermetic.
 *
 * The test seeds 5 distinct `personId` values and 4 instructions
 * per person (20 total). The query for "temple" must return the
 * one instruction whose rawText contains the word, ordered by
 * `capturedAt` DESC.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FtsSearchTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: InstructionDao
    private lateinit var ftsDao: InstructionFtsDao

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.instructionDao()
        ftsDao = db.instructionFtsDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun instruction(
        id: String, personId: String?, title: String, rawText: String, ts: String,
    ) = InstructionEntity(
        id = id, personId = personId, direction = "OUTGOING", status = "OPEN",
        source = "TEXT", priority = "NORMAL", title = title, rawText = rawText,
        dueAt = null, capturedAt = ts, createdAt = ts, updatedAt = ts,
        isSensitive = false, syncStatus = SyncStatus.SYNCED,
        completedAt = null, droppedReason = null, nextActionAt = null,
    )

    private suspend fun seedFivePeopleTwentyInstructions() {
        // 5 people (1 row each in `persons` for FK reasons; not
        // required for the FTS query but mirrors reality).
        val personDao = db.personDao()
        val now = "2026-08-12T00:00:00+00:00"
        for (p in 1..5) {
            personDao.upsert(
                com.baton.app.data.local.entities.PersonEntity(
                    id = "p$p", name = "Person $p", designation = null, station = null,
                    phone = null, userId = "u", createdAt = now, updatedAt = now,
                    isSensitive = false, syncStatus = SyncStatus.SYNCED,
                ),
            )
        }
        // 20 instructions, 4 per person. The FTS rowid MUST
        // match the instructions table's rowid (Room's
        // auto-increment) so the JOIN in the search query
        // works. We read the max rowid after each insert to
        // keep them in sync.
        for (p in 1..5) {
            for (k in 1..4) {
                val id = "p${p}-i$k"
                val title = "title p$p k$k"
                val rawText = when ((p + k) % 4) {
                    0 -> "temple land inquiry for person $p k$k"
                    1 -> "bandobast plan for person $p k$k"
                    2 -> "market patrol schedule for person $p k$k"
                    else -> "weekly briefing for person $p k$k"
                }
                val ts = "2026-08-12T00:00:%02d.000+00:00".format(p * 4 + k)
                dao.upsert(instruction(id, "p$p", title, rawText, ts))
                val rowid = ftsDao.maxInstructionRowid() ?: 0L
                // Mirror into the FTS table (no auto-triggers).
                ftsDao.upsert(
                    InstructionFtsEntity(
                        rowid = rowid,
                        title = title,
                        rawText = rawText,
                        personId = "p$p",
                        capturedAt = ts,
                    ),
                )
            }
        }
    }

    @Test
    fun `FTS round-trip - 5 people 20 instructions, search for temple returns only the temple rows in order`() = runTest {
        seedFivePeopleTwentyInstructions()
        val match = SearchQuery.build("temple")
        val hits = ftsDao.searchOnce(match)
        assertTrue("expected at least 1 hit for 'temple*', got ${hits.size}", hits.isNotEmpty())
        // Every hit must have "temple" in title or rawText.
        for (h in hits) {
            assertTrue(
                "hit ${h.id} must contain 'temple' in title or rawText",
                h.title.contains("temple", ignoreCase = true) ||
                    h.rawText.contains("temple", ignoreCase = true),
            )
        }
        // The seeded data has 5 'temple land' rows out of 20
        // (one per person, k=0 in the rotation).
        assertEquals("expected 5 'temple' rows", 5, hits.size)
    }

    @Test
    fun `FTS returns most-recent hits first`() = runTest {
        seedFivePeopleTwentyInstructions()
        val match = SearchQuery.build("person")
        val hits = ftsDao.searchOnce(match)
        assertTrue("expected many hits for 'person*'", hits.size > 1)
        for (i in 0 until hits.size - 1) {
            assertTrue(
                "hits must be ordered by capturedAt DESC",
                hits[i].capturedAt >= hits[i + 1].capturedAt,
            )
        }
    }

    @Test
    fun `FTS returns empty list for a query with no matches`() = runTest {
        seedFivePeopleTwentyInstructions()
        val match = SearchQuery.build("nonexistent-zzz")
        val hits = ftsDao.searchOnce(match)
        assertEquals(0, hits.size)
    }
}
