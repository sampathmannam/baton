package com.kaavalan.note.data.instructions

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object HeaderTemplate {
    private val dueFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())
    data class Inputs(val senderName: String, val senderDesignation: String?, val senderDivision: String?, val dueAtMs: Long?, val shortRef: String)
    fun wrap(body: String, inputs: Inputs): String {
        val designation = inputs.senderDesignation?.takeIf { it.isNotBlank() }
        val division = inputs.senderDivision?.takeIf { it.isNotBlank() }
        val headerFrom = buildString { append("From: "); append(inputs.senderName); if (designation != null) { append(", "); append(designation) }; if (division != null) { append(", "); append(division) } }
        val headerDue = inputs.dueAtMs?.let { "Due: " + dueFormat.format(Instant.ofEpochMilli(it)) }
        val headerRef = "Ref: Kaavalan #" + inputs.shortRef.uppercase().take(6)
        val headerLines = listOfNotNull(headerFrom, headerDue, headerRef)
        return buildString { for (line in headerLines) { append("\u2014 "); append(line); append('\n') }; append('\n'); append(body.trim()); append('\n'); append("\u2014 Kaavalan") }
    }
    fun shortRefFor(id: String): String = id.replace("-", "").take(8).uppercase()
}
