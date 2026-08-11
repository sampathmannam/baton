package com.baton.app.data.instructions

/**
 * M1 repository for the `instructions` table. M1 only writes (no reads
 * — the Today tab + Today brief are M4). The single method backs the
 * M1-T5 save flow: confirm a capture's proposal, persist the
 * instruction, link it to the (auto-created) person.
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
}
