package com.baton.app.data.instructions

/**
 * Repository for the `instructions` table. M1 only writes (no reads —
 * the Today tab + Today brief are M4). M3 added [fetchAll] for the
 * initial sync-on-launch used by the M3-T5 open-instruction badge.
 */
interface InstructionRepository {
    /**
     * Insert a new instruction. The implementation sets the direction
     * to `OUTGOING` and the status to `OPEN` — M1 only ever creates
     * instructions on the user's own behalf, in the OPEN state. The
     * [capturedAt] is set to `now()` on the server via the column's
     * `default now()` for `created_at` and an explicit client-side
     * value for `captured_at` (the column has no default).
     *
     * @return the inserted row (with server-generated id + timestamps).
     */
    suspend fun create(
        personId: String?,
        source: Source,
        priority: Priority,
        title: String,
        rawText: String,
        dueAt: String?,
    ): Instruction

    /**
     * M3-T5: pull every instruction row the calling user is allowed
     * to see (RLS scopes to `auth.uid()`). Used by the
     * `RoomInstructionRepository.refreshFromNetwork` initial sync so
     * the People-list badge reflects the user's real open-instruction
     * count, including rows captured on other devices.
     */
    suspend fun fetchAll(): List<Instruction>
}
