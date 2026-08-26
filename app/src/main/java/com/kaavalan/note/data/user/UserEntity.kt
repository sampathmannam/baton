package com.kaavalan.note.data.user

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * v1.8.0 (PROD-READINESS-P2-#3): the per-officer user
 * row.
 *
 * The pre-v1.8.0 build had no [User] table — the
 * vault-mode build has exactly one officer (the device
 * owner) and the `userId` columns on the data rows
 * were hard-coded to a single client-generated UUID.
 * A pilot deployment with multiple officers in one
 * station needs per-officer rows so:
 *  1. The capture sheet's "Add person" + "Capture
 *     note" attribution is per-officer (officer A's
 *     notes are A's, not B's).
 *  2. The role model ([Role]) gates write APIs
 *     (READONLY officers can browse but not write).
 *  3. The audit chain ([com.kaavalan.note.data.audit.AuditChainWriter])
 *     signs with the user's id, not the device's.
 *
 * **v1.8.0 trade-off.** The local-only build still has
 * exactly one row in this table (the device owner,
 * `deviceOwner = true`). The role defaults to
 * [Role.SENIOR_OFFICER] because the v1.8.0 single-user
 * case is always the "most powerful" role — a
 * multi-officer build sets the role per officer at
 * provisioning time (out of scope for v1.8.0; a
 * future Settings → Provisioning tab handles it).
 */
@Entity(
    tableName = "users",
    indices = [
        // v1.8.0: a non-unique index on `deviceOwner`
        // for the `deviceOwner = 1` look-up. The
        // "exactly one device owner" invariant is
        // enforced by a partial unique index in the
        // v14->v15 migration (Room's @Index(unique=)
        // does not support partial WHERE clauses).
        Index(value = ["deviceOwner"]),
    ],
)
data class UserEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val role: String,
    /** True if this user owns the device (exactly one row in the table). */
    val deviceOwner: Boolean = false,
    val createdAt: String,
)
