package com.kaavalan.note.data.bridge

import com.kaavalan.note.data.instructions.Instruction
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v1.8.0 (PROD-READINESS-P2-#7): the CCTNS / ICJS / eFIR bridge
 * interface stub.
 *
 * **Why a stub and not a real implementation.** The actual
 * CCTNS (Crime and Criminal Tracking Network & Systems) and
 * ICJS (Inter-operable Criminal Justice System) bridges are
 * owned by the central IT ministry and the state CID; the
 * v1.8.0 pilot has no API credentials, no signed MOU, and no
 * schema documentation in the public domain. Building a fake
 * "succeeds" implementation would be worse than no bridge
 * at all — the user would think their FIR was filed with
 * the state when in fact it was filed with /dev/null.
 *
 * **The stub contract.** Every method returns a sealed
 * [BridgeResult] with a [BridgeStatus.NotConfigured] case
 * so the call sites fail LOUDLY ("CCTNS bridge not
 * configured; check the Settings → Pilot tab") instead of
 * silently swallowing the write. A future v2.x with a real
 * API key + signed schema implements the same interface;
 * the call sites do not change.
 *
 * **The four call sites that need this interface** (all
 * currently bypass via the local-only path):
 *  1. `Instruction.markDone` — could optionally push the
 *     closed-FIR to CCTNS as an "investigation complete"
 *     update.
 *  2. `Capture.create` (PHOTO mode with an FIR number) —
 *     could push the photo + OCR text to the eFIR
 *     attachment bucket.
 *  3. `ImportantDate` create for a court date — could
 *     register the date with ICJS so the case-management
 *     system flags it.
 *  4. `Person.setSensitive` toggling — could push the
 *     declassification to the audit chain.
 *
 * None of these are wired in v1.8.0; the interface exists
 * so the call sites can be added without a re-architecture.
 */
interface CctnsBridge {

    /**
     * Push a closed instruction (status = DONE) to the
     * state CCTNS endpoint as a "case-update" record.
     * Returns [BridgeResult.NotConfigured] until the
     * build is wired with real API credentials.
     */
    suspend fun pushInstructionClosed(instruction: Instruction): BridgeResult

    /**
     * Push a PHOTO capture with an attached FIR number
     * to the eFIR bucket. Returns
     * [BridgeResult.NotConfigured] until a real FIR API
     * is wired.
     */
    suspend fun pushFirPhotoAttachment(
        captureId: String,
        firNumber: String,
        photoUri: String,
    ): BridgeResult

    /**
     * Register a court date with the ICJS so the
     * case-management system can flag the date on its
     * own dashboard. Returns [BridgeResult.NotConfigured]
     * until the ICJS API is wired.
     */
    suspend fun registerCourtDate(
        personId: String,
        dateEpochDay: Long,
        label: String,
    ): BridgeResult

    /**
     * Push a "declassification" event (a sensitive row
     * was toggled to non-sensitive) to the audit chain.
     * Returns [BridgeResult.NotConfigured] until the
     * audit-chain endpoint is wired.
     */
    suspend fun pushDeclassification(
        personId: String,
        declassifiedBy: String,
        declassifiedAt: Long,
    ): BridgeResult
}

/**
 * The four outcomes every bridge call can have. A
 * future real implementation will add a [Success] case
 * with the remote record id; the v1.8.0 stub never returns
 * Success because there is no real bridge to call.
 */
sealed class BridgeResult {
    data object NotConfigured : BridgeResult()
    data class Success(val remoteId: String) : BridgeResult()
    data class Failed(val reason: String) : BridgeResult()
}

/**
 * v1.8.0 (PROD-READINESS-P2-#7): the no-op default
 * implementation. Every method returns
 * [BridgeResult.NotConfigured] so the call sites fail
 * loudly without crashing. A real pilot build replaces
 * this with an implementation that holds API credentials
 * + signed-schema request bodies.
 */
@Singleton
class NoOpCctnsBridge @Inject constructor() : CctnsBridge {
    override suspend fun pushInstructionClosed(instruction: Instruction): BridgeResult =
        BridgeResult.NotConfigured

    override suspend fun pushFirPhotoAttachment(
        captureId: String,
        firNumber: String,
        photoUri: String,
    ): BridgeResult = BridgeResult.NotConfigured

    override suspend fun registerCourtDate(
        personId: String,
        dateEpochDay: Long,
        label: String,
    ): BridgeResult = BridgeResult.NotConfigured

    override suspend fun pushDeclassification(
        personId: String,
        declassifiedBy: String,
        declassifiedAt: Long,
    ): BridgeResult = BridgeResult.NotConfigured
}
