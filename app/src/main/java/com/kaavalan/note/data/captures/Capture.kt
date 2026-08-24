package com.kaavalan.note.data.captures

/**
 * A raw capture — the unprocessed input the user typed, spoke, or
 * snapped. Captures are the audit trail.
 *
 * v1.6.1: the on-device LLM (which used to read a capture and
 * produce an [ExtractedInstruction] via M1-T4) is gone. The
 * capture sheet is now: type / speak / photograph -> tap Save
 * -> one `instructions` row + one `captures` row are written
 * in the same coroutine. The captures table preserves the
 * source mode (TEXT / VOICE / PHOTO) so a future review can
 * see how the note was entered.
 *
 * The M0 `captures` table has these columns:
 *   id, user_id, mode, raw_text, audio_uri, image_uri,
 *   processed, created_at
 *
 * All three modes (TEXT, VOICE, PHOTO) are written in v1.6.1.
 */
data class Capture(
    val id: String,
    val mode: CaptureMode,
    val rawText: String?,
    val audioUri: String? = null,
    val imageUri: String? = null,
    val processed: Boolean,
    val createdAt: String,
)

enum class CaptureMode {
    TEXT, VOICE, PHOTO;

    /** Wire value matching the `instruction_source` Postgres enum. */
    fun toDbValue(): String = when (this) {
        TEXT -> "TEXT"
        VOICE -> "VOICE"
        PHOTO -> "PHOTO"
    }

    companion object {
        fun fromDbValue(value: String): CaptureMode = when (value) {
            "TEXT" -> TEXT
            "VOICE" -> VOICE
            "PHOTO" -> PHOTO
            else -> TEXT
        }
    }
}
