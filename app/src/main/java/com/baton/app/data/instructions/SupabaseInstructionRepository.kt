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
     */
    override suspend fun fetchAll(): List<Instruction> {
        val rows: List<InstructionRow> = client.postgrest
            .from("instructions")
            .select()
            .decodeList()
        return rows.map { it.toDomain() }
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
    )
}
