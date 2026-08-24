package com.baton.app.di

import com.baton.app.data.captures.CaptureRepository
import com.baton.app.data.instructions.InstructionRepository
import com.baton.app.data.person.PersonRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * App-wide Hilt module.
 *
 * **v2.0.0 (drop Supabase):** simplified. The previous v1.9.x
 * version had four @Provides for the Supabase repositories
 * (Person, Instruction, Capture, Auth). All four are gone
 * — the app is local-only. The Room repositories are the
 * canonical implementations. The OkHttp @Provides is gone
 * (no Supabase HTTP client, no LLM model download).
 *
 * The audit-chain signing key is still device-scoped
 * ("anonymous-device-v1") because v2.0.0 has no real auth
 * — the device itself is the principal.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

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
        impl: com.baton.app.data.instructions.RoomInstructionRepository,
    ): InstructionRepository = impl

    /**
     * v1.8.0 (PROD-READINESS-P2-#4): the audit-chain
     * signing key. v1.8.0 binds a fixed device-scoped
     * UUID ("anonymous-device-v1"). v2.0.0 has no real
     * auth (the app is local-only), so the device itself
     * is the principal — every device's chain is
     * self-contained, which is the correct contract for
     * a local-only build.
     */
    @Provides
    @Singleton
    fun provideSigningKeyProvider(): com.baton.app.data.audit.SigningKeyProvider =
        com.baton.app.data.audit.SigningKeyProvider { "anonymous-device-v1" }
}
