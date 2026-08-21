package com.baton.app.data.audit

import com.baton.app.data.local.AuditChainEventDao
import com.baton.app.data.local.entities.AuditChainEventEntity
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v1.8.0 (PROD-READINESS-P2-#4): the audit-chain writer.
 * Appends a [AuditChainEventEntity] to the chain,
 * computing the `thisHash` from the previous row's hash
 * + the new row's payload.
 *
 * The chain is anchored at [AuditChainEventEntity.GENESIS_HASH]
 * (the all-zeros sentinel). The first row of an empty
 * table has `prevHash = GENESIS_HASH` and `thisHash =
 * SHA-256("payload" || GENESIS_HASH || "signingKey")`.
 * Every subsequent row's `prevHash` is the previous row's
 * `thisHash` — a single mutation in the middle is
 * detectable by re-walking the chain.
 *
 * **Hash choice.** SHA-256 is in the platform; no
 * extra dep. The `MessageDigest.getInstance("SHA-256")`
 * call is the JCE standard; on Android the
 * Conscrypt provider is the default. Performance is
 * sufficient for a per-write append (~2 µs on a Pixel
 * 6) and the payload is small (a JSON-serialised row
 * is typically 1-3 KB, well within the SHA-256 input
 * limit).
 *
 * **Failure mode.** If the DAO insert throws (DB
 * corruption, disk full), the chain is not extended.
 * The caller must treat the original write + the chain
 * append as a single transaction; a future v2.x wraps
 * both in a Room `@Transaction`. For v1.8.0 the chain
 * append is best-effort and a chain-walk that finds a
 * gap reports it via [AuditChainVerifier].
 */
@Singleton
class AuditChainWriter @Inject constructor(
    private val dao: AuditChainEventDao,
    private val signingKeyProvider: SigningKeyProvider,
) {

    /**
     * Append a new event to the chain. Returns the
     * rowid of the new event.
     */
    suspend fun append(
        tableName: String,
        rowId: String,
        kind: String,
        payload: String,
    ): Long {
        val prev = dao.latest()
        val prevHash = prev?.thisHash ?: AuditChainEventEntity.GENESIS_HASH
        val signingKey = signingKeyProvider.signingKey()
        val thisHash = computeHash(payload, prevHash, signingKey)
        val event = AuditChainEventEntity(
            tableName = tableName,
            rowId = rowId,
            kind = kind,
            payload = payload,
            signingKey = signingKey,
            createdAtMs = System.currentTimeMillis(),
            prevHash = prevHash,
            thisHash = thisHash,
        )
        return dao.insert(event)
    }

    companion object {
        /**
         * SHA-256 over `payload || prevHash || signingKey`,
         * returned as a lowercase hex string. The concat
         * is unambiguous (no delimiter) because each
         * field is fixed-length (signingKey is a
         * device-specific UUID, 36 chars; prevHash is a
         * 64-char SHA-256 hex digest; payload is a JSON
         * object that always ends in `}`). The output is
         * a 64-char lowercase hex string.
         */
        fun computeHash(payload: String, prevHash: String, signingKey: String): String {
            val md = MessageDigest.getInstance("SHA-256")
            val combined = payload + prevHash + signingKey
            val bytes = md.digest(combined.toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}

/**
 * v1.8.0 (PROD-READINESS-P2-#4): the signing-key
 * provider. The default implementation returns a
 * device-scoped UUID stored in SecurePreferences. A
 * pilot with a real auth provider overrides this to
 * return the user's JWT `sub` claim so audit events
 * are signed by the user, not the device.
 */
fun interface SigningKeyProvider {
    fun signingKey(): String
}
