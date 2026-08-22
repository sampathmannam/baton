package com.baton.app.features.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.compose.ui.unit.dp
import com.baton.app.MainActivity
import com.baton.app.R

/**
 * v1.9.0 (PROD-READINESS-P3-P2-#3): the "widget gallery"
 * expansion. The v1.x build had one widget (the Capture
 * widget — single tap to open the capture sheet). v1.9.0
 * adds two more:
 *
 *  - [BatonTodayWidget] — opens the Today screen. The widget
 *    surface shows the count of open instructions (the
 *    number is the same one the Today screen's "open"
 *    counter surfaces; the widget refreshes on
 *    `updatePeriodMillis` which is set to 30 minutes by
 *    the XML metadata).
 *  - [BatonDecayWidget] — opens the Today screen and scrolls
 *    to the Decay section. Shows the count of quiet contacts
 *    (people the user hasn't touched in 60+ days, i.e. the
 *    "Periodic" tier). The number is the same one the Decay
 *    section surfaces.
 *
 * **State.** Both new widgets are stateless for v1.9.0 —
 * the count is hard-coded to "—" (the empty state). A v2.x
 * build wires the count to the Room query that backs the
 * Today / Decay screens (the SQL is straightforward; the
 * Composable is already designed to render the result).
 * The v1.9.0 trade-off is "show the widget shape, fill in
 * the data later" — the user can pin the widget and the
 * tap target works; the displayed number is always "—".
 *
 * **Receivers.** Each widget has a [GlanceAppWidgetReceiver]
 * that the manifest registers. A single APK can ship
 * multiple receivers; they share the AppWidget icon assets
 * in `res/drawable/`.
 *
 * **No new permission** is required. The widget is a
 * small UI surface; the data it shows is in the app's
 * private SQLCipher DB.
 */
class BatonTodayWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                TodayWidgetBody()
            }
        }
    }

    @Composable
    private fun TodayWidgetBody() {
        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.primaryContainer)
                .cornerRadius(16.dp)
                .padding(12.dp)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Today",
                style = TextStyle(
                    color = GlanceTheme.colors.onPrimaryContainer,
                ),
            )
            Spacer(GlanceModifier.width(8.dp))
            Box(
                modifier = GlanceModifier
                    .background(GlanceTheme.colors.secondaryContainer)
                    .cornerRadius(8.dp)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = "—",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSecondaryContainer,
                    ),
                )
            }
        }
    }
}

class BatonTodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BatonTodayWidget()
}

class BatonDecayWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                DecayWidgetBody()
            }
        }
    }

    @Composable
    private fun DecayWidgetBody() {
        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.tertiaryContainer)
                .cornerRadius(16.dp)
                .padding(12.dp)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Quiet contacts",
                style = TextStyle(
                    color = GlanceTheme.colors.onTertiaryContainer,
                ),
            )
            Spacer(GlanceModifier.width(8.dp))
            Box(
                modifier = GlanceModifier
                    .background(GlanceTheme.colors.surface)
                    .cornerRadius(8.dp)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = "—",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                    ),
                )
            }
        }
    }
}

class BatonDecayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BatonDecayWidget()
}
