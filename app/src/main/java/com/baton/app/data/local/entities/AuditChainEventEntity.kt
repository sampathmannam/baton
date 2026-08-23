package com.baton.app.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * v1.8.0 (PROD-READINESS-P2-#4): the hash-chain audit
 * row. Every state change that the [com.baton.app.data.local.SyncEngine]
 * commits appends one row here. The chain is:
 *
 * ```
 *   prevHash = SHA-256( prevRow.payload || prevRow.signingKey )
 *   thisHash = SHA-256( thisRow.payload || prevHash || thisRow.signingKey )
 * ```
 *
 * The chain is anchored at the genesis row (prevHash =
 * "0000...0000") and grows monotonically. A row in the
 * middle of the chain being edited (offline) is detected
 * at the next chain-walk: the recomputed hash of the row
 * above the edit won't match the stored prevHash.
 *
 * **Why a hash chain and not a flat "who did what when"
 * log.** A flat log is a single mutable list — anyone with
 * SQL access can edit a row in the middle and the audit
 * reader has no way to tell. The chain makes any middle
 * edit detectable because the prevHash of the next row
 * won't match the SHA-256 of the row above. The
 * [com.baton.app.data.audit.AuditChainVerifier] walks the
 * chain and reports the first broken link.
 *
 * **Retention.** Per BNSS / state IT Act the chain is
 * retained for 7 years; rows older than that are redacted
 * (payload removed, hash chain preserved) by the
 * [com.baton.app.data.retention.RetentionWorker].
 *
 * **v1.8.0 trade-off.** The chain is built and verified,
 * but the table is not yet populated by [SyncEngine] on
 * every state change — only the manual `audit_log` calls
 * in the v1.8.0 pilot scope write rows. Wiring the
 * [SyncEngine] is a follow-up.
 */
@Entity(
    tableName = "audit_chain_events",
    indices = [
        Index(value = ["tableName", "rowId"]),
        Index(value = ["createdAtMs"]),
    ],
)
data class AuditChainEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /**
     * The table the state change targets. One of
     * `"persons" | "instructions" | "captures" |
     * "tags" | "important_dates"`. Future: `"vault"`,
     * `"audit_chain_events"` (for the chain's own
     * self-edit events).
     */
    val tableName: String,
    /**
     * The local row's id. With [tableName] gives the
     * fully-qualified address of the state change.
     */
    val rowId: String,
    /**
     * The kind of change. One of
     * `"INSERT" | "UPDATE" | "DELETE"`. `DELETE` is
     * soft (the row is marked PENDING_DELETE; the
     * actual Room delete runs on the next sync drain).
     */
    val kind: String,
    /**
     * The JSON-serialised before/after payload. The
     * schema is `"after": {...}` for INSERT + UPDATE,
     * and `"before": {...}` for DELETE. The after-image
     * is the source of truth for restoring the row.
     */
    val payload: String,
    /**
     * The user / device that initiated the change. For
     * the v1.x local-only build this is a client-side
     * UUID stored in SecurePreferences. A pilot with a
     * real auth provider sets it from the JWT's `sub`
     * claim.
     */
    val signingKey: String,
    /**
     * Epoch millis of the change. Set by the
     * [com.baton.app.data.audit.AuditChainWriter] from
     * `System.currentTimeMillis()`.
     */
    val createdAtMs: Long,
    /**
     * SHA-256 of the *previous* row in the chain
     * (`prevRow.payload || prevRow.signingKey`). The
     * genesis row has `prevHash = GENESIS_HASH`. The
     * next row's prevHash is this row's `thisHash`.
     */
    val prevHash: String,
    /**
     * SHA-256 of `thisRow.payload || prevHash ||
     * thisRow.signingKey`. The next row's prevHash is
     * this value.
     */
    val thisHash: String,
) {
    companion object {
        /**
         * The hash for the genesis row's prevHash. The
         * all-zeros string is the canonical "no previous
         * row" sentinel; it does not collide with a real
         * SHA-256 (which is always exactly 64 hex chars).
         */
        const val GENESIS_HASH = "0000000000000000000000000000000000000000000000000000000000000000"
    }
}
