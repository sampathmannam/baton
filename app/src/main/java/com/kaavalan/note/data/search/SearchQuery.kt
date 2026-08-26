package com.kaavalan.note.data.search

/**
 * Tier 1.3 (v2.0): build a safe FTS4 MATCH expression from
 * a free-form user query.
 *
 * The rules:
 *  - tokenize on whitespace
 *  - drop tokens that look like FTS4 reserved chars
 *    (`"`, `*`, `-`, `+`, `(`, `)`, `:`) — these would crash
 *    the MATCH parser
 *  - append `*` to each surviving token so a prefix search
 *    works ("ramesh*" matches "Ramesh", "Rameshwaram" etc.)
 *  - return an empty string if the input is empty / blank /
 *    has no surviving tokens (caller short-circuits to
 *    "no results")
 *
 * The expression is **safe to pass to a `MATCH` clause** —
 * no SQL-injection path, no reserved-char surprises. We do
 * NOT concat with the table name (Room does that from the
 * DAO method's parameter).
 */
object SearchQuery {

    fun build(input: String): String {
        if (input.isBlank()) return ""
        val tokens = input
            .split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { stripFtsReserved(it) }
            .filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return ""
        return tokens.joinToString(" ") { "$it*" }
    }

    private fun stripFtsReserved(token: String): String {
        // Remove FTS4 reserved characters from the token; if
        // the result is empty, the token is dropped.
        val cleaned = token
            .replace(Regex("[\\\"\\*\\-\\+\\(\\)\\:\\^]"), "")
        return cleaned
    }
}
