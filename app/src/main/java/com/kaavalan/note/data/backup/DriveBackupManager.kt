package com.kaavalan.note.data.backup

import android.util.Log
import com.kaavalan.note.data.export.PlainExporter
import com.kaavalan.note.data.export.PlainImporter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v2.1.0 (PM rating): the Google Drive backup
 * orchestrator. The user said "like WhatsApp" — the
 * local DB stays on the device, a backup goes to
 * Google Drive automatically, and the user can
 * restore on a new device.
 *
 * **The flow.**
 *
 *  1. **Sign-in.** [GoogleOAuthClient] opens a Chrome
 *     Custom Tab to Google's OAuth page. The user
 *     signs in + grants the `drive.appdata` scope.
 *     Google redirects to a custom scheme Baton
 *     catches; the auth code is exchanged for an
 *     access + refresh token. The refresh token is
 *     stored in `SecurePreferences`; the access token
 *     is held in memory.
 *
 *  2. **Back up now.** [backUpNow] takes a snapshot
 *     via [PlainExporter], encrypts the bytes via
 *     [BackupCrypto] (PBKDF2 from the recovery
 *     phrase), uploads via [DriveRestApi] to the
 *     user's `appDataFolder`. The Drive file ID is
 *     returned.
 *
 *  3. **Restore from list.** [listBackups] enumerates
 *     the `appDataFolder`. [restore] picks one by ID,
 *     downloads, decrypts, and imports via
 *     [PlainImporter].
 *
 *  4. **Daily auto-backup.** [DriveBackupWorker] runs
 *     on a 24h WorkManager schedule and calls
 *     [backUpNow].
 *
 * **Encryption is the key choice.** The bytes are
 * encrypted client-side before upload. Even Google
 * (or a future attacker with the user's Google
 * account) cannot read the bytes without the recovery
 * phrase.
 */
@Singleton
class DriveBackupManager @Inject constructor(
    private val httpClient: io.ktor.client.HttpClient,
    private val plainExporter: PlainExporter,
    private val plainImporter: PlainImporter,
    private val crypto: BackupCrypto,
    private val oauth: GoogleOAuthClient,
) {

    private val driveApi = DriveRestApi(httpClient)

    /**
     * Back up the local DB to the user's Drive
     * `appDataFolder`. Returns the new Drive file
     * [DriveRestApi.DriveFile].
     *
     * @param passphrase the recovery phrase (12 words,
     *   space-joined) used to derive the AES key. The
     *   same phrase is required to restore on another
     *   device.
     */
    suspend fun backUpNow(
        passphrase: CharArray,
    ): DriveRestApi.DriveFile {
        require(passphrase.isNotEmpty()) { "passphrase must not be empty" }
        // 1. Refresh the access token (silent sign-in).
        val accessToken = oauth.getAccessToken()
            ?: throw DriveBackupException.NotSignedIn()

        // 2. Snapshot.
        val snapshot = plainExporter.snapshot()
        val json = plainExporter.toJson(snapshot).toByteArray(Charsets.UTF_8)

        // 3. Encrypt.
        val blob = crypto.encrypt(json, passphrase)

        // 4. Upload.
        val fileName = "kaavalan-note-backup-${ts()}.json.enc"
        val id = driveApi.uploadToAppFolder(accessToken, fileName, blob)
        Log.i(
            TAG,
            "Backed up ${blob.size} bytes to Drive file '$fileName' (id=$id)",
        )
        return DriveRestApi.DriveFile(
            id = id,
            name = fileName,
            sizeBytes = blob.size.toLong(),
            createdTimeMs = System.currentTimeMillis(),
        )
    }

    /**
     * List the existing Baton backups in the user's
     * `appDataFolder`, newest first. Same shape as
     * [com.kaavalan.note.data.export.BackupManager.listBackups].
     */
    suspend fun listBackups(): List<DriveRestApi.DriveFile> {
        val accessToken = oauth.getAccessToken()
            ?: throw DriveBackupException.NotSignedIn()
        return driveApi.listBackups(accessToken)
    }

    /**
     * Restore from a specific Drive backup by ID. The
     * downloaded bytes are decrypted with [passphrase]
     * and imported into the local DB via [PlainImporter].
     * Returns the [PlainImporter.ImportReport] so the
     * caller can show inserted/updated counts in a
     * snackbar.
     */
    suspend fun restore(
        fileId: String,
        passphrase: CharArray,
    ): PlainImporter.ImportReport {
        require(passphrase.isNotEmpty()) { "passphrase must not be empty" }
        val accessToken = oauth.getAccessToken()
            ?: throw DriveBackupException.NotSignedIn()
        val bytes = driveApi.downloadFile(accessToken, fileId)
        val json = try {
            crypto.decrypt(bytes, passphrase)
        } catch (e: Throwable) {
            throw DriveBackupException.WrongPassphrase(e)
        }
        // The crypto output is the JSON bytes; we
        // import via [PlainImporter] which parses the
        // JSON. We use a `tempFile` so the importer
        // gets a real [java.io.File] to read from.
        val tmp = kotlin.io.path.createTempFile(suffix = ".json").toFile()
        try {
            tmp.writeBytes(json)
            return plainImporter.importFromUri(android.net.Uri.fromFile(tmp))
                .getOrThrow()
        } finally {
            tmp.delete()
        }
    }

    /**
     * Delete a Drive backup by ID. Idempotent (a 404
     * is treated as success).
     */
    suspend fun deleteBackup(fileId: String) {
        val accessToken = oauth.getAccessToken()
            ?: throw DriveBackupException.NotSignedIn()
        driveApi.deleteFile(accessToken, fileId)
    }

    private fun ts(): String =
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())

    sealed class DriveBackupException(message: String) : RuntimeException(message) {
        class NotSignedIn : DriveBackupException("Not signed in to Google.")
        class WrongPassphrase(cause: Throwable) :
            DriveBackupException("The passphrase did not decrypt the backup. " +
                "(The bytes may be corrupted, or the passphrase is wrong.)")
    }

    private companion object {
        private const val TAG = "BatonDriveBackup"
    }
}
