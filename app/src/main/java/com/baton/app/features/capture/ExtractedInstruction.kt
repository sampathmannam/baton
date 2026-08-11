package com.baton.app.features.capture

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Structured instruction extracted from a free-text note. This is the M1
 * target shape; produced by the on-device LLM in M1-T4 and confirmed by
 * the user in the confirmation card.
 *
 * Field semantics:
 *  - [person]: the person the instruction is about. `null` if no person is
 *    named. The save flow (M1-T5) auto-creates a person if this is non-null
 *    and the name doesn't already exist.
 *  - [action]: the verb phrase; never blank.
 *  - [dueAt]: ISO 8601 timestamp in `Asia/Kolkata`. `null` if no time cue
 *    was detected. The GBNF grammar accepts any string; the confirmation
 *    card validates and lets the user correct it.
 *  - [priority]: one of NORMAL, URGENT, LOW. Default NORMAL.
 *  - [instructionText]: the full instruction in clean prose.
 *  - [confidence]: 0.0 to 1.0, model's self-report. The save flow drops
 *    proposals with `confidence < 0.5` (user retypes).
 */
@Serializable
data class ExtractedInstruction(
    val person: String? = null,
    val action: String,
    @SerialName("due_at") val dueAt: String? = null,
    val priority: String = "NORMAL",
    @SerialName("instruction_text") val instructionText: String,
    val confidence: Double = 0.0,
)
