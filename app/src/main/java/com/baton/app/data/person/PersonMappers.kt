package com.baton.app.data.person

import com.baton.app.data.local.entities.PersonEntity
import com.baton.app.data.local.entities.SyncStatus

/**
 * Convert between the [Person] domain model and the [PersonEntity] Room
 * row. The wire format (`PersonInsert`) lives in
 * `SupabasePersonRepository` and is `internal` so the SyncEngine can
 * share it.
 *
 * M2-T8: [toEntity] now carries [Person.updatedAt] through. When the
 * caller has no `updatedAt` (e.g. an in-flight local write before
 * `RoomPersonRepository.create` stamps it), we fall back to "now"
 * in ISO-8601 form so the column is never blank.
 */
fun PersonEntity.toDomain(): Person = Person(
    id = id,
    name = name,
    designation = designation,
    station = station,
    phone = phone,
    updatedAt = updatedAt,
)

fun Person.toEntity(syncStatus: String = SyncStatus.SYNCED): PersonEntity = PersonEntity(
    id = id,
    name = name,
    designation = designation,
    station = station,
    phone = phone,
    userId = "",  // Filled by the server on insert; the local row
    // doesn't need it (RLS is enforced at the wire).
    createdAt = updatedAt ?: java.time.Instant.now().toString(),
    updatedAt = updatedAt ?: java.time.Instant.now().toString(),
    syncStatus = syncStatus,
)
