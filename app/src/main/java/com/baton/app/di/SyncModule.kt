package com.baton.app.di

import com.baton.app.BuildConfig
import com.baton.app.data.sync.RealtimeSync
import com.baton.app.data.supabase.buildSupabaseClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Hilt qualifier for the application-scoped coroutine scope used
 * by long-lived background work (Realtime subscriptions, sync
 * workers). The scope is a [SupervisorJob] so a failure in one
 * child doesn't cancel the rest.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/**
 * Wires the M2-T7 Realtime subscription. The subscription is
 * application-scoped (singleton) so it survives Home tab
 * navigation. The [CoroutineScope] is also a singleton — both
 * are created on first injection and live until the process dies.
 */
@Module
@InstallIn(SingletonComponent::class)
object SyncModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Provides
    @Singleton
    fun provideRealtimeClient(httpClient: HttpClient): SupabaseClient =
        buildSupabaseClient(
            url = BuildConfig.SUPABASE_URL,
            key = BuildConfig.SUPABASE_ANON_KEY,
            httpClient = httpClient,
        )

    @Provides
    @Singleton
    fun provideRealtimeSync(
        @ApplicationScope scope: CoroutineScope,
        realtimeClient: SupabaseClient,
    ): RealtimeSync = RealtimeSync(realtimeClient, scope).also { it.start() }
}
