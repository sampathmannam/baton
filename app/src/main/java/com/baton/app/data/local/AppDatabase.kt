package com.baton.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.baton.app.data.local.entities.CaptureEntity
import com.baton.app.data.local.entities.InstructionEntity
import com.baton.app.data.local.entities.PersonEntity
import com.baton.app.data.local.entities.SyncQueueEntity

/**
 * Baton local database. Mirrors the four Supabase tables the
 * M2-T6 read path needs (`persons`, `instructions`, `captures`)
 * plus the outbox (`sync_queue`) that the SyncEngine drains to
 * keep Supabase in lock-step.
 *
 * **Versioning:** `version = 1`. Bump and add a Migration when the
 * schema changes. For M2 the schema is small and we own the
 * installation; the first migration will be a `fallbackToDestructiveMigration`
 * until we have a real migration story.
 *
 * **Encryption:** the database is opened via SQLCipher (see
 * [com.baton.app.data.local.DatabaseModule]); the key is held in
 * `EncryptedSharedPreferences`. The on-disk file is unreadable
 * without it — important for police data on a personal device.
 */
@Database(
    entities = [
        PersonEntity::class,
        InstructionEntity::class,
        CaptureEntity::class,
        SyncQueueEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun personDao(): PersonDao
    abstract fun instructionDao(): InstructionDao
    abstract fun captureDao(): CaptureDao
    abstract fun syncQueueDao(): SyncQueueDao

    companion object {
        const val NAME = "baton.db"
    }
}
