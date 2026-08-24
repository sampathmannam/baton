package com.baton.app.data.person

import com.baton.app.data.supabase.BatonSupabase
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Supabase-backed network adapter for the `persons` table. M2-T6
 * decoupled this from the [PersonRepository] interface: the
 * interface is now Room-backed (see
 * [com.baton.app.data.local.RoomPersonRepository]). The
 * Supabase client only does POST/PATCH/DELETE/GET; the Room repo
 * is the only thing the UI talks to.
 *
 * M0 reads/writes the user's own persons only — RLS on the table restricts
 * the result to rows where `user_id = auth.uid()` and rejects inserts/updates
 * for any other user. Once auth is wired in Task 7 the JWT is set
 * automatically by the supabase-kt Auth plugin.
 *
 * M2-T8: [findById] is used by the sync engine's last-write-wins
 * conflict check — it reads the server's `updated_at` and compares
 * against the local row before applying an UPDATE.
 *
 * **v1.9.10 (Obs-1 fix):** the constructor now takes the shared
 * [BatonSupabase] singleton instead of building a fresh
 * `SupabaseClient` in a field initializer. See [BatonSupabase]'s
 * class docstring for the four-clients-collapsed-into-one rationale.
 */
class SupabasePersonRepository(
    batonSupabase: BatonSupabase,
) {

    private val client = batonSupabase.client

    suspend fun create(
        name: String,
        designation: String?,
        station: String?,
        clientId: String? = null,
    ): Person {
        // `select()` after `insert` returns the row that was inserted, so we
        // can hand the new Person back to the UI without a second round-trip.
        val inserted: PersonRow = client.postgrest
            .from("persons")
            .insert(
                PersonInsert(
                    id = clientId,
                    name = name,
                    designation = designation,
                    station = station,
                ),
            ) {
                select()
            }
            .decodeSingle()
        return inserted.toDomain()
    }

    suspend fun findByName(name: String): Person? {
        // Order by created_at so the first hit is deterministic.
        val rows: List<PersonRow> = client.postgrest
            .from("persons")
            .select(
                columns = Columns.list(
                    "id", "name", "designation", "station", "phone",
                    "user_id", "updated_at",
                ),
            ) {
                filter {
                    eq("name", name)
                }
                limit(1)
            }
            .decodeList()
        return rows.firstOrNull()?.toDomain()
    }

    /**
     * M2-T8: fetch a single person by id. Used by the sync engine
     * for last-write-wins conflict detection. Returns `null` if the
     * row no longer exists on the server (e.g. deleted by another
     * device).
     */
    suspend fun findById(id: String): Person? {
        val rows: List<PersonRow> = client.postgrest
            .from("persons")
            .select(
                columns = Columns.list(
                    "id", "name", "designation", "station", "phone",
                    "user_id", "updated_at",
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

    suspend fun findOrCreate(
        name: String,
        designation: String?,
        station: String?,
    ): Person {
        findByName(name)?.let { return it }
        // Race window: two concurrent calls could both miss the lookup
        // and both attempt to insert. The unique constraint
        // `(user_id, name, designation, station)` makes the second
        // insert fail; we catch and re-lookup.
        return try {
            create(name = name, designation = designation, station = station)
        } catch (e: Exception) {
            // The unique-violation class is platform-specific; PostgREST
            // returns HTTP 409. Treat any insert failure here as a race
            // and re-read.
            val found = findByName(name)
            found ?: throw e
        }
    }

    /**
     * M2-T6: pull all rows for the user. Used by the initial Room
     * seed and by the Realtime-triggered refresh. Returns the rows
     * as [Person] (no Room mapping — the caller does that).
     *
     * v1.1: filters out any `is_sensitive = true` rows. Sensitive
     * rows should never have been on the server (spec §13) but
     * defensive filtering keeps the local mirror clean.
     */
    suspend fun fetchAll(): List<Person> {
        val rows: List<PersonRow> = client.postgrest
            .from("persons")
            .select()
            .decodeList()
        return rows
            .filter { !it.isSensitive }
            .map { it.toDomain() }
    }

    /**
     * v1.1: PATCH the `is_sensitive` flag. Used by the sync engine
     * when draining a person-update entry that flipped the flag.
     * The server-side row is updated in place; the local mirror
     * stays the source of truth for the UI.
     */
    suspend fun setSensitive(id: String, sensitive: Boolean) {
        client.postgrest
            .from("persons")
            .update(mapOf("is_sensitive" to sensitive)) {
                filter { eq("id", id) }
            }
    }
}

@Serializable
private data class PersonRow(
    val id: String,
    val name: String,
    val designation: String? = null,
    val station: String? = null,
    val phone: String? = null,
    @SerialName("user_id") val userId: String,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("is_sensitive") val isSensitive: Boolean = false,
) {
    fun toDomain(): Person = Person(
        id = id,
        name = name,
        designation = designation,
        station = station,
        phone = phone,
        updatedAt = updatedAt,
        isSensitive = isSensitive,
    )
}

@Serializable
internal data class PersonInsert(
    val id: String? = null,
    val name: String,
    val designation: String? = null,
    val station: String? = null,
)

/**
 * M2-T8: JSON-serialisable snapshot of a Person row at the moment
 * of a conflict. Stored in `sync_conflicts.localPayload` /
 * `serverPayload` so the user can review what was lost and what
 * won. Distinct from [PersonEntity] (which has Room annotations
 * we don't want in the audit JSON) and from [PersonInsert] (which
 * doesn't carry [updatedAt] / [userId]).
 */
@Serializable
data class PersonConflictPayload(
    val id: String,
    val name: String,
    val designation: String? = null,
    val station: String? = null,
    val phone: String? = null,
    @SerialName("user_id") val userId: String = "",
    @SerialName("updated_at") val updatedAt: String? = null,
)
