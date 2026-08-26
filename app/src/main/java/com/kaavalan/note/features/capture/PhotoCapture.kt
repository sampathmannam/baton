package com.kaavalan.note.features.capture

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * M2-T2 photo capture. The user takes a picture (or picks one
 * from the gallery, or shares one from another app); the URI
 * is fed to [recognize] which runs ML Kit Text Recognition v2
 * (Latin script) and returns the recognised text. The string
 * then flows into the existing M1 extractor pipeline.
 *
 * ML Kit runs entirely on-device. The first call lazily
 * initialises the recognizer; subsequent calls reuse the same
 * instance. The model lives in the APK as part of the
 * `com.google.mlkit:text-recognition` dependency — no extra
 * download needed.
 */
object PhotoCapture {

    // Lazy because the recognizer does a one-time native init
    // and we don't want to pay that cost at app start. The
    // recognizer is thread-safe; we share one across the app.
    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /**
     * Run OCR on the image at [uri]. Returns the concatenated
     * recognised text (blocks separated by newlines), or an
     * empty string if ML Kit couldn't read anything.
     *
     * Throws if [uri] is unreachable or the file is not a
     * recognised image format. Callers (the share receiver, the
     * camera sheet) handle the throw by forwarding an empty
     * pre-fill to the capture sheet.
     */
    suspend fun recognize(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val image = InputImage.fromFilePath(context, uri)
        val result = awaitTask(recognizer.process(image))
        result.text
    }

    /**
     * Bridge a Google Play Services [Task] into a suspending
     * Kotlin call. Avoids the `kotlinx-coroutines-play-services`
     * dependency for one call site.
     */
    private suspend fun <T> awaitTask(task: Task<T>): T =
        suspendCancellableCoroutine { cont ->
            // Note: Google's [Task] does not expose a parameterless
            // `cancel()`; the listeners below simply become no-ops
            // if the coroutine is cancelled before they fire. That's
            // acceptable for a one-shot OCR call.
            task.addOnSuccessListener { cont.resume(it) }
            task.addOnFailureListener { e -> cont.resumeWithException(e) }
        }
}
