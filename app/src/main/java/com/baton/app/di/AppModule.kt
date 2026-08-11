package com.baton.app.di

import com.baton.app.data.person.PersonRepository
import com.baton.app.data.person.SupabasePersonRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import javax.inject.Singleton

/**
 * App-wide Hilt module. The [providePersonRepository] binding is the
 * Supabase-backed implementation — Task 6 wired it up. The [SupabaseClient]
 * is built inside the repository (not bound here) to keep Hilt's KSP
 * processor from trying to resolve a KMP AAR type at binding-analysis time.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun providePersonRepository(httpClient: HttpClient): PersonRepository =
        SupabasePersonRepository(httpClient)
}
