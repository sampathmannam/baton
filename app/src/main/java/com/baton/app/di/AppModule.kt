package com.baton.app.di

import com.baton.app.data.auth.AuthRepository
import com.baton.app.data.captures.CaptureRepository
import com.baton.app.data.captures.SupabaseCaptureRepository
import com.baton.app.data.person.PersonRepository
import com.baton.app.data.person.SupabasePersonRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import javax.inject.Singleton

/**
 * App-wide Hilt module. The [SupabaseClient] is built inside each
 * consumer (not bound here) to keep Hilt's KSP processor from trying to
 * resolve a KMP AAR type at binding-analysis time. See
 * `data/supabase/SupabaseModule.kt` for the full rationale.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun providePersonRepository(httpClient: HttpClient): PersonRepository =
        SupabasePersonRepository(httpClient)

    @Provides
    @Singleton
    fun provideCaptureRepository(httpClient: HttpClient): CaptureRepository =
        SupabaseCaptureRepository(httpClient)

    @Provides
    @Singleton
    fun provideAuthRepository(httpClient: HttpClient): AuthRepository =
        AuthRepository(httpClient)
}
