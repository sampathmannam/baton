package com.baton.app.data.person

import com.baton.app.BuildConfig
import com.baton.app.data.supabase.buildSupabaseClient
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.ktor.client.HttpClient
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Supabase-backed [PersonRepository] for M0. Reads from the `persons` table
 * created in Task 5 (`supabase/migrations/0001_init.sql`).
 *
 * M0 reads the user's own persons only — RLS on the table restricts the
 * result to rows where `user_id = auth.uid()`. Once auth is wired in Task 7
 * the `Authorization: Bearer <jwt>` header is set automatically by the
 * supabase-kt Auth plugin.
 *
 * M3 will replace this with a Room-backed local mirror + sync loop; for M0
 * a single Supabase read on Home open is enough to prove the wire-up.
 */
class SupabasePersonRepository(
    httpClient: HttpClient,
) : PersonRepository {

    private val client: SupabaseClient = buildSupabaseClient(
        url = BuildConfig.SUPABASE_URL,
        key = BuildConfig.SUPABASE_ANON_KEY,
        httpClient = httpClient,
    )

    override suspend fun observeAll(): List<Person> {
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
