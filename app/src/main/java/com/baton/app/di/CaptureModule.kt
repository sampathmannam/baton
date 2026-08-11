package com.baton.app.di

import com.baton.app.features.capture.CaptureProcessor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt bindings for the capture feature.
 *
 * The default [CaptureProcessor] is a no-op: it always returns `null`,
 * which the [com.baton.app.features.capture.CaptureViewModel] surfaces as
 * a "No instruction found" state. M1-T4 replaces this with the
 * on-device LLM binding.
 *
 * To swap the processor for tests, use Hilt's `@TestInstallIn` to
 * replace the [CaptureProcessor] with a fake that returns a
 * deterministic proposal.
 */
@Module
@InstallIn(SingletonComponent::class)
object CaptureModule {

    @Provides
    @Singleton
    fun provideCaptureProcessor(): CaptureProcessor = CaptureProcessor { _ -> null }
}
