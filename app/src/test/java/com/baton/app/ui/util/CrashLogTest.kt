package com.baton.app.ui.util

import androidx.test.core.app.ApplicationProvider
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
import java.io.File

/**
 * v1.9.0 (PROD-READINESS-P3-P1-#1): the
 * in-app crash log test. Verifies that
 * [CrashLog.write] creates a file in
 * `cacheDir/crashes/` with the expected
 * structure, and that [CrashLog.mostRecent]
 * returns the most recent file.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CrashLogTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun setUp() {
        // Wipe any crash files from previous tests.
        CrashLog.clear(context)
    }

    @After
    fun tearDown() {
        CrashLog.clear(context)
    }

    @Test
    fun `write produces a non-empty file in cacheDir crashes`() {
        val report = CrashLog.write(
            context = context,
            throwable = RuntimeException("test crash"),
        )
        assertNotNull(report)
        val file = report!!.file
        assertTrue("crash file should exist", file.exists())
        assertTrue("crash file should be non-empty", file.length() > 0)
        val parent = file.parentFile!!
        assertEquals("crashes", parent.name)
    }

    @Test
    fun `write produces a file with the expected key=value structure`() {
        val report = CrashLog.write(
            context = context,
            throwable = IllegalStateException("kaboom"),
        ) ?: return
        val content = report.file.readText()
        // The format is documented in
        // [CrashLog]. Each line is "key=value".
        assertTrue("should contain timestamp", content.contains("timestamp="))
        assertTrue("should contain app_version", content.contains("app_version="))
        assertTrue("should contain app_build", content.contains("app_build="))
        assertTrue("should contain device_manufacturer", content.contains("device_manufacturer="))
        assertTrue("should contain android_sdk", content.contains("android_sdk="))
        assertTrue("should contain android_release", content.contains("android_release="))
        assertTrue("should contain the throwable class name", content.contains("IllegalStateException"))
        assertTrue("should contain the throwable message", content.contains("kaboom"))
    }

    @Test
    fun `mostRecent returns the most recent file`() {
        CrashLog.write(context, RuntimeException("first"))
        Thread.sleep(50)
        val second = CrashLog.write(context, RuntimeException("second"))!!
        val mostRecent = CrashLog.mostRecent(context)
        assertNotNull(mostRecent)
        assertEquals(second.file.absolutePath, mostRecent!!.absolutePath)
    }

    @Test
    fun `mostRecent returns null on a clean cacheDir`() {
        // Sanity: the setUp() cleared the
        // crash dir, so a fresh mostRecent
        // call should return null.
        assertNull(CrashLog.mostRecent(context))
    }

    @Test
    fun `clear removes every crash file`() {
        repeat(3) { i ->
            CrashLog.write(context, RuntimeException("crash $i"))
        }
        CrashLog.clear(context)
        val dir = File(context.cacheDir, "crashes")
        val remaining = dir.listFiles { f -> f.name.startsWith("crash_") } ?: emptyArray()
        assertEquals(0, remaining.size)
    }

    @Test
    fun `prune keeps at most MAX_CRASH_FILES`() {
        // v1.9.0: 10 is the cap. Write 15,
        // expect 5 to be pruned. This is a
        // sanity check on the prune path
        // (the write path calls prune).
        val reports = (0 until 15).map { i ->
            CrashLog.write(context, RuntimeException("crash $i"))!!
        }
        // Trigger the prune: any subsequent
        // write will prune the older ones.
        CrashLog.write(context, RuntimeException("trigger"))
        val dir = File(context.cacheDir, "crashes")
        val remaining = dir.listFiles { f -> f.name.startsWith("crash_") } ?: emptyArray()
        // The cap is 10 (the most recent 10
        // crash files, including the trigger).
        assertTrue("expected at most 10 files, got ${remaining.size}", remaining.size <= 10)
    }
}
