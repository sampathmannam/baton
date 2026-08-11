package com.baton.app.features.capture

import android.content.Intent

/**
 * M1-T7 helper that extracts the shared text from a SEND intent.
 * The user picks Baton from another app's share sheet; the system
 * dispatches an `Intent(ACTION_SEND)` with `type=text/plain` and
 * `EXTRA_TEXT=<text>` to our `ShareReceiverActivity`. This object
 * is the single point that pulls the text out and validates the
 * shape of the intent before we forward it to MainActivity.
 *
 * The receiver activity is a no-UI forwarder; the actual capture
 * sheet lives in MainActivity. We pre-fill the text on the VM and
 * open the sheet on resume.
 *
 * M1 only handles `text/plain` (no images / OCR yet — that's M2).
 */
object ShareIntake {

    /**
     * The MIME type this intake accepts. M2 will add an image MIME
     * type alongside the OCR pipeline.
     */
    const val ACCEPTED_MIME_TYPE: String = "text/plain"

    /**
     * The intent action we look for.
     */
    const val ACTION: String = Intent.ACTION_SEND

    /**
     * Returns the shared text if [intent] is a valid Baton share
     * intent (ACTION_SEND + text/plain + non-blank EXTRA_TEXT), or
     * `null` otherwise. A `null` result tells the caller "ignore
     * this intent, it's not for us" — the receiver activity then
     * simply forwards to MainActivity without a pre-filled note.
     */
    fun extractText(intent: Intent?): String? {
        if (intent == null) return null
        if (intent.action != ACTION) return null
        val type = intent.type
        // We require the type to be set explicitly. The Android share
        // sheet always sets the type; an Intent with action=SEND but
        // no MIME is either malformed or a non-share intent.
        if (type == null || !type.equals(ACCEPTED_MIME_TYPE, ignoreCase = true)) return null
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        if (text.isNullOrBlank()) return null
        return text
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

    /** Extras key MainActivity reads to know what shared text to pre-fill. */
    const val EXTRA_SHARED_TEXT: String = "com.baton.app.features.capture.EXTRA_SHARED_TEXT"
}
