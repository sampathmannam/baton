package com.baton.app.data.captures

/**
 * Read + write interface for the `captures` table. M1 only ships the
 * `create` and `markProcessed` paths. M2 adds `getById`, M3 replaces
 * this with a Room-backed Flow.
 */
interface CaptureRepository {
    /**
     * Insert a new capture row. The row is `processed=false` and
     * `user_id` is filled by the database default (`auth.uid()`,
     * migration 0002). Returns the inserted capture (with the
     * server-assigned id and createdAt).
     */
    suspend fun create(rawText: String, mode: CaptureMode): Capture

    /**
     * Mark a capture as `processed=true` after M1-T5 has turned it
     * into an `instructions` row. Idempotent.
     */
    suspend fun markProcessed(id: String)
}
