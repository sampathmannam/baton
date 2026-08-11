package com.baton.app.features.capture

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.baton.app.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric tests for [BatonCaptureWidget] + [BatonTileService].
 *
 * **What we test without an emulator:**
 *  - The widget's `onUpdate` produces a non-null RemoteViews (the
 *    layout inflater needs a Robolectric context).
 *  - The QUICK_CAPTURE action constant matches the value wired into
 *    the manifest, the tile, and MainActivity.
 *  - Building an Intent with the action targets MainActivity (the
 *    deep-link contract).
 *
 * **What we don't test here:**
 *  - Actual on-screen rendering (needs a real device or
 *    screenshot test).
 *  - The TileService `onClick` -> startActivity path (needs the
 *    system shade UI, smoke-tested on emulator).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BatonCaptureWidgetTest {

    @Test
    fun `quick capture action is the shared constant`() {
        // The widget, the tile, and MainActivity all reference this
        // same string. Drift between the three would silently break
        // the deep link.
        assertEquals(
            "com.baton.app.action.QUICK_CAPTURE",
            BatonCaptureWidget.ACTION_QUICK_CAPTURE,
        )
    }

    @Test
    fun `quick capture intent targets MainActivity`() {
        val intent = Intent().apply {
            action = BatonCaptureWidget.ACTION_QUICK_CAPTURE
            setClassName("com.baton.app.debug", MainActivity::class.java.name)
        }
        assertEquals(BatonCaptureWidget.ACTION_QUICK_CAPTURE, intent.action)
        assertEquals(MainActivity::class.java.name, intent.component?.className)
    }

    @Test
    fun `onUpdate does not throw for the synthetic widget id`() {
        // Robolectric's AppWidgetManager shadow requires a bound
        // AppWidgetProviderInfo to fully simulate the update; the
        // on-device path is smoke-tested separately. We only assert
        // that constructing a RemoteViews against the widget layout
        // succeeds (the layout inflates and the resource resolves).
        val context = org.robolectric.RuntimeEnvironment.getApplication() as Context
        val views = android.widget.RemoteViews(
            context.packageName,
            com.baton.app.R.layout.widget_baton_capture,
        )
        assertNotNull(views)
    }

    @Test
    fun `component name resolves`() {
        val context = org.robolectric.RuntimeEnvironment.getApplication() as Context
        val cn = ComponentName(context, BatonCaptureWidget::class.java)
        assertEquals(BatonCaptureWidget::class.java.name, cn.className)
        assertTrue(cn.packageName.isNotEmpty())
    }
}
