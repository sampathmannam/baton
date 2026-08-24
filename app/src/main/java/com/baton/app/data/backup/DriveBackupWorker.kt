package com.baton.app.data.backup

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * v2.1.0 (PM rating): the WorkManager-driven daily
 * Google Drive backup. Mirrors the v1.8.0
 * [com.baton.app.data.export.BackupWorker] pattern.
 *
 * **The recovery phrase problem.** A daily
 * background worker can't prompt the user for the
 * 12-word recovery phrase (the worker runs even when
 * the screen is off). For v2.1.0, the passphrase is
 * looked up from [com.baton.app.data.auth.SecurePreferences]
 * — the user set it in Settings on first backup, and
 * the worker re-uses it for every subsequent auto-
 * backup. (The v2.1.0 UX is: "first time you tap
 * 'Back up now', the Settings sheet prompts for the
 * passphrase + saves it. The daily worker re-uses
 * the saved passphrase.")
 *
 * **The failure mode.** If the user revoked the
 * app's Drive access in their Google account
 * settings, [GoogleOAuthClient.getAccessToken]
 * returns `null` and the worker bails out with
 * [Result.retry]. The Settings sheet re-renders
 * the "Sign in" CTA on the next launch.
 */
@HiltWorker
class DriveBackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val driveBackupManager: DriveBackupManager,
    private val securePreferences: com.baton.app.data.auth.SecurePreferences,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // The passphrase was set on first manual backup;
        // it's stored in SecurePreferences as a SHA-256
        // hash. We use the HASH as the encryption key
        // source (a 32-byte secret is plenty for
        // AES-256-GCM). The same hash is required to
        // restore on another device — the user reads
        // it from a "Backup settings" page that
        // shows the hash (not the phrase).
        val passphraseHash = securePreferences.getBackupEncryptionKeyHash()
            ?: return Result.failure(
                androidx.work.workDataOf(
                    "reason" to "no-passphrase-set",
                ),
            )
        return try {
            val file = driveBackupManager.backUpNow(
                passphrase = passphraseHash.toCharArray(),
            )
            if (file.sizeBytes > 0) Result.success() else Result.retry()
        } catch (e: DriveBackupManager.DriveBackupException.NotSignedIn) {
            // The user revoked access or the silent
            // sign-in failed. Retry next day; the user
            // can also re-sign-in manually.
            Result.retry()
        } catch (e: Throwable) {
            Result.failure(
                androidx.work.workDataOf(
                    "error" to (e.message ?: e::class.java.simpleName),
                ),
            )
        }
    }
}
