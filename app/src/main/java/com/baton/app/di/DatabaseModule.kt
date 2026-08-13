package com.baton.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.baton.app.data.auth.SecurePreferences
import com.baton.app.data.local.AppDao
import com.baton.app.data.local.AppDatabase
import com.baton.app.data.local.CaptureDao
import com.baton.app.data.local.InstructionDao
import com.baton.app.data.local.InstructionTagDao
import com.baton.app.data.local.NudgeDraftDao
import com.baton.app.data.local.PersonDao
import com.baton.app.data.local.SyncConflictDao
import com.baton.app.data.local.SyncQueueDao
import com.baton.app.data.local.TagDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Singleton

/**
 * Hilt providers for the local Room database. M2-T6 + M3-T1.
 *
 * **Encryption (M3-T1).** The DB is now opened through SQLCipher
 * with a 32-byte passphrase generated on first launch by
 * [SecurePreferences.databasePassphrase] and persisted in
 * EncryptedSharedPreferences. The on-disk `baton.db` file is
 * unreadable without that passphrase. The M2 unencrypted DB is
 * wiped on the M2 -> M3 transition (handled by
 * [com.baton.app.data.local.AppInitializer] on first run).
 *
 * **Sign-out (M3-T4).** When the user signs out,
 * [SecurePreferences.clearDatabasePassphrase] deletes the key. The
 * next DB read fails (SQLCipher: "file is not a database"); the
 * AppInitializer then deletes the file and opens a fresh DB. On
 * the next sign-in the user pulls from Supabase.
 *
 * **Migration.** The M2 -> M3 schema bump (no schema changes, just
 * `version = 3` to trigger the AppInitializer wipe) is intentional.
 * The old plain DB is destroyed; the new encrypted DB starts empty
 * and is filled from Supabase on the first
 * [com.baton.app.data.local.RoomPersonRepository.refreshFromNetwork]
 * call after sign-in.
 *
 * **v1.2.1 (BUG-DATA-009) PRAGMA foreign_keys = ON.** SQLite's
 * `PRAGMA foreign_keys` is OFF by default for every connection
 * (it's a runtime, per-connection setting, not a schema flag).
 * Without it, `ON DELETE CASCADE` is silently ignored and a deleted
 * parent leaves orphan rows. We enable it on every `onOpen` via a
 * [RoomDatabase.Callback]. This is a SQLite-level best practice
 * that Room doesn't enable on its own; without it, the v1.1 cascade
 * delete on `instruction_tags` (when an instruction is deleted)
 * doesn't fire.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        securePreferences: SecurePreferences,
    ): AppDatabase {
        val passphrase = securePreferences.databasePassphrase()
        val factory = SupportOpenHelperFactory(passphrase)
        // Zero the passphrase bytes after handing them to SQLCipher.
        // SQLCipher has already copied what it needs internally.
        passphrase.fill(0)
        return Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .openHelperFactory(factory)
            .addCallback(foreignKeysCallback())
            // M2-T6: destructive migration is fine because the local
            // cache is reconstructible from Supabase on the next
            // refresh. Replace with real Migrations once the schema
            // stabilises beyond M3.
            .fallbackToDestructiveMigration()
            .build()
    }

    /**
     * v1.2.1 (BUG-DATA-009): `PRAGMA foreign_keys = ON` on every
     * connection open. Exposed as a separate factory so the unit
     * test can verify the callback without standing up the full
     * SQLCipher-backed [AppDatabase] (which fails in Robolectric
     * because the native lib isn't packaged there).
     */
    fun foreignKeysCallback(): RoomDatabase.Callback = object : RoomDatabase.Callback() {
        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            db.execSQL("PRAGMA foreign_keys = ON")
        }
    }

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

    @Provides
    fun provideTagDao(db: AppDatabase): TagDao = db.tagDao()

    @Provides
    fun provideInstructionTagDao(db: AppDatabase): InstructionTagDao = db.instructionTagDao()

    @Provides
    fun provideAppDao(db: AppDatabase): AppDao = db.appDao()

    @Provides
    fun provideNudgeDraftDao(db: AppDatabase): NudgeDraftDao = db.nudgeDraftDao()
}
