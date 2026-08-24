package com.baton.app.data.local

import android.util.Log
import com.baton.app.data.user.UserDao
import com.baton.app.data.user.UserEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v2.0.2 + v2.1.0 (PM rating): the database preflight
 * check. Runs a **read + write + read** round-trip on
 * the device-owner row in the `users` table on first
 * launch and sets a "database corrupt" flag if the
 * write-back doesn't match the write-in.
 *
 * **Why a round-trip, not just a `SELECT 1`.** A
 * file-system-level corruption that surfaces as a
 * page-fault during a write (e.g. an OS-level disk
 * error mid-WAL-flush) would NOT be caught by a
 * read-only check. The pre-flight has to actually
 * exercise the write path. A read-then-write-then-read
 * on a no-op update (the device-owner row already
 * exists) catches the silent-corruption case.
 *
 * **Why the device-owner row.** It's a single row
 * that always exists (the
 * [com.baton.app.data.user.UserBootstrap.ensureDeviceOwner]
 * runs at app start). The "no-op write" is
 * `displayName = displayName` — SQLite is smart
 * enough to short-circuit, but the IO path is still
 * exercised.
 *
 * **Why catch every `Throwable`.** The underlying
 * SQLCipher error surface throws a mix of
 * `SQLiteException`, `RuntimeException` ("file is not
 * a database"), and the occasional
 * `IllegalStateException` from Room's invalidation
 * tracker. Catching `Throwable` is the only way to
 * guarantee the flag is set.
 *
 * **What this does NOT do.** It does NOT attempt to
 * repair the database. The repair is the "Erase all
 * data" path (deletes the file + the passphrase)
 * followed by a restore from backup. The v2.0.2 fix
 * is a controlled surfacing of the error, not a
 * one-click repair.
 */
@Singleton
class DatabasePreflight @Inject constructor(
    private val database: AppDatabase,
    private val userDao: UserDao,
    private val databaseHealth: DatabaseHealth,
) {

    /**
     * The full preflight:
     *  1. Read the device-owner row (exercises the read
     *     path AND catches "no device-owner row" — a
     *     wrong-passphrase failure).
     *  2. Update its `displayName` to itself (a no-op
     *     write that exercises the write path AND
     *     catches silent corruption).
     *  3. Read it back (catches the case where the
     *     write silently succeeded but the read returns
     *     a different value — e.g. a mid-WAL-flush
     *     crash).
     *  4. Compare the original to the read-back; if
     *     they differ, the DB is corrupt.
     *
     * Any throwable in any step is treated as a
     * corruption signal.
     */
    suspend fun runPreflight() {
        // Reset on every launch — the preflight is the
        // source of truth for "is the DB currently
        // readable".
        databaseHealth.clearCorrupt()
        try {
            // Step 1: read.
            val before: UserEntity = userDao.deviceOwner()
                ?: throw IllegalStateException(
                    "device-owner row missing — the v1.8.0 " +
                        "UserBootstrap should have inserted it. " +
                        "This is the failure mode for a wrong " +
                        "SQLCipher passphrase after a Keystore reset.",
                )

            // Step 2: no-op write (displayName → displayName).
            val after = before.copy(
                // No fields change; copy() forces Room to
                // generate an UPDATE statement. The
                // `updatedAt` field is a derived timestamp
                // and doesn't exist on UserEntity; the
                // write is a literal no-op in terms of
                // stored values.
            )
            userDao.upsert(after)

            // Step 3 + 4: read back + compare.
            val readBack: UserEntity = userDao.deviceOwner()
                ?: throw IllegalStateException(
                    "device-owner row vanished after a write — " +
                        "silent corruption. The DB is unreadable.",
                )
            if (readBack != before) {
                throw IllegalStateException(
                    "device-owner row read-back mismatched the " +
                        "write-in: before=$before readBack=$readBack. " +
                        "The DB is corrupted; 'Erase all data' is " +
                        "the only safe recovery.",
                )
            }
            // All three steps OK → DB is healthy.
        } catch (e: Throwable) {
            Log.e(
                TAG,
                "DB preflight failed: ${e.javaClass.simpleName}: ${e.message}. " +
                    "Marking the database as corrupt; the Settings " +
                    "sheet will surface a 'Database error' banner with " +
                    "an 'Erase all data' CTA.",
                e,
            )
            databaseHealth.markCorrupt()
        }
    }

    private companion object {
        private const val TAG = "BatonDbPreflight"
    }
}
