package com.baton.app.data.captures

import com.baton.app.BuildConfig
import com.baton.app.data.supabase.buildSupabaseClient
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.ktor.client.HttpClient
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Supabase-backed [CaptureRepository] for M1. Mirrors the M0
 * `SupabasePersonRepository` pattern: build a client, insert via
 * Postgrest with `select()` to get the row back, decode. RLS
 * restricts reads + writes to the owning user.
 */
class SupabaseCaptureRepository(
    httpClient: HttpClient,
) : CaptureRepository {

    private val client: SupabaseClient = buildSupabaseClient(
        url = BuildConfig.SUPABASE_URL,
        key = BuildConfig.SUPABASE_ANON_KEY,
        httpClient = httpClient,
    )

    override suspend fun create(rawText: String, mode: CaptureMode): Capture {
        val inserted: CaptureRow = client.postgrest
            .from("captures")
            .insert(
                CaptureInsert(rawText = rawText, mode = mode.toDbValue()),
            ) {
                select()
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
