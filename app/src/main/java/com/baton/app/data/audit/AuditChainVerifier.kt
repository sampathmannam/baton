package com.baton.app.data.audit

import com.baton.app.data.local.AuditChainEventDao
import com.baton.app.data.local.entities.AuditChainEventEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v1.8.0 (PROD-READINESS-P2-#4): the audit-chain
 * verifier. Walks the chain in id-order and checks
 *  (a) every `prevHash` matches the previous row's
 *      `thisHash` (or GENESIS_HASH for the first row)
 *  (b) every `thisHash` matches the SHA-256 of
 *      `payload || prevHash || signingKey` (the writer
 *      uses the same formula).
 *
 * The first broken link is reported as
 * [VerifyResult.BrokenAt]; the rowid + table + expected
 * vs actual hashes are returned so the user can decide
 * whether to redact the broken row or restore the
 * expected hash. (A broken row is not necessarily
 * malicious — a power-loss between writer + verifier
 * can leave the chain in a degraded state.)
 */
@Singleton
class AuditChainVerifier @Inject constructor(
    private val dao: AuditChainEventDao,
) {

    suspend fun verify(): VerifyResult {
        val rows = dao.snapshot()
        if (rows.isEmpty()) return VerifyResult.Intact(0)
        var prevHash = AuditChainEventEntity.GENESIS_HASH
        for ((idx, row) in rows.withIndex()) {
            if (row.prevHash != prevHash) {
                return VerifyResult.BrokenAt(
                    rowId = row.id,
                    tableName = row.tableName,
                    rowPk = row.rowId,
                    expectedPrev = prevHash,
                    actualPrev = row.prevHash,
                    index = idx,
                )
            }
            val expectedThis = AuditChainWriter.computeHash(
                payload = row.payload,
                prevHash = row.prevHash,
                signingKey = row.signingKey,
            )
            if (row.thisHash != expectedThis) {
                return VerifyResult.BrokenAt(
                    rowId = row.id,
                    tableName = row.tableName,
                    rowPk = row.rowId,
                    expectedPrev = row.prevHash,
                    actualPrev = expectedThis,
                    index = idx,
                )
            }
            prevHash = row.thisHash
        }
        return VerifyResult.Intact(rows.size)
    }
}

sealed class VerifyResult {
    data class Intact(val eventCount: Int) : VerifyResult()
    data class BrokenAt(
        val rowId: Long,
        val tableName: String,
        val rowPk: String,
        val expectedPrev: String,
        val actualPrev: String,
        val index: Int,
    ) : VerifyResult()
}
