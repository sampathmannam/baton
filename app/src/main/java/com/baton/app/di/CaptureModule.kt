package com.baton.app.di

import com.baton.app.ai.extraction.Extractor
import com.baton.app.features.capture.CaptureProcessor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt bindings for the capture feature.
 *
 * M1-T4 binds the real [Extractor] (on-device LLM) as the
 * [CaptureProcessor]. For unit tests, use `@TestInstallIn` to replace
 * the binding with a fake.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class CaptureModule {

    @Binds
    @Singleton
    abstract fun bindCaptureProcessor(impl: Extractor): CaptureProcessor
}
