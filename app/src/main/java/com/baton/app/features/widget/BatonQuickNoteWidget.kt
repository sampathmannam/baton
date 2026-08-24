package com.baton.app.features.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.glance.LocalContext
import com.baton.app.R

/**
 * v1.9.10: the **Quick Note** home-screen widget.
 *
 * **What this widget is for.** The user wants to capture a note
 * without opening the app — per the v1.9.10 user request: "i also
 * want to create a widget for quick notes instead of opening the
 * app, widget on my phone screen, so carefully design what all
 * are required items in widget". The existing
 * [com.baton.app.features.capture.BatonCaptureWidget] opens the
 * main app to the capture sheet; that requires 3+ taps (open
 * app, wait for Home, tap the note bar, wait for the sheet) before
 * the user can type. This widget collapses that to **2 taps + typing**:
 *
 *   1. Tap the widget → [QuickNoteActivity] launches
 *      (fullscreen Compose, text field already focused, keyboard
 *      already raised)
 *   2. Type the note
 *   3. Tap the "Save" button (or the IME's Done key) → the
 *      capture is written to the local SQLCipher DB and the
 *      activity finishes
 *   4. The home screen is back where the user left it.
 *
 * **What this widget is NOT for.** It is not a "see my recent
 * notes" widget. Recent-notes surfacing (a list of the last 3-5
 * captures with their titles) was a candidate scope per the
 * design discussion in the v1.9.10 plan runbook
 * (`.sdd/deep-research/.../final_turn_001.md` §3.1 and §6), but
 * the v1.9.10 first pass ships the capture-only widget. v1.9.11
 * can add a 4x4 size with the recent-notes list (already
 * shaped by the same Glance composable that powers
 * [BatonTodayWidget]). Reasons to ship small first:
 *
 *  - **Privacy on the lock screen.** Showing note titles on the
 *    lock screen requires Android 12+'s
 *    `widgetFeatures=lockScreenOnlyRedacted` flag (Android
 *    automatically redacts the widget but the title text is the
 *    one thing an attacker would screenshot). The capture-only
 *    widget has no text to redact; the lock-screen surface is
 *    just the button. This dodges the BEAU-NEW-01-class risk
 *    entirely.
 *  - **AppWidget RemoteViews limitations.** AppWidgets do not
 *    support an inline EditText; the only way to capture
 *    text is via a config activity. We use [QuickNoteActivity]
 *    as that config. Adding recent-notes later requires a
 *    `ListView` (or Glance `LazyColumn`) which is fine, but
 *    it does need a state-binding workmanager update path
 *    (the existing [BatonTodayWidget] is a good template).
 *  - **The user's first ask is capture, not browse.** The
 *    existing [BatonTodayWidget] and [BatonDecayWidget]
 *    already cover "browse" needs.
 *
 * **Visual design.** A single rounded card (cornerRadius 16dp,
 * `primaryContainer` background, `onPrimaryContainer` text —
 * the same palette as the other widgets so the home screen
 * looks consistent). The card has a row of [icon] + ["Quick
 * note" label]. No red, no overdue wording — the M3 colour
 * tokens are warm by default; we leave them that way.
 *
 * **Privacy defaults.**
 *  - The widget never reads or displays note content.
 *  - The widget never logs user actions to logcat.
 *  - On Android 12+, the system auto-redacts widgets on the
 *    lock screen. The metadata XML adds
 *    `widgetFeatures=lockScreenOnlyRedacted` explicitly so a
 *    future size with a label follows the same policy.
 *
 * **State.** Stateless for v1.9.10. The widget re-renders on
 * every `update` event (system at `updatePeriodMillis` intervals
 * or explicit refresh from [QuickNoteActivity.save]). There is
 * no per-widget state object because the user-visible content
 * does not depend on any captured value. v1.9.11 will add a
 * "Last saved: 2 min ago" subtitle that does need state.
 */
class BatonQuickNoteWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                QuickNoteWidgetBody()
            }
        }
    }

    @Composable
    private fun QuickNoteWidgetBody() {
        // The click target is the whole card. actionStartActivity
        // takes a reified class parameter; the KSP compiler
        // resolves it to a class literal. The activity is
        // declared in AndroidManifest with `exported="false"`
        // and the standard M3 launch theme so it lands
        // fullscreen with no animation jank.
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.primaryContainer)
                .cornerRadius(16.dp)
                .padding(16.dp)
                .clickable(actionStartActivity<QuickNoteActivity>()),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = GlanceModifier.fillMaxWidth(),
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_quick_note_widget),
                    contentDescription = stringResource(
                        R.string.quick_note_widget_capture_desc,
                    ),
                )
                Spacer(GlanceModifier.width(8.dp))
                Text(
                    text = stringResource(R.string.quick_note_widget_label),
                    style = TextStyle(
                        color = GlanceTheme.colors.onPrimaryContainer,
                    ),
                )
            }
        }
    }

    @Composable
    private fun stringResource(resId: Int): String {
        val ctx = LocalContext.current
        return ctx.getString(resId)
    }
}

/**
 * Manifest-declared receiver. Standard Glance pattern: extends
 * [GlanceAppWidgetReceiver] and exposes the singleton widget.
 */
class BatonQuickNoteWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BatonQuickNoteWidget()
}
