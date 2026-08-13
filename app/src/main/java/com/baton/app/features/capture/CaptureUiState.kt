package com.baton.app.features.capture

import com.baton.app.data.captures.CaptureMode
import com.baton.app.data.tags.Tag

/**
 * State of the note bar / capture sheet.
 *
 * v1.4 (PHONE-FINDING-7): [error] now travels with [errorType], a
 * discriminator that the UI uses to pick the right colour + icon.
 * The previous rendering was bright `colorScheme.error` (red) with
 * the generic message "Could not save note. Try again." — both
 * spec §1 violations.
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
    val errorType: ErrorType = ErrorType.NONE,
    val availableTags: List<Tag> = emptyList(),
    val selectedTagIds: Set<String> = emptySet(),
) {
    val canExtract: Boolean
        get() = isVisible && text.isNotBlank() && !isExtracting && !isSaving

    val canConfirm: Boolean
        get() = isVisible && proposal != null && !isSaving

    val canSaveRaw: Boolean
        get() = isVisible && text.isNotBlank() && !isExtracting && !isSaving
}

/**
 * v1.4 (PHONE-FINDING-7): the discriminator for the capture sheet
 * error.
 */
enum class ErrorType {
    NONE,
    NEEDS_PERSON_FIRST,
    NETWORK_UNAVAILABLE,
    PERMISSION_DENIED,
    UNKNOWN,
}
