package com.baton.app.features.capture

import android.content.Context
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * M2-T2 helper that wires the system camera. The launcher
 * pattern:
 *  1. Build a destination File in `cacheDir/captures/` and a
 *     FileProvider content:// URI from it.
 *  2. `TakePicture` launches the system camera with that URI.
 *  3. On success, the URI is read by ML Kit and OCR'd.
 *  4. On failure / cancel, the caller surfaces a friendly "no
 *     photo" state.
 */
object CameraLauncher {

    /** Authority must match AndroidManifest's provider. */
    const val AUTHORITY_SUFFIX: String = ".fileprovider"

    const val CAPTURE_SUBDIR: String = "captures"

    /**
     * Register the launcher in a Composable. Returns a pair of
     * the launcher (call [launch] to start the camera) and a
     * `pendingUri` (set after [launcher.launch] is called; the
     * callback receives `true` on success).
     *
     * The caller must hold this Composable's scope for the
     * launcher's lifetime (registerForActivityResult is
     * Composable-scoped).
     */
    fun register(
        activity: ComponentActivity,
        onResult: (success: Boolean, uri: Uri?) -> Unit,
    ): ActivityResultLauncher<Uri> = activity.registerForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success ->
        if (success) {
            onResult(true, null)  // URI was passed in via launch(); the caller saved it
        } else {
            onResult(false, null)
        }
    }

    /**
     * Create a fresh destination file in the app's cache and
     * return a FileProvider URI for it. Pass that URI to
     * [ActivityResultLauncher.launch]. After the camera returns
     * `success=true`, the file holds a JPEG.
     */
    fun newCaptureUri(context: Context): Uri {
        val dir = File(context.cacheDir, CAPTURE_SUBDIR).apply { mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "capture_$timestamp.jpg")
        // Pre-create the file so the FileProvider has something to
        // back the URI with. The camera will overwrite the bytes.
        file.createNewFile()
        return FileProvider.getUriForFile(
            context,
            context.packageName + AUTHORITY_SUFFIX,
            file,
        )
    }
}
