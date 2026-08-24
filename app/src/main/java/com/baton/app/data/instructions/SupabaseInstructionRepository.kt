package com.baton.app.data.instructions

import com.baton.app.data.supabase.BatonSupabase
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Supabase-backed [InstructionRepository] for M1. Mirrors the M0
 * `SupabasePersonRepository` pattern: build a client, insert via
 * Postgrest with `select()` to get the row back, decode. RLS restricts
 * reads + writes to the owning user.
 *
 * **v1.9.10 (Obs-1 fix):** the constructor now takes the shared
 * [BatonSupabase] singleton instead of building a fresh
 * `SupabaseClient` in a field initializer. Tests build their own
 * [BatonSupabase] from a MockEngine-backed [io.ktor.client.HttpClient]
 * so the wire format can still be exercised end-to-end without a
 * real network.
 */
class SupabaseInstructionRepository(
    batonSupabase: BatonSupabase,
) : InstructionRepository {

    private val client = batonSupabase.client

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
     * v1.4.2 (DATA-FINDING-02): mirror
     * [com.baton.app.data.person.SupabasePersonRepository.findById].
     * Used by [com.baton.app.data.local.SyncEngine]'s last-write-wins
     * conflict check on `OP_UPDATE`: it reads the server's `updated_at`
     * and compares against the local row's `updated_at` before applying
     * the PATCH. Returns `null` if the row no longer exists on the
     * server (RLS hides it, or it was deleted by another device); the
     * sync engine treats a `null` server row as "no conflict" and
     * proceeds with the PATCH (mirroring the persons path's policy).
     */
    suspend fun findById(id: String): Instruction? {
        val rows: List<InstructionRow> = client.postgrest
            .from("instructions")
            .select(
                columns = Columns.list(
                    "id", "person_id", "direction", "status", "source",
                    "priority", "title", "raw_text", "due_at", "captured_at",
                    "created_at", "updated_at", "is_sensitive",
                    "completed_at", "dropped_reason",
                ),
            ) {
                filter {
                    eq("id", id)
                }
                limit(1)
            }
            .decodeList()
        return rows.firstOrNull()?.toDomain()
    }

    /**
     * v1.1: PATCH an instruction's status. Supports the
     * mark-done / mark-dropped / re-open transitions. The
     * completedAt and droppedReason fields are written in the
     * same PATCH so the server's audit trail matches the local
     * Room state.
     *
     * **v1.1.1 root-cause fix:** the previous version used a
     * @Serializable [InstructionUpdate] data class. The
     * supabase-kt `update(...)` callsite serializes that class
     * and OMITS fields whose value is `null` (PostgREST only
     * updates the columns present in the body). This meant
     * re-opening a DROPPED instruction (status=OPEN,
     * droppedReason=null) left the server's `dropped_reason`
     * column holding the old text — a real audit-trail bug
     * because a later `refreshFromNetwork` would mirror that
     * stale value back into Room.
     *
     * We now build a typed `Map<String, JsonElement>` (using
     * `JsonNull` for null fields) and hand it to `update(...)`.
     * The typed map keeps supabase-kt's `KotlinXSerializer`
     * happy (no `Any`-type ambiguity), and `JsonNull` survives
     * the PATCH so the server sets the column to NULL.
     *
     * The [markDone] / [markDropped] convenience methods still
     * use a typed `mapOf<String, String>` because they never
     * need to send nulls.
     */
    override suspend fun update(
        id: String,
        status: Status,
        completedAt: String?,
        droppedReason: String?,
        isSensitive: Boolean,
    ): Instruction {
        // v1.2 BATON-WIRE-007 fix: do NOT include `updated_at` in
        // the PATCH body. The previous code stamped it with the
        // device's `Instant.now()` — which is wrong if the device
        // clock is skewed (NTP drift, manual timezone change, fresh
        // phone). The brief generator and the LWW conflict check
        // both compare `updated_at` lexicographically, so a
        // mis-stamped server value masked real concurrent edits.
        //
        // We leave the column out of the PATCH so the server keeps
        // its existing value. The Postgres schema has a
        // `BEFORE UPDATE` trigger (deployed via the v1.2 migration)
        // that sets `new.updated_at = now()` on every UPDATE. If
        // the trigger is missing, the column is left unchanged —
        // still safer than stamping a wrong value.
        val body: Map<String, kotlinx.serialization.json.JsonElement> = mapOf(
            "status" to JsonPrimitive(status.name),
            "completed_at" to (completedAt?.let { JsonPrimitive(it) } ?: JsonNull),
            "dropped_reason" to (droppedReason?.let { JsonPrimitive(it) } ?: JsonNull),
            "is_sensitive" to JsonPrimitive(isSensitive),
        )
        val updated: InstructionRow = client.postgrest
            .from("instructions")
            .update(body) {
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
     *
     * v1.2: do NOT include `updated_at` (see BATON-WIRE-007). The
     * server's `BEFORE UPDATE` trigger handles the timestamp.
     */
    override suspend fun markDone(id: String, completedAt: String) {
        client.postgrest
            .from("instructions")
            .update(
                mapOf(
                    "status" to Status.DONE.name,
                    "completed_at" to completedAt,
                ),
            ) { filter { eq("id", id) } }
    }

    /**
     * v1.1: convenience — mark a row DROPPED on the server. The
     * `reason` (free-text) is written into the `dropped_reason`
     * column so the user can review why in a future conflict UI.
     *
     * v1.2: do NOT include `updated_at` (see BATON-WIRE-007).
     */
    override suspend fun markDropped(id: String, reason: String?, at: String) {
        client.postgrest
            .from("instructions")
            .update(
                mapOf(
                    "status" to Status.DROPPED.name,
                    "dropped_reason" to reason,
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
