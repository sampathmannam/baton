package com.kaavalan.note.data.dates

import com.kaavalan.note.data.local.ImportantDateDao
import com.kaavalan.note.data.local.entities.ImportantDateEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v2.0 Tier 2 (§2.5): the important-dates domain. Wraps
 * [ImportantDateDao] with a `Flow<List<ImportantDateEntity>>` for
 * the per-person query and a one-shot `today` query for the
 * morning-brief worker (§2.6).
 *
 * `dateEpochDay` is `LocalDate.toEpochDay()` so the "is today"
 * query is a single integer compare.
 */
@Singleton
class ImportantDateRepository @Inject constructor(
    private val dao: ImportantDateDao,
) {
    fun observeForPerson(personId: String): Flow<List<ImportantDateEntity>> =
        dao.observeForPerson(personId)

    fun observeOnDay(epochDay: Long): Flow<List<ImportantDateEntity>> =
        dao.observeOnDay(epochDay)

    fun observeBetweenDays(fromEpochDay: Long, toEpochDay: Long): Flow<List<ImportantDateEntity>> =
        dao.observeBetweenDays(fromEpochDay, toEpochDay)

    suspend fun add(
        personId: String,
        label: String,
        dateEpochDay: Long,
        recurring: Boolean,
    ): ImportantDateEntity {
        val now = Instant.now().toString()
        val entity = ImportantDateEntity(
            id = UUID.randomUUID().toString(),
            personId = personId,
            label = label,
            dateEpochDay = dateEpochDay,
            recurring = recurring,
            createdAt = now,
            updatedAt = now,
        )
        dao.upsert(entity)
        return entity
    }

    suspend fun delete(id: String) = dao.deleteById(id)

    suspend fun deleteForPerson(personId: String) = dao.deleteForPerson(personId)

    /**
     * Returns "today" in the user's local zone as an epoch day.
     */
    fun todayEpochDay(): Long = LocalDate.now().toEpochDay()
}
