package com.baton.app.data.instructions

import com.baton.app.BuildConfig
import com.baton.app.data.supabase.buildSupabaseClient
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.ktor.client.HttpClient
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Supabase-backed [InstructionRepository] for M1. Mirrors the M0
 * `SupabasePersonRepository` pattern: build a client, insert via
 * Postgrest with `select()` to get the row back, decode. RLS restricts
 * reads + writes to the owning user.
 *
 * The [url] and [key] parameters default to the production
 * [BuildConfig] values; tests pass in a [HttpClient] built on top of
 * an [io.ktor.client.engine.mock.MockEngine] and explicit URL/key
 * values so no real network is touched.
 */
class SupabaseInstructionRepository(
    httpClient: HttpClient,
    url: String = BuildConfig.SUPABASE_URL,
    key: String = BuildConfig.SUPABASE_ANON_KEY,
    withAuth: Boolean = true,
) : InstructionRepository {

    private val client: SupabaseClient = buildSupabaseClient(
        url = url,
        key = key,
        httpClient = httpClient,
        withAuth = withAuth,
    )

    override suspend fun create(
        personId: String?,
        source: Source,
        priority: Priority,
        title: String,
        rawText: String,
        dueAt: String?,
    ): Instruction {
        val inserted: InstructionRow = client.postgrest
            .from("instructions")
            .insert(
                InstructionInsert(
                    personId = personId,
                    direction = Direction.OUTGOING,
                    status = Status.OPEN,
                    source = source,
                    priority = priority,
                    title = title,
                    rawText = rawText,
                    dueAt = dueAt,
                    capturedAt = java.time.Instant.now().toString(),
                ),
            ) {
                select()
            }
            .decodeSingle()
        return inserted.toDomain()
    }

    /**
     * M3-T5: read all instructions visible to the calling user.
     * Used on app launch to populate the local Room mirror so the
     * People-list open-instruction badge reflects the user's full
     * dataset, not just the rows they created on this device. RLS
     * filters server-side to `auth.uid()`.
     *
     * v1.1: also filters out any `is_sensitive = true` rows. The
     * server should never have returned them (spec §13 — sensitive
     * rows stay local-only) but defensive filtering keeps the
     * local mirror clean.
     */
    override suspend fun fetchAll(): List<Instruction> {
        val rows: List<InstructionRow> = client.postgrest
            .from("instructions")
            .select()
            .decodeList()
        return rows
            .filter { !it.isSensitive }
            .map { it.toDomain() }
    }

    /**
     * v1.1: PATCH an instruction's status. Supports the
     * mark-done / mark-dropped / re-open transitions. The
     * completedAt and droppedReason fields are written in the
     * same PATCH so the server's audit trail matches the local
     * Room state.
     */
    override suspend fun update(
        id: String,
        status: Status,
        completedAt: String?,
        droppedReason: String?,
        isSensitive: Boolean,
    ): Instruction {
        val updated: InstructionRow = client.postgrest
            .from("instructions")
            .update(
                InstructionUpdate(
                    status = status,
                    completedAt = completedAt,
                    droppedReason = droppedReason,
                    isSensitive = isSensitive,
                ),
            ) {
                filter { eq("id", id) }
                select()
            }
            .decodeSingle()
        return updated.toDomain()
    }

    /**
     * v1.1: convenience — mark a row DONE on the server. Used by
     * the sync engine when draining a PENDING_UPDATE with status =
     * DONE. The wire side does NOT itself queue another outbox
     * entry; the caller is responsible for the post-update mirror.
     */
    override suspend fun markDone(id: String, completedAt: String) {
        client.postgrest
            .from("instructions")
            .update(
                mapOf(
                    "status" to Status.DONE.name,
                    "completed_at" to completedAt,
                    "updated_at" to completedAt,
                ),
            ) { filter { eq("id", id) } }
    }

    /**
     * v1.1: convenience — mark a row DROPPED on the server. The
     * `reason` (free-text) is written into the `dropped_reason`
     * column so the user can review why in a future conflict UI.
     */
    override suspend fun markDropped(id: String, reason: String?, at: String) {
        client.postgrest
            .from("instructions")
            .update(
                mapOf(
                    "status" to Status.DROPPED.name,
                    "dropped_reason" to reason,
                    "updated_at" to at,
                ),
            ) { filter { eq("id", id) } }
    }
}

@Serializable
internal data class InstructionInsert(
    @SerialName("person_id") val personId: String?,
    val direction: Direction,
    val status: Status,
    val source: Source,
    val priority: Priority,
    val title: String,
    @SerialName("raw_text") val rawText: String,
    @SerialName("due_at") val dueAt: String?,
    @SerialName("captured_at") val capturedAt: String,
)

@Serializable
internal data class InstructionUpdate(
    val status: Status,
    @SerialName("completed_at") val completedAt: String? = null,
    @SerialName("dropped_reason") val droppedReason: String? = null,
    @SerialName("is_sensitive") val isSensitive: Boolean = false,
)

@Serializable
internal data class InstructionRow(
    val id: String,
    @SerialName("person_id") val personId: String? = null,
    val direction: Direction,
    val status: Status,
    val source: Source,
    val priority: Priority,
    val title: String,
    @SerialName("raw_text") val rawText: String,
    @SerialName("due_at") val dueAt: String? = null,
    @SerialName("captured_at") val capturedAt: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("is_sensitive") val isSensitive: Boolean = false,
    @SerialName("completed_at") val completedAt: String? = null,
    @SerialName("dropped_reason") val droppedReason: String? = null,
) {
    fun toDomain(): Instruction = Instruction(
        id = id,
        personId = personId,
        direction = direction,
        status = status,
        source = source,
        priority = priority,
        title = title,
        rawText = rawText,
        dueAt = dueAt,
        capturedAt = capturedAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isSensitive = isSensitive,
        completedAt = completedAt,
        droppedReason = droppedReason,
    )
}
