package com.baton.app.features.capture

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.baton.app.MainActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * M1-T7 invisible share-target. The user picks Baton from another
 * app's share sheet; the system dispatches an ACTION_SEND intent
 * with `type=text/plain` to this activity (per the manifest
 * `<activity-alias>`). We:
 *
 *  1. Read `Intent.EXTRA_TEXT` via [ShareIntake.extractText].
 *  2. If the text is valid, build a forward intent targeting
 *     [MainActivity] with the text in `EXTRA_SHARED_TEXT`.
 *  3. Start MainActivity and `finish()` so we don't linger.
 *
 * If the intent is not a valid Baton share (wrong action, wrong
 * MIME type, blank text), we just open MainActivity without a
 * pre-filled note.
 *
 * M2 will add an image MIME type and the OCR pipeline.
 */
@AndroidEntryPoint
class ShareReceiverActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sharedText = ShareIntake.extractText(intent)
        val forward = if (sharedText != null) {
            ShareIntake.buildForwardIntent(sharedText)
        } else {
            // Fall through to MainActivity with no pre-fill. The
            // user may have triggered the share accidentally; we
            // still want Baton to open.
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        }
        forward.setClassName(this, MainActivity::class.java.name)
        startActivity(forward)
        finish()
    }
}
