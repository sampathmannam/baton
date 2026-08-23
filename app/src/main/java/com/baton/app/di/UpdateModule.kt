package com.baton.app.di

import com.baton.app.data.update.UpdateChecker
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
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

    @Provides
    @Singleton
    fun provideUpdateChecker(httpClient: HttpClient): UpdateChecker =
        UpdateChecker(httpClient = httpClient)
}
