package com.kaavalan.note

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.baton.app.data.local.AppInitializer
import com.baton.app.data.work.WorkManagerInitializer
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class KaavalanApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    /**
     * M3-T1: one-shot startup tasks. Injected by Hilt's `@HiltAndroidApp`
     * path. Runs in [onCreate] before any other Hilt-injected component
     * is touched, so by the time [com.baton.app.di.DatabaseModule] is
     * asked for the [com.baton.app.data.local.AppDatabase] the
     * M2 plain DB is already wiped and the SQLCipher passphrase is
     * generated.
     */
    @Inject lateinit var appInitializer: AppInitializer

    /**
     * v1.8.0 (PROD-READINESS-P2-#3): the user bootstrap.
     * Injected by Hilt so the device-owner row is in
     * place before any UI code reads the [UserDao].
     */
    @Inject lateinit var userBootstrap: com.baton.app.data.user.UserBootstrap

    // v2.0.2 (PM rating): the DB preflight. Runs a
    // `SELECT 1` on first launch and sets the
    // "database corrupt" flag in [SecurePreferences]
    // if the open throws. The Settings sheet reads
    // the flag and surfaces a "Database error — tap
    // to erase and start fresh" banner. The preflight
    // is async so a slow DB open doesn't block the
    // launcher activity.
    @Inject lateinit var databasePreflight: com.baton.app.data.local.DatabasePreflight

    // v2.1.1 (security): the Google OAuth client.
    // Injected so the cold-start path can check
    // [GoogleOAuthClient.isSignedIn] and only
    // schedule the daily Drive backup when the
    // user has signed in. The v2.1.0 path scheduled
    // it unconditionally, which meant the worker
    // fired on every cold start of a device that
    // had never signed in and dumped a failure
    // result to the WorkManager log.
    @Inject lateinit var googleOAuthClient: com.baton.app.data.backup.GoogleOAuthClient

    // v2.1.1 (security): the encrypted-preferences
    // store. The cold-start path reads
    // [SecurePreferences.getBackupEncryptionKeyHash]
    // so the daily Drive backup is only scheduled
    // when the user has set a passphrase.
    @Inject lateinit var securePreferences: com.baton.app.data.auth.SecurePreferences

    override fun onCreate() {
        super.onCreate()
        // v1.9.0 (PROD-READINESS-P3-P1-#1): install
        // the uncaught-exception handler BEFORE
        // anything else runs. The handler writes
        // a structured crash log to
        // `cacheDir/crashes/`; the user can share
        // it with support on the next launch.
        // Installed as the SECOND handler (the
        // system default is the first; this wraps
        // it so the user still gets the standard
        // "App has stopped" dialog).
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            com.baton.app.ui.util.CrashLog.write(this, throwable)
            previous?.uncaughtException(thread, throwable)
        }
        appInitializer.runOnAppStart()
        // v2.0.2 (PM rating): the database preflight.
        // Runs after [appInitializer] (which has loaded
        // the lib + pre-warmed the passphrase) but
        // before any UI code reads a DAO. The preflight
        // is async so a slow DB open doesn't block the
        // launcher activity; the Settings sheet reads
        // the flag on its first render.
        GlobalScope.launch(Dispatchers.IO) {
            runCatching { databasePreflight.runPreflight() }
        }
        // v1.8.0 (PROD-READINESS-P2-#3): ensure the
        // device-owner row exists. Idempotent; the row
        // is in place before any UI code reads the
        // UserDao (the bootstrap completes in <1 ms on
        // a real DB).
        GlobalScope.launch(Dispatchers.IO) {
            runCatching { userBootstrap.ensureDeviceOwner() }
        }
        // v1.5.0 vault mode: no cloud sync. The
        // [com.baton.app.data.work.WorkManagerInitializer] periodic
        // drain + capture-sync schedules are intentionally NOT
        // called. The per-write `enqueueCaptureSync` in
        // [com.baton.app.data.captures.RoomCaptureRepository] still
        // fires one-shot workers (a no-op without Supabase creds);
        // the periodic schedule was the one that mattered for the
        // "I was offline and now I'm not" self-heal, and v1.5.0
        // has no offline-to-online path because there is no online.
        // The code paths are left in place so a future Settings
        // toggle can re-enable cloud sync without a refactor.
        //
        // v1.8.0 (PROD-READINESS-P0-#1): the daily local
        // backup IS scheduled. The backup is local (writes to
        // filesDir, no network), so the v1.5.0 vault-mode
        // "no cloud = no WorkManager" rule doesn't apply.
        // WorkManagerInitializer.scheduleBackup is idempotent
        // (KEEP policy) so calling it on every cold start is
        // a no-op after the first.
        com.baton.app.data.work.WorkManagerInitializer.scheduleBackup(this)
        // v1.8.0 (PROD-READINESS-P2-#5): the daily
        // retention sweep is also scheduled. Same
        // KEEP-on-re-enqueue idempotency as the backup.
        com.baton.app.data.work.WorkManagerInitializer.scheduleRetention(this)
        // v2.1.1 (security): only schedule the daily
        // Google Drive backup when the user has both
        // (a) signed in to Google and (b) set a backup
        // passphrase. v2.1.0 scheduled unconditionally,
        // which meant the worker fired on every cold
        // start of a device that had never signed in
        // and dumped a failure result to the
        // WorkManager log. The user re-enables the
        // schedule the next time they sign in.
        if (googleOAuthClient.isSignedIn() &&
            securePreferences.getBackupEncryptionKeyHash() != null) {
            com.baton.app.data.work.WorkManagerInitializer.scheduleDriveBackup(this)
        }
    }

    /**
     * M3-T2: WorkManager on-demand init. We declare the
     * [Configuration.Provider] interface so Hilt can inject the
     * [HiltWorkerFactory]; the actual [WorkManager.getInstance] call
     * is deferred to first use (see the M3 manifest change that
     * disables the auto-init ContentProvider).
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}
