package com.baton.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.baton.app.data.local.entities.AppStateEntity
import com.baton.app.data.local.entities.CaptureEntity
import com.baton.app.data.local.entities.InstructionEntity
import com.baton.app.data.local.entities.InstructionTagCrossRef
import com.baton.app.data.local.entities.NudgeDraftEntity
import com.baton.app.data.local.entities.PersonEntity
import com.baton.app.data.local.entities.SyncConflictEntity
import com.baton.app.data.local.entities.SyncQueueEntity
import com.baton.app.data.local.entities.TagEntity

/**
 * Baton local database. Mirrors the six Supabase tables the
 * read paths need (`persons`, `instructions`, `captures`, `tags`,
 * `instruction_tags`) plus the outbox (`sync_queue`) that the
 * SyncEngine drains to keep Supabase in lock-step, plus the
 * `sync_conflicts` audit table.
 *
 * **Versioning:**
 *  - v1 M0 initial
 *  - v2 M2-T6 added sync_queue + sync_conflicts
 *  - v3 M3-T1 SQLCipher encryption (no schema change, just bump so
 *    the M2 plain DB is wiped on the M2 -> M3 transition)
 *  - v4 M3-T7 added tags + instruction_tags
 *  - v5 M4-T6 added app_state
 *  - v6 v1.0 nudge_drafts added (M5)
 *  - v7 v1.0 is_sensitive added on persons
 *  - v8 v1.1 lifecycle (completedAt, droppedReason) on instructions
 *  - v9 v1.2.2 sync_queue.nextAttemptAt (exponential backoff)
 *
 * v8 -> v9 is the first non-destructive migration in the project:
 * `ALTER TABLE sync_queue ADD COLUMN nextAttemptAt INTEGER NOT NULL
 * DEFAULT 0`. PENDING outbox rows are preserved across the upgrade
 * (BUG-DATA-001 was the audit finding that every previous bump
 * nuked pending writes).
 *
 * v3 -> v4 -> v5 -> v6 -> v7 -> v8 are all `fallbackToDestructiveMigration`
 * transitions — the local cache is reconstructible from Supabase on
 * the next refresh, and there were no PENDING writes to lose
 * (M2-T6 + M3-T1 wipe before the outbox was in production use).
 *
 * **Encryption:** the database is opened via SQLCipher (see
 * [com.baton.app.di.DatabaseModule]); the key is held in
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
        TagEntity::class,
        InstructionTagCrossRef::class,
        AppStateEntity::class,
        NudgeDraftEntity::class,
    ],
    version = 9,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun personDao(): PersonDao
    abstract fun instructionDao(): InstructionDao
    abstract fun captureDao(): CaptureDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun syncConflictDao(): SyncConflictDao
    abstract fun tagDao(): TagDao
    abstract fun instructionTagDao(): InstructionTagDao
    abstract fun appDao(): AppDao
    abstract fun nudgeDraftDao(): NudgeDraftDao

    companion object {
        const val NAME = "baton.db"
    }
}
