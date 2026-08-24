package com.kaavalan.note.data.local

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.kaavalan.note.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.launch

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
 * (i.e. when [com.kaavalan.note.data.auth.SecurePreferences.hasDatabasePassphrase]
 * is `false` AND an unencrypted DB is on disk), delete the file.
 * The AppDatabase version bump (3) ensures the destructive
 * migration also fires on the very first Room read.
 *
 * **Idempotency.** Called from [com.kaavalan.note.BatonApplication.onCreate]
 * via Hilt's `@HiltAndroidApp` path. Safe to call on every launch.
 *
 * v1.2.1 (BUG-DATA-021): the previous version re-threw
 * `UnsatisfiedLinkError` from `System.loadLibrary("sqlcipher")` —
 * which crashed the app before the first frame. Now we log and
 * continue: if the library is genuinely missing the next DB read
 * surfaces a clearer error to the user; if the lib is already
 * loaded (e.g. on a warm restart) `loadLibrary` is a no-op anyway.
 * The body of `runOnAppStart` is also guarded by an `@Volatile`
 * boolean so the wipe + passphrase pre-warm run exactly once per
 * process, not on every Compose recomposition or Hilt rebuild.
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
    private val securePreferences: com.kaavalan.note.data.auth.SecurePreferences,
    private val fixtureLoader: com.kaavalan.note.data.dev.FixtureLoader,
    @com.kaavalan.note.di.ApplicationScope private val appScope: kotlinx.coroutines.CoroutineScope,
) {

    @Volatile
    private var appStartRan: Boolean = false

    @Volatile
    private var signOutRan: Boolean = false

    /**
     * v1.9.9 (A6 audit fix): the synthetic-data fixture is a
     * **debug-only** feature (see [com.kaavalan.note.data.dev.FixtureLoader]
     * class docstring: "Not exposed in release builds."). The
     * Settings → Developer → "Load test data" menu is gated on
     * `BuildConfig.DEBUG`, but the auto-reseed on every app
     * start (this class) was NOT. A release-build user who
     * never touched the debug menu had `storedFixtureVersion=0`
     * and `asset.version=2`; the very first launch ran
     * [FixtureLoader.reseedIfStale], which cleared all 6
     * tables (persons / instructions / instructions_fts /
     * captures / tags / instruction_tags) before re-inserting
     * the fixture. Net effect: the user opened the app, all
     * their real casework was gone, and the synthetic fixture
     * (designed to find UI bugs on a 12-row sample) replaced
     * it. Subsequent launches were a no-op because
     * `storedFixtureVersion` had been updated to the asset
     * version — but the damage was already done on launch #1.
     *
     * The fix gates the auto-reseed on `BuildConfig.DEBUG`. A
     * release-build app's first launch no longer touches the
     * user's data. The v1.7.1→v1.7.2 migration scenario that
     * justified the original "runs on every build" comment is
     * irrelevant in v1.9.9: that migration was two years
     * ago, the synthetic-data dates it was fixing were
     * explicitly debug-fixture data (no real user ever had
     * "year 3995" worry-box dates), and a release-build user
     * would not benefit from the auto-update because they
     * never loaded the fixture in the first place.
     *
     * The [isDebugBuild] indirection is a
     * [VisibleForTesting] hook so a unit test can verify the
     * release-build contract without recompiling against the
     * `release` variant.
     */
    @VisibleForTesting
    internal var isDebugBuild: Boolean = BuildConfig.DEBUG

    fun runOnAppStart() {
        // v1.2.1 (BUG-DATA-021 + BUG-DATA-023): guard against
        // double-init. The body runs at most once per process; on
        // warm restarts (or Hilt rebuilding this singleton) the
        // second call is a no-op.
        if (appStartRan) return

        // M3-T1 fix: `net.zetetic:sqlcipher-android:4.6.1` ships the
        // native `libsqlcipher.so` inside the AAR but does NOT
        // auto-load it. Without this call the first Room read fails
        // with `No implementation found for nativeOpen (is the library
        // loaded, e.g. System.loadLibrary?)`. The old `SQLiteDatabase
        // .loadLibs(context)` from 4.5.x is gone in 4.6+. We just do
        // it ourselves here, once, before anything touches the DB.
        // Idempotent: loadLibrary is a no-op if the lib is already
        // loaded in this process.
        try {
            System.loadLibrary("sqlcipher")
        } catch (e: UnsatisfiedLinkError) {
            // v1.2.1 (BUG-DATA-021): do NOT re-throw. The previous
            // version crashed the app before the first frame if the
            // lib was missing (e.g. on a stripped emulator image).
            // We log + continue; the next DB read will surface the
            // real "file is not a database" error which is more
            // actionable for the user. If the lib is genuinely
            // missing, the Room open fails — the user sees the
            // AuthScreen with a broken sign-in rather than a hard
            // crash. The crash log goes to crash reporting.
            Log.e(TAG, "loadLibrary(sqlcipher) failed: ${e.message}. " +
                "DB reads will fail; check sqlcipher-android packaging.")
            // Still mark as ran so we don't re-attempt on every
            // recomposition (which would just spam the log).
            appStartRan = true
            return
        }

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
        // v1.6.4: debug builds auto-load the synthetic fixture
        // on first launch so the systematic-debugging test pass
        // has realistic data without needing to navigate to
        // Settings → Developer → Load test data. DISABLED —
        // the load is 200 instructions and the system UI ANRs
        // for 2-3s during the load on a Pixel 6 emulator. The
        // user can trigger the load from Settings → Developer
        // → "Load test data" or "Clear & reload" after the
        // first frame paints.
        //
        // v1.7.3 (P0-A): replaced the isEmpty auto-load with a
        // version-gated reseed. The fixture asset now carries a
        // top-level `version` field; on every launch we compare
        // it against the stored version in SharedPreferences. If
        // the stored version is strictly less, we re-seed in a
        // background coroutine. This is the path that closes the
        // v1.7.2 gap where existing users' Room DBs still had
        // the v1.7.1 worry-box dates (year 3995). v1.9.9 (A6):
        // gated on [isDebugBuild] so release builds do not
        // touch the user's data on first launch. The
        // `Run on every build (not DEBUG-gated)` rationale from
        // v1.7.3 was wrong for production: see the
        // [isDebugBuild] docstring for the failure mode.
        if (isDebugBuild) {
            appScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { fixtureLoader.reseedIfStale() }
                    .onSuccess { report ->
                        if (report != null) {
                            Log.i(TAG, "auto-reseeded fixture: $report")
                        }
                    }
                    .onFailure { e ->
                        Log.e(TAG, "auto-reseed failed: ${e.message}")
                    }
            }
        }
        appStartRan = true
    }

    /**
     * M3-T4 (sign-out path). Called by the sign-out flow BEFORE
     * the AuthRepository.signOut() call so the next DB read fails
     * and the file is wiped. After this returns, the AppDatabase
     * singleton is in a "broken" state; the next app start (or the
     * next sign-in) re-creates a fresh DB.
     *
     * v1.2.1 (BUG-DATA-022 + BUG-DATA-024): idempotent. A second
     * call (e.g. from a deep link, accessibility action, or a
     * double-tap before the first call's coroutine completes) is
     * a no-op once the first call has finished wiping. The
     * passphrase clear and the file delete are already individually
     * safe (clearDatabasePassphrase is a no-op if no key is
     * stored, and `dbFile.delete()` is a no-op on a missing file);
     * the guard makes the contract explicit + testable.
     *
     * Note: the [com.kaavalan.note.ui.settings.SettingsViewModel]
     * already has a `_signingOut: Boolean` guard at the VM level;
     * this is the data-layer belt-and-suspenders for callers that
     * go around the VM.
     */
    fun runOnSignOut() {
        if (signOutRan) return
        securePreferences.clearDatabasePassphrase()
        val dbFile = context.getDatabasePath(AppDatabase.NAME)
        if (dbFile.exists()) {
            dbFile.delete()
            File(dbFile.absolutePath + "-wal").takeIf { it.exists() }?.delete()
            File(dbFile.absolutePath + "-shm").takeIf { it.exists() }?.delete()
            Log.i(TAG, "sign-out: wiped baton.db (encrypted passphrase cleared)")
        }
        signOutRan = true
    }

    /**
     * v1.2.1 (BUG-DATA-024): reset the signOut guard so a fresh
     * sign-in can re-init cleanly. The next `runOnAppStart` (on
     * process death + restart, or the next launch) flips it back
     * to false via the field initialiser. This method is for the
     * rare case where a sign-out / sign-in happens in the same
     * process (e.g. Compose rebuild during a deep-link flow) and
     * the user expects the AppInitializer to be a clean slate.
     */
    fun resetSignOutGuard() {
        signOutRan = false
    }

    companion object {
        private const val TAG = "BatonAppInit"
    }
}
