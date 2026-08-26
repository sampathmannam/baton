package com.kaavalan.note.data.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import java.io.File

/**
 * v1.9.0 (PROD-READINESS-P3-P1-#8): the
 * "back up to Google Drive" helper. The
 * v1.5.0+ build is local-only, so the
 * "Drive" part is the user picking a Drive
 * folder in the system file picker (which
 * already has Google Drive integration
 * out of the box). The app does NOT use
 * the Google Sign-In API or the Drive REST
 * API directly — that would require
 * authentication tokens, account
 * management, and a third-party SDK that
 * the privacy policy is careful to avoid.
 *
 * **The pattern.**
 *  1. [BackupManager.backup] writes a JSON
 *     snapshot to `cacheDir/backups/`.
 *  2. [exportBackup] launches the system
 *     `ACTION_CREATE_DOCUMENT` intent with
 *     a `text/json` MIME type. The user
 *     picks a folder (which can be Google
 *     Drive, Dropbox, local storage — the
 *     system file picker supports all of
 *     them).
 *  3. The system calls back with the
 *     chosen URI. [exportBackup.onResult]
 *     copies the cached JSON file to the
 *     URI via `ContentResolver.openOutputStream`.
 *
 * **Restore on a new device.**
 *  1. [importBackup] launches `ACTION_OPEN_DOCUMENT`
 *     with a `text/json` MIME type. The
 *     user picks the backup file.
 *  2. [importBackup.onResult] reads the
 *     JSON, validates the schema, applies
 *     to the local DB via
 *     [BackupManager.restore].
 *
 * **Privacy.** No Google Sign-In, no Drive
 * REST API, no third-party SDK. The
 * data flow is "app → system file picker
 * → Google Drive (or wherever the user
 * picks)". The Drive auth happens inside
 * the system file picker, which the
 * privacy policy covers.
 */
object DriveBackup {

    /**
     * Build the [Intent] that launches the
     * system "Save As" dialog with a JSON
     * filter. The system file picker will
     * show Google Drive, Dropbox, local
     * storage, etc. — whatever the user
     * has installed.
     *
     * The result URI is delivered to
     * [onCreateDocumentLauncher] which the
     * caller registers in their
     * `ComponentActivity`.
     */
    fun buildCreateDocumentIntent(
        suggestedFileName: String,
    ): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            // The suggested file name shows in
            // the system file picker; the
            // user can edit it before
            // confirming.
            putExtra(Intent.EXTRA_TITLE, suggestedFileName)
        }

    /**
     * Build the [Intent] that launches the
     * system "Open" dialog with a JSON
     * filter. The user picks a backup
     * file (which can be in Google Drive,
     * local storage, etc.).
     */
    fun buildOpenDocumentIntent(): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            // Multiple = false — the user
            // picks one file at a time.
        }

    /**
     * Write the cached backup file at
     * [sourceFile] to the user-chosen
     * [destinationUri]. Uses
     * `ContentResolver.openOutputStream` so
     * the destination can be a content://
     * URI (Google Drive's
     * `com.google.android.apps.docs.storage`
     * provider, a local file provider, or
     * a Dropbox provider).
     *
     * Returns the number of bytes written,
     * or throws if the write fails.
     */
    fun writeToUri(
        context: Context,
        sourceFile: File,
        destinationUri: Uri,
    ): Long {
        val output = context.contentResolver.openOutputStream(destinationUri, "wt")
            ?: throw java.io.IOException("Could not open output for $destinationUri")
        return output.use { out ->
            sourceFile.inputStream().use { input ->
                input.copyTo(out)
            }
        }
    }

    /**
     * Read the user-chosen backup file at
     * [sourceUri] into a temporary file
     * under `cacheDir/backups/restore_*.json`.
     * The caller passes the returned file
     * to [BackupManager.restore]. The
     * temporary file is deleted after
     * restore.
     *
     * Returns the temp file, or throws if
     * the read fails.
     */
    fun readFromUri(
        context: Context,
        sourceUri: Uri,
    ): File {
        val input = context.contentResolver.openInputStream(sourceUri)
            ?: throw java.io.IOException("Could not open input for $sourceUri")
        val timestamp = java.text.SimpleDateFormat(
            "yyyyMMdd-HHmmss",
            java.util.Locale.US,
        ).format(java.util.Date())
        val dir = File(context.cacheDir, "backups").apply { mkdirs() }
        val temp = File(dir, "restore_$timestamp.json")
        input.use { inp ->
            temp.outputStream().use { out ->
                inp.copyTo(out)
            }
        }
        return temp
    }
}
