package com.kaavalan.note.ui.privacy

import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * v2.0 T3-2 (recovery phrase): set [WindowManager.LayoutParams.FLAG_SECURE]
 * on the hosting Activity's window while this composable is
 * on-screen, and clear it when the composable leaves the
 * composition.
 *
 * **Why this matters.** The recovery phrase is the master
 * secret. The user is told (in the Onboarding copy) to write
 * it down on paper and "do not screenshot". A
 * `FLAG_SECURE`d window blocks:
 *  - screenshots (the system screenshot button / gesture
 *    produces a black frame)
 *  - screen recording (the recorder shows black)
 *  - the recents thumbnail (the recents UI shows black)
 *
 * FLAG_SECURE is a per-window setting; the DisposableEffect
 * ensures we add it on enter and clear it on exit so other
 * surfaces (Settings, Home) screenshot normally.
 *
 * **No-op in non-Activity contexts.** If the composable is
 * hosted in something other than an [Activity] (e.g. a
 * preview), the effect is a no-op.
 */
@Composable
fun FlagSecureEffect() {
    val context = LocalContext.current
    DisposableEffect(context) {
        val window = (context as? Activity)?.window
        if (window != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose {
            if (window != null) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }
}
