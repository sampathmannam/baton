package com.kaavalan.note.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room mirror of the `captures` table. Mirrors
 * [com.kaavalan.note.data.captures.Capture]. `mode` is stored as the
 * string form ("TEXT" | "VOICE" | "PHOTO") for direct read parity
 * with Supabase; the in-memory conversion happens at the
 * repository boundary.
 *
 * v2.0 Tier 2 fields (migration v10 -> v11):
 *  - [ocrText]: §2.4 photo OCR result. Null for TEXT / VOICE captures.
 *  - [calendarEventId]: §2.9 calendar-link. When a capture is
 *    auto-attached to a calendar event, the event's row id is
 *    stored here.
 *  - [urgency]: §2.10 worry-box. Same convention as
 *    [InstructionEntity.urgency]: "normal" | "worry" | "worry_with_date".
 */
@Entity(
    tableName = "captures",
    indices = [
        Index(value = ["mode"]),
        Index(value = ["processed"]),
        Index(value = ["syncStatus"]),
        Index(value = ["urgency"]),
    ],
)
data class CaptureEntity(
    @PrimaryKey val id: String,
    val mode: String,
    val rawText: String?,
    val audioUri: String?,
    val imageUri: String?,
    val processed: Boolean,
    val createdAt: String,
    val syncStatus: String = SyncStatus.SYNCED,
    // v2.0 Tier 2 (§2.4): photo OCR text. Set by PhotoCapture after
    // ML Kit's TextRecognizer processes the image. Null for TEXT
    // and VOICE captures.
    val ocrText: String? = null,
    // v2.0 Tier 2 (§2.9): calendar-link. The event-id from
    // CalendarContract.Events._ID when the capture is auto-attached.
    val calendarEventId: String? = null,
    // v2.0 Tier 2 (§2.10): worry-box marker. Same vocabulary as
    // InstructionEntity.urgency: "normal" | "worry" | "worry_with_date".
    val urgency: String = "normal",
    // v2.0 Tier 2 (§2.10): if urgency == "worry_with_date", the
    // epoch-day for the user-set review.
    val reviewAtEpochDay: Long? = null,
)
