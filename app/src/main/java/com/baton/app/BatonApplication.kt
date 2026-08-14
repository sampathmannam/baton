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
        // v1.2 root-cause fix (F-CRIT-07): schedule a periodic
        // sync drain so the outbox self-heals after process kill
        // + reopen. The per-write drain in the VMs remains the
        // foreground path; this is the "I was offline and now I'm
        // not" safety net.
        WorkManagerInitializer.schedulePeriodicDrain(this)
        // v1.4.3 (F-09 / F-20 wiring): schedule a periodic
        // capture-sync worker. v1.4.2-final added
        // [com.baton.app.data.sync.CaptureSyncWorker] but never
        // registered it with WorkManager, so every capture that
        // [com.baton.app.data.captures.RoomCaptureRepository] wrote
        // with `syncStatus = PENDING_INSERT` stayed dirty forever.
        // The 5-minute interval is intentionally faster than the
        // 15-minute sync drain above: captures are time-sensitive
        // (the user is waiting on the "synced" badge) and a dirty
        // capture row only blocks that one row, not the whole outbox.
        WorkManagerInitializer.schedulePeriodicCaptureSync(this)
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
