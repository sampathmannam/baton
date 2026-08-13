package com.baton.app.data.captures

import com.baton.app.BuildConfig
import com.baton.app.data.supabase.buildSupabaseClient
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.ktor.client.HttpClient
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Supabase-backed [CaptureRepository] for M1. Mirrors the M0
 * `SupabasePersonRepository` pattern: build a client, insert via
 * Postgrest with `select()` to get the row back, decode. RLS
 * restricts reads + writes to the owning user.
 *
 * ## Wire-level idempotency (BATON-WIRE-006, v1.3)
 *
 * The `create()` wire call is idempotent. Two changes make that true:
 *
 *  1. The client generates a UUID for the row's primary key (`id`) and
 *     includes it in the POST body. The server's `captures.id` column
 *     is the primary key, so a retry with the same id is a unique-key
 *     conflict — not a fresh insert.
 *
 *  2. The POST is sent as an `upsert` with `onConflict = "id"` and
 *     `ignoreDuplicates = true`. supabase-kt 3.1.1 only attaches
 *     `Prefer: resolution=ignore-duplicates` to the wire call when
 *     `upsert = true` (the `insert` builder's `prefer` list never
 *     includes the `resolution=` token). The header tells PostgREST
 *     to treat the call as idempotent on the `(table, id)` primary
 *     key: if a row with the same `id` already exists, the server
 *     returns the existing row instead of erroring or creating a
 *     duplicate.
 *
 * Together, these mean a network call that times out *after* the
 * server wrote the row can be safely retried — the client gets the
 * same `Capture` back, and the server doesn't grow a phantom row.
 *
 * ## Server-side migration (applied separately, documented for reference)
 *
 * The Kotlin side of the contract is the header + the body field. The
 * Postgres side is a `UNIQUE` constraint on `captures.id`, which is
 * already true because `id` is the primary key. No new column is
 * required. If a future re-key strategy needs a separate
 * `client_request_id` column (so the request id can differ from the
 * row id), the migration would be:
 *
 * ```sql
 * -- ALTER TABLE captures
 * --   ADD COLUMN client_request_id UUID UNIQUE;
 * ```
 *
 * For v1.3 we use the existing `id` directly — it is already a
 * client-generated UUID (see [insertCapture]).
 */
class SupabaseCaptureRepository(
    httpClient: HttpClient,
    url: String = BuildConfig.SUPABASE_URL,
    key: String = BuildConfig.SUPABASE_ANON_KEY,
    withAuth: Boolean = true,
) : CaptureRepository {

    private val client: SupabaseClient = buildSupabaseClient(
        url = url,
        key = key,
        httpClient = httpClient,
        withAuth = withAuth,
    )

    override suspend fun create(rawText: String, mode: CaptureMode): Capture {
        // v1.3: generate the UUID here so the wire call is
        // idempotent on retry. The `insertCapture` method is the
        // one that actually sets the header + includes the id in
        // the body. Callers that already have a stable id (the
        // future outbox pattern) can call `insertCapture` directly
        // with the outbox row's id and skip the random-UUID step.
        return insertCapture(
            id = UUID.randomUUID().toString(),
            rawText = rawText,
            mode = mode,
        )
    }

    /**
     * v1.3: internal entry point that takes a caller-supplied id.
     * Used by [create] (which generates the id) and by tests that
     * need to verify retry safety with a specific id.
     *
     * **BATON-WIRE-006 wire-level idempotency.** The call uses
     * `upsert` (not `insert`) because supabase-kt 3.1.1 only
     * attaches `Prefer: resolution=ignore-duplicates` when
     * `upsert = true` — the `insert` builder's `prefer` list never
     * includes the `resolution=` token. The `onConflict = "id"`
     * tells PostgREST that a "duplicate" is defined by the `id`
     * column (the primary key), and `ignoreDuplicates = true`
     * means a duplicate row is a no-op (the existing row is
     * returned, no error). Together with the client-generated UUID
     * in the body, a retry after a response loss is safe: the
     * server sees the same `id` and returns the existing row
     * instead of creating a duplicate.
     */
    internal suspend fun insertCapture(
        id: String,
        rawText: String,
        mode: CaptureMode,
    ): Capture {
        val inserted: CaptureRow = client.postgrest
            .from("captures")
            .upsert(
                listOf(CaptureInsert(id = id, rawText = rawText, mode = mode.toDbValue())),
            ) {
                select()
                // BATON-WIRE-006: conflict key is the primary key
                // (`id`); duplicates are ignored, not merged.
                onConflict = "id"
                ignoreDuplicates = true
            }
            .decodeSingle()
        return inserted.toDomain()
    }

    override suspend fun markProcessed(id: String) {
        // PATCH the row by id; RLS ensures only the owning user can
        // update, so a wrong id is just a no-op (PostgREST returns
        // an empty array, no exception).
        client.postgrest
            .from("captures")
            .update(mapOf("processed" to true)) {
                filter { eq("id", id) }
            }
    }
}

@Serializable
private data class CaptureInsert(
    // v1.3: client-generated UUID for wire-level idempotency
    // (BATON-WIRE-006). The server's `captures.id` is the primary
    // key; the `Prefer: resolution=ignore-duplicates` header on the
    // insert call makes a retry with the same id a safe no-op.
    val id: String,
    @SerialName("raw_text") val rawText: String,
    val mode: String,
)

@Serializable
private data class CaptureRow(
    val id: String,
    val mode: String,
    @SerialName("raw_text") val rawText: String? = null,
    @SerialName("audio_uri") val audioUri: String? = null,
    @SerialName("image_uri") val imageUri: String? = null,
    val processed: Boolean,
    @SerialName("created_at") val createdAt: String,
) {
    fun toDomain(): Capture = Capture(
        id = id,
        mode = CaptureMode.fromDbValue(mode),
        rawText = rawText,
        audioUri = audioUri,
        imageUri = imageUri,
        processed = processed,
        createdAt = createdAt,
    )
}
