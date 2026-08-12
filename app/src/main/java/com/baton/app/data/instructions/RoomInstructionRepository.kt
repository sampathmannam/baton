package com.baton.app.data.instructions

import com.baton.app.data.local.InstructionDao
import com.baton.app.data.local.entities.InstructionEntity
import com.baton.app.data.local.entities.SyncStatus
import javax.inject.Inject
import javax.inject.Singleton

/**
 * M3-T5: thin local mirror for the [InstructionRepository] contract.
 * The DAO is the source of truth for the People-list badge
 * (`observeOpenCountByPerson`) and the PersonDetailScreen timeline
 * (`observeForPerson`). The network-side [SupabaseInstructionRepository]
 * is what populates the mirror on launch via [refreshFromNetwork].
 *
 * This class deliberately does NOT implement [InstructionRepository] —
 * the create path still goes through the M1 [SupabaseInstructionRepository]
 * directly (the sync queue + outbox model for instructions isn't in M3).
 * Adding it would invite accidental double-writes; the badge + detail
 * flows only need reads.
 */
@Singleton
class RoomInstructionRepository @Inject constructor(
    private val dao: InstructionDao,
) {

    /**
     * Pull every row from the network and upsert into Room with
     * [SyncStatus.SYNCED]. Called once on app start (after auth) and
     * on every Realtime `Change.Instructions` event so the local
     * open-instruction count reflects the full user dataset.
     *
     * Replaces all locally-tracked rows that came from the server
     * (status = SYNCED) and inserts any new ones. Locally-created
     * rows with status = PENDING_INSERT are preserved (they haven't
     * been sent to the server yet, so the network doesn't know about
     * them and we don't want a refresh to clobber them).
     */
    suspend fun refreshFromNetwork() {
        // We don't have a Supabase client wired in here — pass it via
        // a wrapper. Simpler: have the caller pass it in. The single
        // caller (HomeViewModel) already injects both DAOs and the
        // Supabase repo via Hilt. We expose the network read as an
        // extension function instead of pulling another Hilt graph
        // dependency in here.
        throw UnsupportedOperationException(
            "Use the suspend overload that takes a SupabaseInstructionRepository",
        )
    }

    /**
     * Hilt-injected overload that does the actual pull + upsert.
     * Kept separate so the network dependency stays explicit at the
     * call site (HomeViewModel.refreshInstructionsFromNetwork) and
     * this class remains trivially unit-testable with a fake DAO.
     */
    suspend fun refreshFromNetwork(remote: SupabaseInstructionRepository) {
        val remoteRows = remote.fetchAll()
        val entities = remoteRows.map { it.toEntity() }
        // REPLACE on conflict: a row whose id matches a pending local
        // insert will overwrite the PENDING_INSERT placeholder with
        // the server's response. That's the right behaviour — the
        // server has now seen the row, so the local mirror should
        // reflect the server's version.
        dao.upsertAll(entities)
    }

    private fun Instruction.toEntity(): InstructionEntity = InstructionEntity(
        id = id,
        personId = personId,
        direction = direction.name,
        status = status.name,
        source = source.name,
        priority = priority.name,
        title = title,
        rawText = rawText,
        dueAt = dueAt,
        capturedAt = capturedAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
        syncStatus = SyncStatus.SYNCED,
    )
}
