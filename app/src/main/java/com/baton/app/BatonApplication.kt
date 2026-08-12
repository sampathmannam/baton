package com.baton.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.baton.app.data.local.AppInitializer
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
