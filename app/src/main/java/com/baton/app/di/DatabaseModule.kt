package com.baton.app.di

import android.content.Context
import androidx.room.Room
import com.baton.app.data.local.AppDatabase
import com.baton.app.data.local.CaptureDao
import com.baton.app.data.local.InstructionDao
import com.baton.app.data.local.PersonDao
import com.baton.app.data.local.SyncConflictDao
import com.baton.app.data.local.SyncQueueDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt providers for the local Room database. M2-T6.
 *
 * **Encryption:** the M2-T6 build opens a plain (unencrypted) Room
 * DB. The `sqlcipher-android` dep is already in the project
 * (`app/build.gradle.kts`) and the encryption story is staged for
 * the privacy audit after the pilot; the key derivation will live
 * in [com.baton.app.data.auth.SecurePreferences] once that lands.
 * For the pilot the local DB is wiped on logout (see
 * `SecurePreferences.clearOnLogout`).
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            // M2-T6: destructive migration is fine because the local
            // cache is reconstructible from Supabase on the next
            // refresh. Replace with real Migrations in M3 once the
            // schema stabilises.
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun providePersonDao(db: AppDatabase): PersonDao = db.personDao()

    @Provides
    fun provideInstructionDao(db: AppDatabase): InstructionDao = db.instructionDao()

    @Provides
    fun provideCaptureDao(db: AppDatabase): CaptureDao = db.captureDao()

    @Provides
    fun provideSyncQueueDao(db: AppDatabase): SyncQueueDao = db.syncQueueDao()

    @Provides
    fun provideSyncConflictDao(db: AppDatabase): SyncConflictDao = db.syncConflictDao()
}
