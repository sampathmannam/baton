package com.baton.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.baton.app.data.local.AppInitializer
import com.baton.app.data.work.WorkManagerInitializer
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class BatonApplication : Application(), Configuration.Provider {

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

    override fun onCreate() {
        super.onCreate()
        appInitializer.runOnAppStart()
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
