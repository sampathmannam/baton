package com.baton.app.data.captures

/**
 * A raw capture — the unprocessed input the user typed, spoke, or
 * snapped. Captures are the audit trail. The M1-T4 LLM extraction
 * reads a capture and (if successful) produces an [ExtractedInstruction]
 * (in `com.baton.app.features.capture`); M1-T5 then creates an
 * `instructions` row and links it back to the capture.
 *
 * The M0 `captures` table has these columns:
 *   id, user_id, mode, raw_text, audio_uri, image_uri,
 *   processed, created_at
 *
 * M1 only ever writes `TEXT` mode captures; `VOICE` and `PHOTO` are
 * M2.
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
