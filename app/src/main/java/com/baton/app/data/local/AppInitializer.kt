package com.baton.app.data.local

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * M3-T1: one-shot startup tasks that have to run before [AppDatabase]
 * is opened. Specifically: wipe the M2 unencrypted `baton.db` on
 * first M3 run, so the SQLCipher-encrypted DB can be created
 * fresh in its place.
 *
 * **Why the wipe.** The M2 build opened a plain Room DB. The M3
 * build opens the same path through SQLCipher. SQLCipher's
 * [net.zetetic.database.sqlcipher.SupportOpenHelperFactory] would
 * fail (or worse, silently produce a DB that is partly encrypted
 * and partly not) on the old plain file. The cheapest safe path
 * is to delete the file and let Room recreate it.
 *
 * **Detection.** We can't tell at runtime whether the on-disk DB
 * is encrypted or not without opening it (which would require the
 * key we haven't generated yet). So the rule is: on first M3 run
 * (i.e. when [com.baton.app.data.auth.SecurePreferences.hasDatabasePassphrase]
 * is `false` AND an unencrypted DB is on disk), delete the file.
 * The AppDatabase version bump (3) ensures the destructive
 * migration also fires on the very first Room read.
 *
 * **Idempotency.** Called from [com.baton.app.BatonApplication.onCreate]
 * via Hilt's `@HiltAndroidApp` path. Safe to call on every launch.
 *
 * **Future schema migrations.** When the schema actually changes
 * (not just the version bump), this class should add a check that
 * deletes the file only when the new version differs from the
 * stored version. For M3 there's no stored version yet, so the
 * "wipe if M2 file present + no passphrase set" rule covers it.
 */
@Singleton
class AppInitializer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val securePreferences: com.baton.app.data.auth.SecurePreferences,
) {

    fun runOnAppStart() {
        val dbFile = context.getDatabasePath(AppDatabase.NAME)
        val hasPassphrase = securePreferences.hasDatabasePassphrase()
        if (dbFile.exists() && !hasPassphrase) {
            // M2 unencrypted DB on disk; M3 needs it gone before
            // the new SQLCipher-backed Room reads the same path.
            val deleted = dbFile.delete()
            val walDeleted = File(dbFile.absolutePath + "-wal").let { if (it.exists()) it.delete() else true }
            val shmDeleted = File(dbFile.absolutePath + "-shm").let { if (it.exists()) it.delete() else true }
            Log.i(
                TAG,
                "M2->M3 transition: wiped plain baton.db (deleted=$deleted, wal=$walDeleted, shm=$shmDeleted)",
            )
        }
        // Pre-warm the passphrase so the first DB read doesn't
        // synchronously generate the key. This is a no-op on a
        // brand-new install (where the file is gone) and on a
        // subsequent launch (where the key is already persisted).
        securePreferences.databasePassphrase()
    }

    /**
     * M3-T4 (sign-out path). Called by the sign-out flow BEFORE
     * the AuthRepository.signOut() call so the next DB read fails
     * and the file is wiped. After this returns, the AppDatabase
     * singleton is in a "broken" state; the next app start (or the
     * next sign-in) re-creates a fresh DB.
     */
    fun runOnSignOut() {
        securePreferences.clearDatabasePassphrase()
        val dbFile = context.getDatabasePath(AppDatabase.NAME)
        if (dbFile.exists()) {
            dbFile.delete()
            File(dbFile.absolutePath + "-wal").takeIf { it.exists() }?.delete()
            File(dbFile.absolutePath + "-shm").takeIf { it.exists() }?.delete()
            Log.i(TAG, "sign-out: wiped baton.db (encrypted passphrase cleared)")
        }
    }

    companion object {
        private const val TAG = "BatonAppInit"
    }
}
