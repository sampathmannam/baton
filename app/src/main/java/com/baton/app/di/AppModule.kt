package com.baton.app.di

import android.content.Context
import com.baton.app.data.auth.AuthRepository
import com.baton.app.data.captures.CaptureRepository
import com.baton.app.data.captures.SupabaseCaptureRepository
import com.baton.app.data.instructions.InstructionRepository
import com.baton.app.data.instructions.SupabaseInstructionRepository
import com.baton.app.data.person.PersonRepository
import com.baton.app.data.person.SupabasePersonRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import okhttp3.OkHttpClient
import javax.inject.Singleton

/**
 * App-wide Hilt module. The [SupabaseClient] is built inside each
 * consumer (not bound here) to keep Hilt's KSP processor from trying to
 * resolve a KMP AAR type at binding-analysis time. See
 * `data/supabase/SupabaseModule.kt` for the full rationale.
 *
 * **M2-T6:** the [PersonRepository] binding is the Room-backed
 * `RoomPersonRepository` (see `data/local/RoomPersonRepository.kt`).
 * The [SupabasePersonRepository] is a constructor dep of the Room
 * repo, not a Hilt binding. Any consumer that wants the
 * Supabase-only path (e.g. the sync queue drain) can inject
 * [SupabasePersonRepository] directly.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSupabasePersonRepository(httpClient: HttpClient): SupabasePersonRepository =
        SupabasePersonRepository(httpClient)

    @Provides
    @Singleton
    fun provideSupabaseCaptureRepository(httpClient: HttpClient): SupabaseCaptureRepository =
        SupabaseCaptureRepository(httpClient)

    @Provides
    @Singleton
    fun provideSupabaseInstructionRepository(httpClient: HttpClient): SupabaseInstructionRepository =
        SupabaseInstructionRepository(httpClient)

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
    fun provideInstructionRepository(impl: SupabaseInstructionRepository): InstructionRepository = impl

    @Provides
    @Singleton
    fun provideAuthRepository(
        httpClient: HttpClient,
        @ApplicationContext context: Context,
    ): AuthRepository = AuthRepository(httpClient, context)

    /** M1-T3: OkHttp is used by ModelManager to download the GGUF model. */
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient()
}

