package com.baton.app.features.capture

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.baton.app.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * M1-T7 / M2-T1 invisible share-target. The user picks Baton from
 * another app's share sheet; the system dispatches an
 * ACTION_SEND intent with `type=text/plain` or any image MIME to
 * this activity (per the manifest `<activity-alias>`). We:
 *
 *  1. Inspect the intent via [ShareIntake.inspect].
 *  2. For text: forward the text directly to MainActivity.
 *  3. For images: run [PhotoCapture.recognize] on the URI
 *     (M2-T1 hands off to the OCR stub, M2-T2 wires ML Kit
 *     Text Recognition v2), then forward the recognised text.
 *  4. For unknown / invalid intents: open MainActivity with no
 *     pre-fill.
 *
 * M2-T4 will add an audio share path if we choose to support it
 * (out of scope for v1).
 */
@AndroidEntryPoint
class ShareReceiverActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when (val payload = ShareIntake.inspect(intent)) {
            is ShareIntake.Result.Text -> {
                forwardText(payload.text)
            }
            is ShareIntake.Result.Image -> {
                // M2-T1: defer to the OCR. The OCR call is suspending
                // so we run it on a small scope; the activity's
                // lifecycle ends as soon as we forward.
                val uri = payload.uri
                val pending = CoroutineScope(Dispatchers.IO).launch {
                    val text = PhotoCapture.recognize(applicationContext, uri)
                    forwardText(text)
                }
                // If the OCR takes too long, we still want to land
                // somewhere. Fallback: forward MainActivity with the
                // placeholder so the user can re-capture.
                pending.invokeOnCompletion {
                    if (pending.isCancelled) forwardText("")
                }
            }
            null -> forwardText("")
        }
    }

    private fun forwardText(text: String) {
        val forward = ShareIntake.buildForwardIntent(sharedText = text)
        forward.setClassName(this, MainActivity::class.java.name)
        startActivity(forward)
        finish()
    }
}
