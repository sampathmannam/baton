package com.baton.app.features.capture

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
 */
data class CaptureUiState(
    val isVisible: Boolean = false,
    val text: String = "",
    val isExtracting: Boolean = false,
    val proposal: ExtractedInstruction? = null,
    val addToCalendar: Boolean = false,
    val error: String? = null,
) {
    val canExtract: Boolean
        get() = isVisible && text.isNotBlank() && !isExtracting

    val canConfirm: Boolean
        get() = isVisible && proposal != null
}
