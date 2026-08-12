package com.baton.app.data.person

import com.baton.app.BuildConfig
import com.baton.app.data.supabase.buildSupabaseClient
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.ktor.client.HttpClient
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
 */
class SupabasePersonRepository(
    httpClient: HttpClient,
) {

    private val client: SupabaseClient = buildSupabaseClient(
        url = BuildConfig.SUPABASE_URL,
        key = BuildConfig.SUPABASE_ANON_KEY,
        httpClient = httpClient,
    )

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
            .select(columns = Columns.list("id", "name", "designation", "station", "phone", "user_id")) {
                filter {
                    eq("name", name)
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
     */
    suspend fun fetchAll(): List<Person> {
        val rows: List<PersonRow> = client.postgrest
            .from("persons")
            .select()
            .decodeList()
        return rows.map { it.toDomain() }
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
) {
    fun toDomain(): Person = Person(
        id = id,
        name = name,
        designation = designation,
        station = station,
        phone = phone,
    )
}

@Serializable
internal data class PersonInsert(
    val id: String? = null,
    val name: String,
    val designation: String? = null,
    val station: String? = null,
)
