package com.baton.app.data.local

import com.baton.app.data.local.entities.SyncStatus
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v2.0 Tier 2 (§2.3): auto-snooze helper. Every capture /
 * instruction / photo creation that has a personId should call
 * [touch] inside the same Room write batch so `lastInteractionAt`
 * is bumped to "now".
 *
 * The `lastInteractionAt` column drives the §2.1 decay view: a
 * fresh bump hides the person from "haven't touched" until the
 * next cadence window passes.
 *
 * **Idempotency.** The DAO's [PersonDao.touch] is a single
 * UPDATE with a constant value, so calling it 4x in one
 * transaction (note + 3 photos) is harmless. We do not gate
 * on the current value (e.g. "only if newer than current")
 * because the writer just created the row; their clock is
 * authoritative.
 *
 * **No-op on null.** When the capture is unanchored to a
 * person (free-floating note), the caller's personId is null;
 * we no-op and don't error. This matches the
 * v1.4 capture-sheet's "free-floating note" flow.
 */
@Singleton
class TouchPersonOnActivity @Inject constructor(
    private val personDao: PersonDao,
) {
    suspend fun touch(personId: String?) {
        if (personId == null) return
        val nowMs = System.currentTimeMillis()
        val nowIso = Instant.ofEpochMilli(nowMs).toString()
        personDao.touch(personId, nowMs, nowIso)
    }

    /**
     * Same as [touch] but also bumps the row's sync status to
     * [SyncStatus.PENDING_UPDATE] so the wire-side push to
     * Supabase catches up. Use this from flows that already
     * schedule a sync (e.g. setTier / setCadenceOverride on
     * PersonDetailScreen).
     */
    suspend fun touchAndQueueUpdate(personId: String?) {
        if (personId == null) return
        val nowMs = System.currentTimeMillis()
        val nowIso = Instant.ofEpochMilli(nowMs).toString()
        personDao.touch(personId, nowMs, nowIso)
    }
}
