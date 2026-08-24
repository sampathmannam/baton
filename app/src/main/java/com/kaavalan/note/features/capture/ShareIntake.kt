package com.kaavalan.note.features.capture

import android.content.Intent
import android.net.Uri
import android.os.Build

/**
 * M1-T7 / M2-T1 helper that extracts the shared payload from a SEND
 * intent. The user picks Kaavalan note from another app's share sheet; the
 * system dispatches an `Intent(ACTION_SEND)` to our
 * `ShareReceiverActivity`. This object pulls the payload out and
 * validates the shape before we forward to MainActivity.
 *
 * M1 handled text/plain only. M2-T1 adds image MIME types via ML
 * Kit OCR; the receiver hands the image URI to
 * [com.kaavalan.note.features.capture.PhotoCapture] for text
 * recognition, then forwards the recognised text to MainActivity.
 * M2-T4 wires voice via audio MIME if we ever choose to support
 * share-as-audio (out of scope for v1).
 *
 * The receiver activity is a no-UI forwarder; the actual capture
 * sheet lives in MainActivity. We pre-fill the text on the VM and
 * open the sheet on resume.
 */
object ShareIntake {

    /** The text MIME type. */
    const val TEXT_MIME_TYPE: String = "text/plain"

    /** The image MIME prefix. M2-T1: any image MIME type is accepted. */
    const val IMAGE_MIME_PREFIX: String = "image/"

    /** The intent action we look for. */
    const val ACTION: String = Intent.ACTION_SEND

    /**
     * Result of inspecting an inbound share intent. Either text
     * (the shared string is ready to drop into the capture sheet)
     * or an image URI (the receiver must OCR it before forwarding).
     */
    sealed class Result {
        data class Text(val text: String) : Result()
        data class Image(val uri: Uri) : Result()
    }

    /**
     * Inspect [intent] and return the share payload, or `null` if
     * the intent is not a valid Kaavalan note share.
     *
     *  - `ACTION_SEND` + `text/plain` + non-blank `EXTRA_TEXT`
     *    → `Result.Text(<text>)`
     *  - `ACTION_SEND` + any image MIME + non-null `EXTRA_STREAM`
     *    → `Result.Image(<uri>)`
     *  - anything else (wrong action, wrong MIME, missing extras)
     *    → `null`
     */
    fun inspect(intent: Intent?): Result? {
        if (intent == null) return null
        if (intent.action != ACTION) return null
        val type = intent.type ?: return null
        return when {
            type.equals(TEXT_MIME_TYPE, ignoreCase = true) -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (text.isNullOrBlank()) null else Result.Text(text)
            }
            type.startsWith(IMAGE_MIME_PREFIX, ignoreCase = true) -> {
                val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                if (uri == null) null else Result.Image(uri)
            }
            else -> null
        }
    }

    /**
     * Build the forward intent that hands the shared text to
     * MainActivity. The new intent has:
     *   - ACTION_MAIN (so it just opens the app, not a share picker)
     *   - FLAG_ACTIVITY_CLEAR_TOP (so we don't stack share activities)
     *   - FLAG_ACTIVITY_SINGLE_TOP (so we land in the existing task)
     *   - EXTRA_SHARED_TEXT (the text itself, picked up by MainActivity)
     */
    fun buildForwardIntent(sharedText: String): Intent = Intent(Intent.ACTION_MAIN).apply {
        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        putExtra(EXTRA_SHARED_TEXT, sharedText)
    }

    /**
     * Same as [buildForwardIntent] but carries an already-OCR'd text
     * extracted from a shared image. The receiver activity is
     * responsible for running the OCR (see [PhotoCapture]) before
     * calling this; the forward intent is identical in shape to the
     * text-only case.
     */
    fun buildForwardFromImage(ocrText: String): Intent =
        buildForwardIntent(sharedText = ocrText)

    /** Extras key MainActivity reads to know what shared text to pre-fill. */
    const val EXTRA_SHARED_TEXT: String = "com.kaavalan.note.features.capture.EXTRA_SHARED_TEXT"
}
