package com.baton.app.di

import com.baton.app.data.auth.AuthRepository
import com.baton.app.data.captures.CaptureRepository
import com.baton.app.data.captures.SupabaseCaptureRepository
import com.baton.app.data.instructions.InstructionRepository
import com.baton.app.data.instructions.SupabaseInstructionRepository
import com.baton.app.data.person.PersonRepository
import com.baton.app.data.person.SupabasePersonRepository
import com.baton.app.data.supabase.BatonSupabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

/**
 * App-wide Hilt module.
 *
 * **v1.9.10 (Obs-1 fix):** the four Supabase repositories
 * ([SupabasePersonRepository], [SupabaseInstructionRepository],
 * [SupabaseCaptureRepository], [AuthRepository]) all take the
 * shared [BatonSupabase] singleton now — see
 * `data/supabase/SupabaseModule.kt` for the wrapper that hides
 * the KMP-AAR type from the Hilt KSP processor. The pre-v1.9.10
 * design built one `SupabaseClient` per repository (4 parallel
 * Realtime WebSockets at cold start, 4 retry loops on network
 * failure); the new design is one client, four consumers.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSupabasePersonRepository(batonSupabase: BatonSupabase): SupabasePersonRepository =
        SupabasePersonRepository(batonSupabase)

    @Provides
    @Singleton
    fun provideSupabaseCaptureRepository(batonSupabase: BatonSupabase): SupabaseCaptureRepository =
        SupabaseCaptureRepository(batonSupabase)

    @Provides
    @Singleton
    fun provideSupabaseInstructionRepository(batonSupabase: BatonSupabase): SupabaseInstructionRepository =
        SupabaseInstructionRepository(batonSupabase)

    @Provides
    @Singleton
    fun provideAuthRepository(batonSupabase: BatonSupabase): AuthRepository =
        AuthRepository(batonSupabase)

    @Provides
    @Singleton
    fun providePersonRepository(
        impl: com.baton.app.data.local.RoomPersonRepository,
    ): PersonRepository = impl

    @Provides
    @Singleton
    fun provideCaptureRepository(impl: com.baton.app.data.captures.RoomCaptureRepository): CaptureRepository = impl

    @Provides
    @Singleton
    fun provideInstructionRepository(
        // v1.5.1 (VAULT-005): vault mode binds the production
        // InstructionRepository to the local Room impl, not the
        // Supabase one. Every capture-and-save now lands in the
        // local SQLCipher DB. The SupabaseInstructionRepository
        // is still in the graph (as a dep of SyncEngine and the
        // optional refreshFromNetwork path) so a future Settings
        // toggle can flip this back to the cloud repo.
        impl: com.baton.app.data.instructions.RoomInstructionRepository,
    ): InstructionRepository = impl

    /** M1-T3: OkHttp is used by the Supabase HTTP client and
     *  (v1.6.0/v1.6.0.1) by the LLM model download. v1.6.1
     *  drops the LLM, but OkHttp stays for Supabase + vault
     *  import. */
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient()

    /**
     * v1.8.0 (PROD-READINESS-P2-#4): the audit-chain
     * signing key. v1.8.0 binds a fixed device-scoped
     * UUID ("anonymous-device-v1"); a pilot with a real
     * auth provider overrides this to return the
     * user's JWT `sub` claim so events are signed by
     * the user, not the device. The v1.8.0 trade-off is
     * "every device's chain is self-contained" which
     * is correct for the local-only build (the chain
     * never leaves the device) and acceptable for
     * the pilot scope (each user = each device).
     */
    @Provides
    @Singleton
    fun provideSigningKeyProvider(): com.baton.app.data.audit.SigningKeyProvider =
        com.baton.app.data.audit.SigningKeyProvider { "anonymous-device-v1" }
}

