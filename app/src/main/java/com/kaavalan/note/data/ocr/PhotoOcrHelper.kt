package com.kaavalan.note.data.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * v2.0 Tier 2 (§2.4): photo OCR via ML Kit's on-device Text
 * Recognition v2 (Latin script). The recognizer is created once
 * per process and reused; the model is ~12 MB and is auto-
 * downloaded by ML Kit on first use.
 *
 * **Why a one-shot suspend, not a Flow:** the OCR call is
 * async, takes a few seconds on first use (model download +
 * inference), and there is no partial result to stream. We
 * surface the final text via [recognizeFromUri]; callers store
 * the result on the capture's `ocrText` column.
 *
 * **Errors are non-fatal:** a recogniser failure (e.g. ML Kit
 * is unavailable on a stripped-down image) returns null. The
 * caller writes null into `ocrText` and the UI shows the
 * photo without the OCR block.
 */
@Singleton
class PhotoOcrHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val recognizer: TextRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun recognizeFromUri(uri: Uri): String? {
        val image = try {
            InputImage.fromFilePath(context, uri)
        } catch (e: Exception) {
            return null
        }
        return suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { result -> cont.resume(result.text.takeIf { it.isNotBlank() }) }
                .addOnFailureListener { e -> cont.resumeWithException(e) }
        }
    }
}
