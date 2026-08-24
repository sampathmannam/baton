package com.kaavalan.note.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * v2.0.0: the application-scoped [CoroutineScope] qualifier
 * and provider. v1.x defined these in `SyncModule`; that
 * module was deleted in v2.0.0 (no cloud sync). The qualifier
 * is kept because [com.kaavalan.note.data.local.AppInitializer]
 * and [com.kaavalan.note.data.instructions.RoomInstructionRepository]
 * still inject the scope (the latter for the `enqueueUpdate`
 * path; in v2.0.0 the path is a no-op but the
 * `CoroutineScope` injection is still wired).
 *
 * The scope is `SupervisorJob + Dispatchers.Default`. v1.x
 * used `Dispatchers.IO`; v2.0.0 uses `Default` because the
 * remaining fire-and-forget work is Room writes (which
 * dispatch internally to their own pool). Switching to
 * `Default` reduces the chance of `Dispatchers.IO` queue
 * saturation in a local-only build that no longer drains
 * outbox rows.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object CoroutineModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
