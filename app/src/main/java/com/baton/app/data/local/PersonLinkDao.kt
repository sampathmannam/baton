package com.baton.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.baton.app.data.local.entities.PersonLinkEntity
import kotlinx.coroutines.flow.Flow

/**
 * v2.0 Tier 2 (§2.12): DAO for the `person_link` table. Directed
 * edges between two people with a free-form relation label.
 * Composite primary key on (fromId, toId, relation) so the same
 * pair can have multiple distinct relations.
 *
 * Cascade delete is set up at the table level: removing a
 * [com.baton.app.data.local.entities.PersonEntity] wipes any
 * edge that uses them as either endpoint.
 */
@Dao
interface PersonLinkDao {

    @Query("SELECT * FROM person_link WHERE fromId = :personId ORDER BY relation ASC")
    fun observeFrom(personId: String): Flow<List<PersonLinkEntity>>

    @Query("SELECT * FROM person_link WHERE toId = :personId ORDER BY relation ASC")
    fun observeTo(personId: String): Flow<List<PersonLinkEntity>>

    /**
     * The PersonDetailScreen wants both directions. This is the
     * OR of [observeFrom] and [observeTo] in one round trip.
     */
    @Query(
        """
        SELECT * FROM person_link
        WHERE fromId = :personId OR toId = :personId
        ORDER BY relation ASC
        """,
    )
    fun observeForPerson(personId: String): Flow<List<PersonLinkEntity>>

    @Query("SELECT * FROM person_link")
    suspend fun snapshot(): List<PersonLinkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(link: PersonLinkEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(links: List<PersonLinkEntity>)

    @Query("DELETE FROM person_link WHERE fromId = :fromId AND toId = :toId AND relation = :relation")
    suspend fun deleteEdge(fromId: String, toId: String, relation: String)
}
