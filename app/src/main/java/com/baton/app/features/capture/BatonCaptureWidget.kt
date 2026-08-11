package com.baton.app.features.capture

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.baton.app.MainActivity
import com.baton.app.R

/**
 * Lock-screen / home-screen widget for Baton. A single mic button
 * that launches MainActivity with the [ACTION_QUICK_CAPTURE] deep
 * link, the same entry point the [BatonTileService] uses.
 *
 * **Layout note:** the widget is a single `ImageButton` over a
 * background drawable. The button's `setOnClickPendingIntent` carries
 * the launch intent. There is no remote-list / service binding yet —
 * the widget is the entry point, the data lives inside the activity.
 */
class BatonCaptureWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_baton_capture)
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                action = ACTION_QUICK_CAPTURE
            }
            val pending = PendingIntent.getActivity(
                context,
                /* requestCode = */ 0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_mic_button, pending)
            appWidgetManager.updateAppWidget(id, views)
        }
    }

    companion object {
        /**
         * Deep-link action for the quick-capture entry point. Must
         * match the string used by [BatonTileService] and consumed by
         * MainActivity. Kept here (not in a constants file) so the
         * tile, widget, and the activity are co-located.
         */
        const val ACTION_QUICK_CAPTURE = "com.baton.app.action.QUICK_CAPTURE"

        /** Manifest declaration. Referenced in [BatonCaptureWidget]. */
        val COMPONENT: Class<*>
            get() = BatonCaptureWidget::class.java

        /** Trigger an update from anywhere via [AppWidgetManager]. */
        fun requestUpdate(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, BatonCaptureWidget::class.java))
            if (ids.isNotEmpty()) {
                val intent = Intent(context, BatonCaptureWidget::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                context.sendBroadcast(intent)
            }
        }
    }
}
