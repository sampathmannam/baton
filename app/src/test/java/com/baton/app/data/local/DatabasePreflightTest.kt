package com.baton.app.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.baton.app.data.user.UserDao
import com.baton.app.data.user.UserEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v2.1.0 (PM rating): the [DatabasePreflight] write+read
 * round-trip. The PM rating called this out: a `SELECT
 * 1` preflight passes when the file is readable but
 * fails to catch the "write silently corrupts" case
 * (page-fault mid-WAL-flush, OS-level disk error, etc.).
 *
 * The v2.1.0 preflight is a full read + write + read
 * round-trip. The test seeds a device-owner row, runs
 * the preflight, and asserts the corrupt flag stays
 * `false`. A second test asserts the flag is `true` when
 * the device-owner row is missing (the
 * wrong-passphrase failure mode).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DatabasePreflightTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var db: AppDatabase
    private lateinit var userDao: UserDao
    private lateinit var preflight: DatabasePreflight
    private lateinit var databaseHealth: DatabaseHealth

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(testDispatcher.asExecutor())
            .setTransactionExecutor(testDispatcher.asExecutor())
            .build()
        userDao = db.userDao()
        databaseHealth = DatabaseHealth(context)
        preflight = DatabasePreflight(db, userDao, databaseHealth)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `preflight passes when the device-owner row is healthy`() = runTest {
        userDao.upsert(
            UserEntity(
                id = "u1",
                displayName = "Ramu",
                role = "SENIOR_OFFICER",
                deviceOwner = true,
                createdAt = "2026-08-24T00:00:00Z",
            ),
        )
        advanceUntilIdle()

        preflight.runPreflight()
        advanceUntilIdle()

        assertFalse(
            "preflight should not mark the DB corrupt for a healthy device-owner row",
            databaseHealth.isCorrupt(),
        )
    }

    @Test
    fun `preflight marks corrupt when the device-owner row is missing`() = runTest {
        // No seed: the device-owner row is absent. This is
        // the failure mode for a wrong SQLCipher passphrase
        // after a Keystore reset.
        advanceUntilIdle()

        preflight.runPreflight()
        advanceUntilIdle()

        assertTrue(
            "preflight should mark the DB corrupt when the device-owner row is missing",
            databaseHealth.isCorrupt(),
        )
    }
}
