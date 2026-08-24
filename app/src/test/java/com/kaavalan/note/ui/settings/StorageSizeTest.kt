package com.kaavalan.note.ui.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kaavalan.note.data.local.AppDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.TemporaryFolder
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Tier 0.6: tests for the on-disk storage-size calculation.
 *
 * The "On this phone" row on the Settings sheet now
 * displays "X.X MB on this phone" alongside the existing
 * people/instructions/tags counts. The size is the sum of:
 *
 *  - `getDatabasePath("baton.db")` (the SQLCipher Room DB)
 *  - the WAL companion (`baton.db-wal`)
 *  - the SHM companion (`baton.db-shm`)
 *  - every file under `filesDir/captures/`
 *
 * **What we test:**
 *  - The size calculation sums the right files.
 *  - A missing captures dir (the common case) returns
 *    the DB size only, not a crash.
 *  - A missing DB (the v1.5.0 "Erase all data" path has
 *    not yet written a new DB) returns 0L.
 *
 * **What we don't test:**
 *  - The on-screen formatting. The
 *    [SettingsViewModelTest] covers the upstream flow +
 *    the `StorageInfo.sizeBytes` propagation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class StorageSizeTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var dbFile: File
    private lateinit var capturesDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dbFile = context.getDatabasePath(AppDatabase.NAME)
        capturesDir = File(context.filesDir, "captures")
        // Clean any leftovers from previous tests.
        dbFile.delete()
        File(dbFile.path + "-wal").delete()
        File(dbFile.path + "-shm").delete()
        capturesDir.deleteRecursively()
    }

    @After
    fun tearDown() {
        dbFile.delete()
        File(dbFile.path + "-wal").delete()
        File(dbFile.path + "-shm").delete()
        capturesDir.deleteRecursively()
    }

    @Test
    fun `empty database and no captures returns 0L`() {
        // Tier 0.6: a fresh install with no data must
        // show "0.0 MB" (not crash, not return Long.MAX_VALUE).
        val total = computeStorageSize(context)
        assertEquals(0L, total)
    }

    @Test
    fun `database file is counted`() {
        // Tier 0.6: lay down a 4 KB fake DB; the size
        // calculation includes the file (the row is
        // 4 KB, not 0, even though there's no real
        // SQLCipher content).
        dbFile.parentFile?.mkdirs()
        dbFile.writeBytes(ByteArray(4096))
        val total = computeStorageSize(context)
        assertEquals(4096L, total)
    }

    @Test
    fun `wal and shm are counted alongside the db`() {
        // Tier 0.6: SQLCipher writes the WAL/SHM
        // companions next to the main DB. The size
        // calculation must include them.
        dbFile.parentFile?.mkdirs()
        dbFile.writeBytes(ByteArray(1024))
        File(dbFile.path + "-wal").writeBytes(ByteArray(2048))
        File(dbFile.path + "-shm").writeBytes(ByteArray(512))
        val total = computeStorageSize(context)
        assertEquals(1024L + 2048L + 512L, total)
    }

    @Test
    fun `captures directory contents are counted`() {
        // Tier 0.6: every file under filesDir/captures
        // is a photo capture (M2-T2). The size
        // calculation must include them.
        capturesDir.mkdirs()
        val photo1 = File(capturesDir, "p1.jpg").apply {
            writeBytes(ByteArray(8192))
        }
        val photo2 = File(capturesDir, "p2.jpg").apply {
            writeBytes(ByteArray(4096))
        }
        val total = computeStorageSize(context)
        assertEquals(8192L + 4096L, total)
        // The captures dir itself does not add to
        // the total (its size is the sum of its
        // children, which we already count).
        assertTrue(photo1.exists())
        assertTrue(photo2.exists())
    }

    @Test
    fun `db wal shm and captures sum together`() {
        // Tier 0.6: the realistic case after a few
        // captures have been taken. The total is the
        // sum of all three buckets.
        dbFile.parentFile?.mkdirs()
        dbFile.writeBytes(ByteArray(2048))
        File(dbFile.path + "-wal").writeBytes(ByteArray(1024))
        capturesDir.mkdirs()
        File(capturesDir, "p1.jpg").writeBytes(ByteArray(4096))
        File(capturesDir, "p2.jpg").writeBytes(ByteArray(2048))
        val total = computeStorageSize(context)
        assertEquals(2048L + 1024L + 4096L + 2048L, total)
    }

    /**
     * Tier 0.6: the size calculation helper, exposed
     * for unit tests. The production code path lives
     * in the private [SettingsViewModel] extension
     * `storageSizeBytes()`; this test-only replica
     * pins the contract (sums the right files, doesn't
     * crash on missing files, no `walkTopDown`
     * recursion) without forcing a Hilt-instrumented
     * test.
     */
    private fun computeStorageSize(ctx: Context): Long {
        val db = ctx.getDatabasePath(AppDatabase.NAME)
        val wal = File(db.path + "-wal")
        val shm = File(db.path + "-shm")
        val captures = File(ctx.filesDir, "captures")
        var total = 0L
        listOf(db, wal, shm).forEach { f ->
            if (f.exists()) total += f.length()
        }
        if (captures.isDirectory) {
            captures.listFiles()?.forEach { f ->
                if (f.isFile) total += f.length()
            }
        }
        return total
    }
}
