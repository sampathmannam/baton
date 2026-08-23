package com.baton.app.features.widget

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.baton.app.data.local.AppDatabase
import com.baton.app.data.local.entities.InstructionEntity
import com.baton.app.data.local.entities.PersonEntity
import com.baton.app.data.local.entities.SyncStatus
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v1.9.1 (PROD-READINESS-P3-P2-#3 wiring): the data-binding
 * tests for the Today + Decay widget badge values.
 *
 * The widgets call:
 *  - [com.baton.app.data.local.InstructionDao.countOpen] for
 *    the Today widget
 *  - [com.baton.app.data.local.PersonDao.countQuietSince] for
 *    the Decay widget
 *
 * These tests pin the DAO behaviour that the widget depends
 * on, so a future SQL edit can't silently break the widget
 * surface. The widget's Composable rendering of the count
 * is a thin `Text(text = count.toString())` and is not
 * separately tested (the UI dump from drive-verify covers
 * the end-to-end path).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WidgetCountTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun ins(id: String, status: String) = InstructionEntity(
        id = id,
        personId = null,
        direction = "INCOMING",
        status = status,
        source = "TEXT",
        priority = "NORMAL",
        title = "t-$id",
        rawText = "r-$id",
        dueAt = null,
        capturedAt = "2026-08-12T00:00:00Z",
        createdAt = "2026-08-12T00:00:00Z",
        updatedAt = "2026-08-12T00:00:00Z",
        syncStatus = SyncStatus.SYNCED,
    )

    private fun person(id: String, lastInteractionAt: Long?) = PersonEntity(
        id = id,
        name = "p-$id",
        designation = null,
        station = null,
        phone = null,
        userId = "u1",
        createdAt = "2026-01-01T00:00:00Z",
        updatedAt = "2026-01-01T00:00:00Z",
        isSensitive = false,
        syncStatus = SyncStatus.SYNCED,
        lastInteractionAt = lastInteractionAt,
        tier = "ACTIVE",
        cadenceOverrideDays = null,
    )

    @Test
    fun `countOpen returns 0 for an empty table`() = runTest {
        assertEquals(0, db.instructionDao().countOpen())
    }

    @Test
    fun `countOpen includes only non-terminal instructions`() = runTest {
        val dao = db.instructionDao()
        dao.upsert(ins("i1", "OPEN"))
        dao.upsert(ins("i2", "OPEN"))
        dao.upsert(ins("i3", "DONE"))            // closed — ignored
        dao.upsert(ins("i4", "CARRIED_OVER"))    // closed — ignored
        dao.upsert(ins("i5", "DROPPED"))          // closed — ignored
        assertEquals(2, dao.countOpen())
    }

    @Test
    fun `countQuietSince returns 0 for an empty table`() = runTest {
        val now = System.currentTimeMillis()
        val threshold = now - TimeUnit.DAYS.toMillis(60)
        assertEquals(0, db.personDao().countQuietSince(threshold))
    }

    @Test
    fun `countQuietSince only counts touched-before-threshold people`() = runTest {
        val dao = db.personDao()
        val now = System.currentTimeMillis()
        val sixtyDaysMs = TimeUnit.DAYS.toMillis(60)
        // Person 1: touched 90 days ago — quiet (> 60d)
        dao.upsert(person("p1", now - TimeUnit.DAYS.toMillis(90)))
        // Person 2: touched 30 days ago — NOT quiet (< 60d)
        dao.upsert(person("p2", now - TimeUnit.DAYS.toMillis(30)))
        // Person 3: never touched (new) — NOT quiet (per DecayViewModel
        // line 66 filter; matches the DAO's `IS NOT NULL` guard)
        dao.upsert(person("p3", null))
        val threshold = now - sixtyDaysMs
        assertEquals(1, dao.countQuietSince(threshold))
    }
}
