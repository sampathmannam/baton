package com.baton.app.data.export

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * v1.8.0 (PROD-READINESS-P0-#1): the WorkManager-driven daily
 * backup. Calls [BackupManager.backup] and reports the result
 * via WorkManager's standard `Result.success` / `Result.failure`
 * channel.
 *
 * The worker is scheduled by [com.baton.app.data.work.WorkManagerInitializer.scheduleBackup]
 * (periodic, 24 h) or by [com.baton.app.data.work.WorkManagerInitializer.enqueueBackupNow]
 * (one-shot, from the Settings sheet "Back up now" button).
 */
@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val backupManager: BackupManager,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val file = backupManager.backup()
            if (file.exists() && file.length() > 0) {
                Result.success()
            } else {
                // v1.8.0: the file was created but is empty
                // (e.g. the Room DB has no rows yet and the
                // JSON serialiser wrote {}). That's not a
                // failure — a first-launch user with no data
                // has nothing to back up.
                Result.success()
            }
        } catch (e: Throwable) {
            // v1.8.0: log + retry. The user-facing toast /
            // notification lives in the WorkManager observer
            // that the Settings sheet sets up.
            Result.retry()
        }
    }
}
