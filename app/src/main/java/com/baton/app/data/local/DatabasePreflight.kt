package com.baton.app.data.local

import android.database.sqlite.SQLiteException
import android.util.Log
import com.baton.app.data.auth.SecurePreferences
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v2.0.2 (PM rating): the database preflight check. Runs
 * a `SELECT 1` on first launch and sets a
 * "database corrupt" flag if the query throws.
 *
 * **Why this exists.** The `AppInitializer` already
 * catches the `UnsatisfiedLinkError` for the lib
 * load failure, but it does NOT catch the runtime
 * DB open failure. A wrong-passphrase (e.g. after a
 * Keystore reset on a new device) or a corrupt file
 * (e.g. after a force-stop during a write) would
 * propagate to the first DAO call, throw, and crash
 * the activity. This preflight detects the throw
 * early and sets a flag the Settings sheet reads
 * to surface a "Database error" banner with a
 * one-tap path to the "Erase all data" flow.
 *
 * **What this does NOT do.** It does NOT attempt
 * to repair the database. The repair is the
 * "Erase all data" path (deletes the file + the
 * passphrase) followed by a restore from backup.
 * The v2.0.2 fix is a controlled surfacing of the
 * error, not a one-click repair.
 */
@Singleton
class DatabasePreflight @Inject constructor(
    private val database: AppDatabase,
    private val securePreferences: SecurePreferences,
) {

    /**
     * Run a `SELECT 1` on a small table. Catches every
     * throwable (not just `SQLiteException`) because
     * the underlying SQLCipher error surface throws
     * a mix of `SQLiteException`, `RuntimeException`
     * ("file is not a database"), and the occasional
     * `IllegalStateException` from Room's invalidation
     * tracker.
     *
     * The flag is reset on every call so a transient
     * error (e.g. a one-time disk full) doesn't
     * persist across launches.
     */
    suspend fun runPreflight() {
        // Reset on every launch — the preflight is the
        // source of truth for "is the DB currently
        // readable".
        securePreferences.clearDatabaseCorrupt()
        try {
            database.openHelper.writableDatabase
                .query("SELECT 1", arrayOf())
                .use { cursor -> cursor.moveToFirst() }
            // No throw → DB is healthy.
        } catch (e: Throwable) {
            Log.e(
                TAG,
                "DB preflight failed: ${e.javaClass.simpleName}: ${e.message}. " +
                    "Marking the database as corrupt; the Settings " +
                    "sheet will surface a 'Database error' banner with " +
                    "an 'Erase all data' CTA.",
                e,
            )
            securePreferences.markDatabaseCorrupt()
        }
    }

    private companion object {
        private const val TAG = "BatonDbPreflight"
    }
}
