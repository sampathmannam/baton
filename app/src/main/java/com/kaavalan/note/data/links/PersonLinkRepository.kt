package com.kaavalan.note.data.links

import com.kaavalan.note.data.local.PersonLinkDao
import com.kaavalan.note.data.local.entities.PersonLinkEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v2.0 Tier 2 (§2.12): person-to-person links. The
 * `from -> to` direction is the canonical direction; the UI
 * can also show the reverse direction (i.e. "Ramesh is linked
 * to me as Reports-to"). Self-loops are accepted at the DAO
 * layer but the UI prevents them.
 */
@Singleton
class PersonLinkRepository @Inject constructor(
    private val dao: PersonLinkDao,
) {
    fun observeForPerson(personId: String): Flow<List<PersonLinkEntity>> =
        dao.observeForPerson(personId)

    suspend fun add(fromId: String, toId: String, relation: String) {
        val entity = PersonLinkEntity(
            fromId = fromId,
            toId = toId,
            relation = relation,
            createdAt = Instant.now().toString(),
        )
        dao.upsert(entity)
    }

    suspend fun delete(fromId: String, toId: String, relation: String) =
        dao.deleteEdge(fromId, toId, relation)
}
