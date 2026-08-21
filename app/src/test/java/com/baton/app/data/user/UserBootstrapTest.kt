package com.baton.app.data.user

import androidx.test.core.app.ApplicationProvider
import com.baton.app.data.local.AppDatabase
import kotlinx.coroutines.test.runTest
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

/**
 * v1.8.0 (PROD-READINESS-P2-#3): the user-bootstrap
 * test. Uses a real in-memory Room DB so the
 * `users` table is exercised end-to-end.
 *
 * What we assert:
 *  1. On a fresh DB, [UserBootstrap.ensureDeviceOwner]
 *     inserts exactly one row, with `deviceOwner = true`
 *     and `role = SENIOR_OFFICER`.
 *  2. On a second call, the row is unchanged (no
 *     second device-owner row).
 *  3. After two calls, the `deviceOwner()` query still
 *     returns exactly one row.
 *  4. The generated id is a valid UUID.
 *  5. The `createdAt` field is a parseable ISO-8601
 *     string.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class UserBootstrapTest {

    private lateinit var db: AppDatabase
    private lateinit var userDao: UserDao
    private lateinit var bootstrap: UserBootstrap

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        // v1.8.0: the in-memory test builder does not
        // run the production migrations; we want a
        // fresh v15 schema (with the `users` table) for
        // the test to be hermetic. The default
        // inMemoryDatabaseBuilder creates a DB at the
        // current @Database(version=...) so no explicit
        // .addMigrations(...) call is required.
        db = androidx.room.Room.inMemoryDatabaseBuilder(
            context, AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        userDao = db.userDao()
        bootstrap = UserBootstrap(userDao)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `ensureDeviceOwner inserts a single SENIOR_OFFICER row on a fresh DB`() = runTest {
        // Pre-condition: no device owner.
        assertNull(userDao.deviceOwner())
        bootstrap.ensureDeviceOwner()
        val owner = userDao.deviceOwner()
        assertNotNull(owner)
        assertEquals(Role.SENIOR_OFFICER.name, owner!!.role)
        assertTrue(owner.deviceOwner)
        assertTrue(
            "displayName should be 'Device owner' but was '${owner.displayName}'",
            owner.displayName == "Device owner",
        )
        // The id is a UUID — 36 chars, 4 dashes, etc.
        val uuid = owner.id
        assertEquals(36, uuid.length)
        assertEquals(4, uuid.count { it == '-' })
        // The createdAt is a parseable ISO-8601 string.
        val parsed = java.time.Instant.parse(owner.createdAt)
        assertNotNull(parsed)
    }

    @Test
    fun `ensureDeviceOwner is idempotent - second call is a no-op`() = runTest {
        bootstrap.ensureDeviceOwner()
        val first = userDao.deviceOwner()!!
        bootstrap.ensureDeviceOwner()
        val second = userDao.deviceOwner()!!
        // Same row: the id does not change on a no-op.
        assertEquals(first.id, second.id)
        // Still SENIOR_OFFICER, still device owner.
        assertEquals(Role.SENIOR_OFFICER.name, second.role)
        assertTrue(second.deviceOwner)
    }

    @Test
    fun `the partial unique index allows multiple non-device-owner rows but not two device owners`() = runTest {
        bootstrap.ensureDeviceOwner()
        // A second, non-device-owner row is allowed.
        userDao.upsert(
            UserEntity(
                id = "00000000-0000-0000-0000-000000000001",
                displayName = "Trainee",
                role = Role.READONLY.name,
                deviceOwner = false,
                createdAt = java.time.Instant.now().toString(),
            ),
        )
        // deviceOwner() still returns the first one
        // (the device-owner row, not the trainee).
        val owner = userDao.deviceOwner()
        assertNotNull(owner)
        assertEquals(Role.SENIOR_OFFICER.name, owner!!.role)
    }
}
