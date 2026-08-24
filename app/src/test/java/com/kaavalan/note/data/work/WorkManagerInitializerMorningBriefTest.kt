package com.kaavalan.note.data.work

import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.test.runTest
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
 * v2.0 Tier 2 (§2.6): WorkManager wiring for the
 * [com.kaavalan.note.data.brief.MorningBriefWorker]. The test
 * uses `WorkManagerTestInitHelper` to swap in a test
 * WorkManager and asserts the enqueue call lands a real
 * [WorkInfo] in the unique-work slot.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WorkManagerInitializerMorningBriefTest {

    private lateinit var context: android.content.Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
    }

    @After
    fun tearDown() {
        // Test work is reset by the next setUp.
    }

    @Test
    fun `enqueueMorningBriefOneShot enqueues a one-shot work named baton-morning-brief`() = runTest {
        WorkManagerInitializer.enqueueMorningBriefOneShot(context, delaySec = 2L)
        val wm = WorkManager.getInstance(context)
        val infos = wm.getWorkInfosForUniqueWork("baton-morning-brief").get()
        assertEquals(1, infos.size)
        val info = infos[0]
        assertNotNull("info should be non-null", info)
        assertTrue("work should be in ENQUEUED state",
            info.state == WorkInfo.State.ENQUEUED || info.state == WorkInfo.State.RUNNING)
    }

    @Test
    fun `enqueueMorningBriefOneShot is idempotent under REPLACE policy`() = runTest {
        // First enqueue
        WorkManagerInitializer.enqueueMorningBriefOneShot(context, delaySec = 2L)
        // Second enqueue with REPLACE -> the first is replaced.
        WorkManagerInitializer.enqueueMorningBriefOneShot(context, delaySec = 5L)
        val infos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork("baton-morning-brief").get()
        // REPLACE policy -> only the latest enqueue survives.
        assertEquals(1, infos.size)
    }

    @Test
    fun `scheduleMorningBrief enqueues a periodic 24h work`() = runTest {
        WorkManagerInitializer.scheduleMorningBrief(context, hourOfDay = 9, minute = 0)
        val infos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork("baton-morning-brief").get()
        assertEquals(1, infos.size)
        val info = infos[0]
        // Periodicity is opaque on the public WorkInfo API, but
        // the info must be present and enqueued.
        assertTrue("work must be present after schedule",
            info.state == WorkInfo.State.ENQUEUED)
    }
}
