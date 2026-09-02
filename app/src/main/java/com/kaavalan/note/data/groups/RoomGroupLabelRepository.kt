package com.kaavalan.note.data.groups

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomGroupLabelRepository @Inject constructor(
    private val dao: GroupLabelDao,
) : GroupLabelRepository {

    override fun observeAll(): Flow<List<GroupLabel>> =
        dao.observeAll().map { rows -> rows.map(GroupLabelEntity::toDomain) }

    override suspend fun create(name: String, responsiblePersonId: String?): GroupLabel {
        val normalizedName = name.trim()
        require(normalizedName.isNotEmpty()) { "Group label name is required" }
        dao.findByName(normalizedName)?.let { return it.toDomain() }

        val now = Instant.now().toString()
        val entity = GroupLabelEntity(
            id = UUID.randomUUID().toString(),
            name = normalizedName,
            responsiblePersonId = responsiblePersonId,
            createdAt = now,
            updatedAt = now,
        )
        val inserted = dao.insert(entity)
        if (inserted != -1L) return entity.toDomain()
        return checkNotNull(dao.findByName(normalizedName)) {
            "Group label insert conflicted but no existing row was found"
        }.toDomain()
    }

    override suspend fun delete(id: String) {
        dao.deleteById(id)
    }
}
