package com.baton.app.features.capture

import com.baton.app.data.captures.CaptureMode
import com.baton.app.data.tags.Tag

/**
 * State of the note bar / capture sheet.
 *
 * State machine:
 *   Idle    -> isVisible=false.  The user is on the Home tab; the note
 *             bar is the only affordance.
 *   Editing -> isVisible=true, text!=blank, isExtracting=false, proposal=null.
 *             The user has typed something but hasn't tapped Extract yet.
 *   Working -> isVisible=true, isExtracting=true. The LLM is running.
 *   Review  -> isVisible=true, proposal!=null. The confirmation card is
 *             showing; user can edit fields and confirm.
 *   Failed  -> isVisible=true, error!=null. The LLM returned null or threw.
 *             The sheet stays open with a retry hint.
 *
 * M3-T7: `availableTags` are the user's existing tags (the picker shows
 * them as chips). `selectedTagIds` is the set the user has tapped on
 * before confirming. The flow: sheet opens -> available tags load ->
 * user toggles -> on confirm the instruction row is created, the tag
 * rows are created if free-form `#tag` is missing, and the join rows
 * land in `instruction_tags`.
 *
 * v1.1: `mode` is the capture source (TEXT / VOICE / PHOTO) and is
 * carried all the way through to the instruction row so the audit
 * trail reflects how the user actually captured the thought. v1.0
 * always saved `Source.TEXT` regardless of input — a data integrity
 * gap.
 */
data class CaptureUiState(
    val isVisible: Boolean = false,
    val text: String = "",
    val mode: CaptureMode = CaptureMode.TEXT,
    val isExtracting: Boolean = false,
    val isSaving: Boolean = false,
    val proposal: ExtractedInstruction? = null,
    val addToCalendar: Boolean = false,
    val error: String? = null,
    val availableTags: List<Tag> = emptyList(),
    val selectedTagIds: Set<String> = emptySet(),
) {
    val canExtract: Boolean
        get() = isVisible && text.isNotBlank() && !isExtracting && !isSaving

    val canConfirm: Boolean
        get() = isVisible && proposal != null && !isSaving

    /**
     * v1.1: a "Save as raw text" affordance is shown when the
     * proposal is null after extraction (LLM returned nothing
     * useful) or when the user explicitly wants to skip extraction.
     * Always available when there's text in the box.
     */
    val canSaveRaw: Boolean
        get() = isVisible && text.isNotBlank() && !isExtracting && !isSaving
}
