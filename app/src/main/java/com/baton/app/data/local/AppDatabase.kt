package com.baton.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.baton.app.data.local.entities.CaptureEntity
import com.baton.app.data.local.entities.InstructionEntity
import com.baton.app.data.local.entities.PersonEntity
import com.baton.app.data.local.entities.SyncConflictEntity
import com.baton.app.data.local.entities.SyncQueueEntity

/**
 * Baton local database. Mirrors the four Supabase tables the
 * M2-T6 read path needs (`persons`, `instructions`, `captures`)
 * plus the outbox (`sync_queue`) that the SyncEngine drains to
 * keep Supabase in lock-step, plus the `sync_conflicts` audit
 * table (M2-T8).
 *
 * **Versioning:** `version = 2` after M2-T8 added the
 * `sync_conflicts` table. Bump and add a Migration when the
 * schema changes. The `fallbackToDestructiveMigration` in
 * [com.baton.app.di.DatabaseModule] keeps the upgrade simple
 * because the local cache is reconstructible from Supabase on
 * the next refresh.
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
        SyncConflictEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun personDao(): PersonDao
    abstract fun instructionDao(): InstructionDao
    abstract fun captureDao(): CaptureDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun syncConflictDao(): SyncConflictDao

    companion object {
        const val NAME = "baton.db"
    }
}
