package com.kaavalan.note.data.brief

import com.kaavalan.note.data.instructions.Instruction
import com.kaavalan.note.data.instructions.Status

/**
 * M4-T1: domain model for a daily brief. The spec defines the
 * `daily_briefs` table (M4 brief content) — needs you today, waiting
 * on others, carried over. This is the client-side shape. The brief
 * is generated locally from the SQLCipher-encrypted instruction
 * mirror, so the UI never has to wait for a Supabase round-trip.
 *
 * **Sections are deliberately unranked lists.** Spec §3.3: "no counts
 * in titles, no streak, no 'X things overdue'". Each section renders
 * as a list, no header counter, no shame language.
 */
data class DailyBrief(
    val date: String,  // ISO yyyy-MM-dd
    val type: BriefType,
    val needsYouToday: List<Instruction>,
    val waitingOnOthers: List<Instruction>,
    val carriedOver: List<Instruction>,
) {
    val isEmpty: Boolean
        get() = needsYouToday.isEmpty() &&
            waitingOnOthers.isEmpty() &&
            carriedOver.isEmpty()
}

enum class BriefType { MORNING, EVENING }

/**
 * M4-T1: the "needs you today" filter per spec §8.1.1.
 *  - direction IN ('INCOMING','SELF')
 *  - status IN ('OPEN','ACK_PENDING','IN_PROGRESS')
 *  - due_at::date == today OR (priority = 'HIGH' AND status = 'OPEN')
 *    OR (now() - updated_at) > interval '7 days'
 */
internal fun Instruction.needsYouToday(today: java.time.LocalDate, now: java.time.Instant): Boolean {
    if (direction !in setOf(com.kaavalan.note.data.instructions.Direction.INCOMING, com.kaavalan.note.data.instructions.Direction.SELF)) return false
    if (status !in setOf(Status.OPEN, Status.ACK_PENDING, Status.IN_PROGRESS)) return false
    val due = dueAt?.let { runCatching { java.time.Instant.parse(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate() }.getOrNull() }
    if (due == today) return true
    if (priority == com.kaavalan.note.data.instructions.Priority.HIGH && status == Status.OPEN) return true
    val updated = runCatching { java.time.Instant.parse(updatedAt) }.getOrNull() ?: return false
    return java.time.Duration.between(updated, now).toDays() > 7
}

/**
 * M4-T1: "waiting on others" per spec §8.1.2.
 *  - direction = 'OUTGOING'
 *  - status IN ('OPEN','ACK_PENDING','IN_PROGRESS')
 *  Sorted by how long they've been waiting (oldest first).
 */
internal fun Instruction.waitingOnOthers(): Boolean =
    direction == com.kaavalan.note.data.instructions.Direction.OUTGOING &&
        status in setOf(Status.OPEN, Status.ACK_PENDING, Status.IN_PROGRESS)

/**
 * M4-T1: "carried over" per spec §8.1.3.
 *  - direction IN ('INCOMING','SELF')
 *  - status = 'OPEN'
 *  - (now() - updated_at) > 7 days
 *  - (now() - updated_at) <= 30 days  (older get dropped silently)
 */
internal fun Instruction.carriedOver(now: java.time.Instant): Boolean {
    if (direction !in setOf(com.kaavalan.note.data.instructions.Direction.INCOMING, com.kaavalan.note.data.instructions.Direction.SELF)) return false
    if (status != Status.OPEN) return false
    val updated = runCatching { java.time.Instant.parse(updatedAt) }.getOrNull() ?: return false
    val days = java.time.Duration.between(updated, now).toDays()
    return days in 8L..30L
}
