package com.baton.app.features.capture

import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi

/**
 * Quick-settings tile for Baton. The user adds the tile from the
 * system shade (`adb shell cmd statusbar add-tile ...` for testing).
 * Tapping the tile launches MainActivity with a deep-link that
 * focuses the capture flow.
 *
 * **Why MainActivity and not a direct VoiceCaptureService start:** the
 * M2-T3 (Whisper JNI) + M2-T4 (voice foreground service) pair lands in
 * a later session. Until then, the tile drops the user at the capture
 * sheet, which accepts text, photo, and (post-M2-T3+T4) voice. The
 * tile is wired and the deep-link works; only the dedicated voice
 * pipeline is deferred.
 *
 * Android 7.0+ (the project's minSdk = 26) is the floor for
 * TileService, so the class is unconditional.
 */
class BatonTileService : TileService() {

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onClick() {
        super.onClick()
        val intent = Intent(this, com.baton.app.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            // The capture flow checks for this action; CaptureSheet opens
            // pre-focused on the text input.
            action = "com.baton.app.action.QUICK_CAPTURE"
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // API 34+: TileService.startActivity expects a foreground
            // service launch type when the activity is started from the
            // background. CATEGORY_LAUNCHER + NEW_TASK works for our
            // launch-from-shade case.
            startActivityAndCollapse(
                Intent(intent).addCategory(Intent.CATEGORY_LAUNCHER),
            )
        } else {
            startActivityAndCollapse(intent)
        }
    }
}
