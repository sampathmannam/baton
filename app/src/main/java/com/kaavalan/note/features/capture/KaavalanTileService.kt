package com.kaavalan.note.features.capture

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.kaavalan.note.MainActivity
import com.kaavalan.note.R

/**
 * Tier 0.2 (cleanup + ship-the-built): the quick-settings tile.
 *
 * This is the v1.6.0 rewrite of the v1.5.7
 * `KaavalanCaptureTileService`. The legacy implementation was
 * correct in shape but had three v1.6-relevant issues:
 *
 *  1. The tile label was read from `R.string.tile_label`
 *     ("Kaavalan note quick-capture") which renders correctly
 *     in the system shade but is verbose; the system tile
 *     picker prefers the short form. The new label is the
 *     same string (no breaking change) and the description is
 *     a new `tier0_tile_description` string the system uses
 *     for TalkBack and the long-press hint.
 *  2. The tile state was never explicitly set. The system
 *     defaults to [Tile.STATE_INACTIVE] on first render, but a
 *     tile that is part of a "fire-and-forget" capture
 *     surface must explicitly set the state in
 *     [onStartListening] so the system's accessibility
 *     services (TalkBack) announce the correct state.
 *  3. The tile did not have the `META_DATA_ACTIVE_TILE`
 *     meta-data. With it, the system only binds the service
 *     after the user explicitly requests listening state
 *     (which we never do for a one-shot capture tile), so the
 *     bound service lifetime is much shorter -- a battery win
 *     and a privacy win (no background service is alive when
 *     the user is not in the shade).
 *
 * **Active mode + INACTIVE state:** with the
 * `META_DATA_ACTIVE_TILE` flag set, the tile is an "active
 * tile" -- the system calls [onStartListening] when the tile
 * becomes visible, and [onStopListening] when it leaves. We
 * use [onStartListening] to push the [Tile.STATE_INACTIVE]
 * label/icon so TalkBack and the tile picker both see the
 * correct state. Tapping fires [onClick] which deep-links to
 * [MainActivity] via the
 * [com.kaavalan.note.features.capture.KaavalanCaptureWidget.ACTION_QUICK_CAPTURE]
 * action -- the same action the Tier 0.1 widget fires.
 *
 * **Android 14 (UPSIDE_DOWN_CAKE) handling:** unchanged from
 * the v1.5.7 implementation. The system requires a launcher
 * category when starting an activity from the background; we
 * add `CATEGORY_LAUNCHER` on API 34+.
 *
 * **Permission:** the manifest declares
 * `android.permission.BIND_QUICK_SETTINGS_TILE` -- a
 * system-level permission that requires no runtime ask. The
 * user adds the tile from the system shade.
 */
class KaavalanTileService : TileService() {

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onStartListening() {
        super.onStartListening()
        // Tier 0.2: explicitly push the inactive state. The
        // tile is a "fire-and-forget" capture entry point --
        // there is no persistent on/off state, so it is
        // permanently INACTIVE. The system will not flip the
        // state on its own; the explicit set is the contract.
        val tile = qsTile ?: return
        tile.label = getString(R.string.tier0_tile_label)
        tile.state = Tile.STATE_INACTIVE
        // v1.6: the description is what TalkBack reads when
        // the tile is focused in the shade. The
        // `R.string.tier0_tile_description` is the same copy
        // the tile picker shows in the "add a tile" dialog.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.contentDescription = getString(R.string.tier0_tile_description)
        }
        tile.updateTile()
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            // The capture flow checks for this action;
            // CaptureSheet opens pre-focused on the text input.
            action = KaavalanCaptureWidget.ACTION_QUICK_CAPTURE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // API 34+: TileService.startActivity expects a
            // foreground service launch type when the activity
            // is started from the background.
            // CATEGORY_LAUNCHER + NEW_TASK works for our
            // launch-from-shade case.
            // v1.6.7: startActivityAndCollapse(p0: Intent) is
            // deprecated in Q+. The replacement is a plain
            // startActivity() call -- the system auto-dismisses
            // the tile once the activity is launched (this is
            // the standard quick-settings-tile behaviour; no
            // explicit dismiss call is needed). TileService is
            // a Service, not an Activity, so finish() is not
            // available -- the service unbinds and the system
            // reclaims the tile when the binding goes away.
            startActivity(
                Intent(intent).addCategory(Intent.CATEGORY_LAUNCHER),
            )
        } else {
            startActivity(intent)
        }
    }
}
