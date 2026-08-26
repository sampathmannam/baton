package com.kaavalan.note.di

import com.kaavalan.note.data.update.UpdateChecker
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import javax.inject.Singleton

/**
 * v1.9.0 (PROD-READINESS-P3-P1-#3): the Hilt
 * binding for the in-app [UpdateChecker].
 * Singleton because the GitHub release list is
 * cacheable in-memory across the process
 * lifetime (a single user-session check is
 * enough).
 */
@Module
@InstallIn(SingletonComponent::class)
object UpdateModule {

    /**
     * v2.0.0 (drop Supabase): the GitHub Releases
     * check is the only remaining network call.
     * We use the OkHttp engine because the offline
     * cache already ships `ktor-client-okhttp` (it
     * was a Supabase transitive dep in v1.x). One
     * `HttpClient` per process — `UpdateChecker`
     * is the only consumer.
     */
    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient = HttpClient(OkHttp)

    @Provides
    @Singleton
    fun provideUpdateChecker(httpClient: HttpClient): UpdateChecker =
        UpdateChecker(httpClient = httpClient)
}
