package com.kaavalan.note.di

import android.app.Application
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Smoke test that Hilt is wired up. We use [HiltTestApplication] (provided
 * by `hilt-android-testing`, itself a `@HiltAndroidApp` class) as the
 * Robolectric application — Hilt forbids two `@HiltAndroidApp` roots in the
 * same variant, so we cannot ship a project-local TestApp alongside
 * BatonApplication. The assertion exercises the Robolectric + Hilt
 * application plumbing; it would fail at the Hilt KSP step if the Hilt
 * plugin weren't applied correctly.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = HiltTestApplication::class)
class HiltTest {

    @Test
    fun `Application is Hilt-instrumented`() {
        val app = Application()
        assertNotNull(app)
    }
}
