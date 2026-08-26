package com.kaavalan.note.data.tags

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * M3-T7: domain model for a tag (spec §4.3). The `kind` enum drives
 * the UI colour in the picker and the person/instruction chips:
 *  - PERSON / DESIGNATION / STATION  -- "structural", colored dot
 *  - CASE / FIR / PRIORITY             -- "contextual", colored dot
 *  - FREE                              -- plain, user-authored
 *
 * Tag creation is automatic: the LLM extractor surfaces a tag name
 * + kind for every captured person, designation, station, FIR
 * number, or free-form `#tag`, and the sync engine creates the row
 * on first sight. The user can also create a tag manually from
 * the management screen.
 */
@Serializable
data class Tag(
    val id: String,
    val name: String,
    val kind: TagKind,
    val color: String? = null,
    @SerialName("usage_count") val usageCount: Int = 0,
    @SerialName("last_used_at") val lastUsedAt: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
)

/** Wire values match the `tag_kind` Postgres enum. */
@Serializable
enum class TagKind { PERSON, DESIGNATION, STATION, CASE, FIR, PRIORITY, FREE }

/** Wire shape for the cloud `tags` table. */
@Serializable
internal data class TagRow(
    val id: String,
    val name: String,
    val kind: TagKind,
    val color: String? = null,
    @SerialName("usage_count") val usageCount: Int = 0,
    @SerialName("last_used_at") val lastUsedAt: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
) {
    fun toDomain(): Tag = Tag(
        id = id,
        name = name,
        kind = kind,
        color = color,
        usageCount = usageCount,
        lastUsedAt = lastUsedAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
