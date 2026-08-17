package com.baton.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
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
 *  - v10 v1.4.2 sync_queue UNIQUE INDEX on (op, table, rowId)
 *         (DATA-FINDING-04: dedup the outbox so a double-tap of
 *         `markDone` doesn't enqueue two UPDATE rows for the
 *         same instruction)
 *  - v11 v2.0 T3-1 added `vaultMode` to `persons` and
 *         `instructions` (deniable vault). Default `'visible'`;
 *         existing rows are backfilled with the default so no
 *         data is hidden on upgrade.
 *
 * v8 -> v9 is the first non-destructive migration in the project:
 * `ALTER TABLE sync_queue ADD COLUMN nextAttemptAt INTEGER NOT NULL
 * DEFAULT 0`. PENDING outbox rows are preserved across the upgrade
 * (BUG-DATA-001 was the audit finding that every previous bump
 * nuked pending writes).
 *
 * v9 -> v10 (DATA-FINDING-04) is also non-destructive:
 * `CREATE UNIQUE INDEX ... ON sync_queue (op, table, rowId)`.
 * The migration dedupes any pre-existing duplicate rows first
 * (keeping the row with the highest `id`, i.e. the latest
 * enqueue) so the CREATE INDEX doesn't fail on legacy data.
 * If a user has duplicate `(op, table, rowId)` rows from a
 * double-tap before this upgrade, the drain would have done the
 * right thing anyway (re-PATCH is idempotent for `is_sensitive`,
 * and the instruction status PATCH is the latest-write-wins
 * because the server side just overwrites). Collapsing to a
 * single row on upgrade is the right cleanup.
 *
 * v10 -> v11 (T3-1) is also non-destructive:
 * `ALTER TABLE persons ADD COLUMN vaultMode TEXT NOT NULL DEFAULT
 * 'visible'` and the matching ALTER on `instructions` plus two
 * CREATE INDEX statements. The DEFAULT backfill means existing
 * rows are NOT silently hidden on upgrade — the user opts in
 * by switching the global vault mode in Settings.
 *
 * v3 -> v4 -> v5 -> v6 -> v7 -> v8 are all `fallbackToDestructiveMigration`
 * transitions - the local cache is reconstructible from Supabase on
 * the next refresh, and there were no PENDING writes to lose
 * (M2-T6 + M3-T1 wipe before the outbox was in production use).
 *
 * **Encryption:** the database is opened via SQLCipher (see
 * [com.baton.app.di.DatabaseModule]); the key is held in
 * `EncryptedSharedPreferences`. The on-disk file is unreadable
 * without it - important for police data on a personal device.
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
    version = 11,
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

        /**
         * v1.4.2 (DATA-FINDING-04): add the UNIQUE INDEX on
         * `(op, table, rowId)` that [SyncQueueEntity] declares
         * and that [SyncQueueDao.enqueue] relies on for
         * dedup-on-conflict. The index column order matches the
         * @Entity declaration so Room's post-migration schema
         * validation accepts it.
         *
         * Pre-step: collapse any pre-existing duplicate rows
         * (one per `(op, table, rowId)` group, keeping the
         * latest by `id`) so the CREATE INDEX doesn't fail on
         * legacy data. The non-aggregating DELETE + correlated
         * subquery form is intentional - `DELETE ... WHERE id
         * NOT IN (SELECT MAX(id) ...)` does the dedupe in a
         * single statement.
         *
         * Production wiring: `DatabaseModule.provideDatabase`
         * should add this to its `addMigrations(...)` call,
         * matching the v8 -> v9 pattern. The migration is
         * exposed here as a public companion constant for that
         * one-line wiring change.
         */
        val MIGRATION_9_10: Migration = object : Migration(9, 10) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Dedupe any pre-existing duplicate outbox rows
                // before adding the unique constraint. Keeps the
                // row with the highest `id` per
                // (op, table, rowId) - i.e. the latest enqueue.
                db.execSQL(
                    "DELETE FROM sync_queue WHERE id NOT IN (" +
                        "SELECT MAX(id) FROM sync_queue " +
                        "GROUP BY `op`, `table`, rowId" +
                        ")",
                )
                // Add the unique index. `IF NOT EXISTS` so a
                // partially-completed prior run is idempotent.
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "`index_sync_queue_op_table_rowId` " +
                        "ON sync_queue (`op`, `table`, rowId)",
                )
            }
        }

        /**
         * v2.0 (Tier 3, feature 3.1) - deniable vault. Adds
         * the `vaultMode` column to `persons` and `instructions`.
         * Default `'visible'` so existing rows are NOT
         * silently hidden on upgrade - the user opts in by
         * switching the global vault mode in Settings.
         *
         * The `persons_vaultMode` and `instructions_vaultMode`
         * indices match the `@Index` declarations on the
         * entity so Room's post-migration schema validation
         * accepts them.
         */
        val MIGRATION_10_11: Migration = object : Migration(10, 11) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE persons ADD COLUMN vaultMode TEXT NOT NULL DEFAULT 'visible'",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_persons_vaultMode` ON persons(vaultMode)",
                )
                db.execSQL(
                    "ALTER TABLE instructions ADD COLUMN vaultMode TEXT NOT NULL DEFAULT 'visible'",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_instructions_vaultMode` ON instructions(vaultMode)",
                )
            }
        }
    }
}
