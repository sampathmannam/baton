package com.baton.app.data.appstate

import com.baton.app.data.local.AppDao
import com.baton.app.data.local.entities.AppStateEntity
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * M4-T6: cross-app state shared with MindAnchor (and any other
 * integrated app). The cloud table is `app_state` (spec §4.8) with
 * `(user_id, source, key)` unique. Baton writes its own keys
 * (`BATON.*`) and reads `MINDANCHOR.*` keys to learn the user's
 * current energy state, sunset mode, and notification batching
 * preferences.
 *
 * **Opt-in.** The integration is gated by the `mindanchor_enabled`
 * setting (spec §4.10); when disabled, the read paths return
 * defaults and the writes are skipped.
 */
@Singleton
open class AppStateRepository @Inject constructor(
    private val dao: AppDao,
    private val supabase: SupabaseClient,
) {

    private val json = appStateJson

    /**
     * Reactive view of one app's keys. Empty Flow if none.
     */
    fun observeFor(source: AppStateSource): Flow<List<AppStateEntry>> =
        dao.observeBySource(source.name)
            .map { rows -> rows.map { it.toDomain() } }

    /**
     * Read a single key (any value shape). Returns null if absent.
     */
    suspend fun read(source: AppStateSource, key: String): AppStateEntry? =
        dao.get(source.name, key)?.toDomain()

    /**
     * Upsert a key. The local row carries the new value; the
     * cloud write happens in the background (sync outbox).
     */
    suspend fun write(source: AppStateSource, key: String, value: JsonElement) {
        val now = java.time.Instant.now().toString()
        dao.upsert(
            AppStateEntity(
                id = "${source.name}:$key",
                source = source.name,
                `key` = key,
                valueJson = value.toString(),
                updatedAt = now,
            )
        )
    }

    /**
     * M4-T6: read the MindAnchor energy state. The default is
     * NOMINAL — the app behaves as if no integration is present
     * when the row is absent.
     */
    fun observeEnergyState(): Flow<EnergyState> =
        observeFor(AppStateSource.MINDANCHOR)
            .map { rows ->
                rows.firstOrNull { it.key == "energy_state" }
                    ?.value
                    ?.jsonObject
                    ?.get("level")
                    ?.jsonPrimitive
                    ?.let { EnergyState.fromWire(it.content) }
                    ?: EnergyState.NOMINAL
            }

    /**
     * M4-T6: read the MindAnchor sunset-mode flag. Default false.
     */
    fun observeSunsetMode(): Flow<Boolean> =
        observeFor(AppStateSource.MINDANCHOR)
            .map { rows ->
                rows.firstOrNull { it.key == "sunset_mode" }
                    ?.value
                    ?.jsonObject
                    ?.get("enabled")
                    ?.jsonPrimitive
                    ?.boolean
                    ?: false
            }

    /**
     * M4-T6: pull a fresh copy of MindAnchor's app_state rows from
     * the cloud. Called on launch and on every Realtime event for
     * the `app_state` table. The RLS policy restricts to the
     * calling user; MindAnchor-side keys come through because the
     * RLS predicate is on `user_id`, not on `source`.
     */
    suspend fun refreshFromNetwork() {
        val rows: List<AppStateRow> = supabase.postgrest
            .from("app_state")
            .select(Columns.ALL)
            .decodeList()
        val entities = rows.map { row ->
            AppStateEntity(
                id = "${row.source}:${row.key}",
                source = row.source,
                `key` = row.key,
                valueJson = row.value.toString(),
                updatedAt = row.updatedAt,
            )
        }
        dao.upsertAll(entities)
    }
}

enum class AppStateSource { BATON, MINDANCHOR }

/**
 * M4-T6: four-state energy level. Maps from MindAnchor's wire
 * string `"NOMINAL" | "FAIR" | "LOW" | "CRITICAL"`. Used by the
 * brief to shrink the "needs you today" section when energy is
 * low and to suppress nudges.
 */
enum class EnergyState {
    NOMINAL, FAIR, LOW, CRITICAL;
    companion object {
        fun fromWire(s: String): EnergyState = runCatching { valueOf(s.uppercase()) }
            .getOrDefault(NOMINAL)
    }
}

data class AppStateEntry(
    val source: AppStateSource,
    val key: String,
    val value: JsonObject,
    val updatedAt: String,
)

private val appStateJson = Json { ignoreUnknownKeys = true }

internal fun AppStateEntity.toDomain(): AppStateEntry = AppStateEntry(
    source = runCatching { AppStateSource.valueOf(source) }.getOrDefault(AppStateSource.BATON),
    key = `key`,
    value = runCatching { appStateJson.parseToJsonElement(valueJson).jsonObject }.getOrElse { JsonObject(emptyMap()) },
    updatedAt = updatedAt,
)

@Serializable
internal data class AppStateRow(
    val id: String,
    val source: String,
    val key: String,
    val value: JsonElement,
    @SerialName("updated_at") val updatedAt: String,
)
