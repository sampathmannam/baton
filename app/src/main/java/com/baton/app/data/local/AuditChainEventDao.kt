package com.baton.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.baton.app.data.local.entities.AuditChainEventEntity
import kotlinx.coroutines.flow.Flow

/**
 * v1.8.0 (PROD-READINESS-P2-#4): the hash-chain DAO.
 * The chain grows in the auto-generated `id` order; the
 * latest row is `ORDER BY id DESC LIMIT 1` (genesis has
 * the lowest id, the newest write has the highest).
 */
@Dao
interface AuditChainEventDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(event: AuditChainEventEntity): Long

    @Query("SELECT * FROM audit_chain_events ORDER BY id ASC")
    suspend fun snapshot(): List<AuditChainEventEntity>

    @Query("SELECT * FROM audit_chain_events ORDER BY id DESC LIMIT 1")
    suspend fun latest(): AuditChainEventEntity?

    @Query("SELECT * FROM audit_chain_events WHERE tableName = :tableName AND rowId = :rowId ORDER BY id ASC")
    suspend fun eventsForRow(tableName: String, rowId: String): List<AuditChainEventEntity>

    @Query("SELECT * FROM audit_chain_events ORDER BY id DESC")
    fun observeAll(): Flow<List<AuditChainEventEntity>>

    @Query("SELECT COUNT(*) FROM audit_chain_events")
    suspend fun count(): Long

    /**
     * v1.8.0 (PROD-READINESS-P2-#5): REDACT (not delete)
     * every audit event whose `createdAtMs` is older
     * than the supplied [cutoffMs]. The JSON
     * `payload` is replaced with [redactedPayload]
     * (a fixed marker string) but the prevHash /
     * thisHash / signingKey are preserved so the
     * chain integrity remains verifiable. Returns
     * the number of rows redacted.
     */
    @Query("UPDATE audit_chain_events SET payload = :redactedPayload WHERE createdAtMs < :cutoffMs")
    suspend fun redactOlderThan(cutoffMs: Long, redactedPayload: String): Int
}
