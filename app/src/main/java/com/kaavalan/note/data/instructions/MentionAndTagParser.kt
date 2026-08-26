package com.kaavalan.note.data.instructions

object MentionAndTagParser {
    data class Token(val kind: Kind, val text: String, val start: Int, val end: Int) { enum class Kind { AT_MENTION, HASHTAG } }
    data class ParseResult(val body: String, val tokens: List<Token>, val mentions: List<Mention>, val hashtags: List<String>)
    data class Mention(val raw: String, val prefix: Prefix, val payload: String) { enum class Prefix { ALL, STATION, DESIGNATION, NAME } }
    fun parse(body: String): ParseResult {
        if (body.isEmpty()) return ParseResult(body, emptyList(), emptyList(), emptyList())
        val tokens = mutableListOf<Token>(); val mentions = mutableListOf<Mention>(); val hashtags = mutableListOf<String>()
        val seenHashtags = mutableSetOf<String>(); val seenMentions = mutableSetOf<String>()
        var i = 0
        while (i < body.length) {
            val c = body[i]
            if (c == '@' || c == '#') {
                if (i == 0 || body[i - 1].isWhitespace()) {
                    val end = scanToken(body, i + 1)
                    val raw = body.substring(i, end).trimEnd { it in TRAILING_PUNCTUATION }
                    if (raw.length > 1) {
                        if (c == '@') { tokens.add(Token(Token.Kind.AT_MENTION, raw, i, i + raw.length)); val mention = classifyMention(raw); if (seenMentions.add(raw.lowercase())) mentions.add(mention) }
                        else { tokens.add(Token(Token.Kind.HASHTAG, raw, i, i + raw.length)); val tag = raw.substring(1).lowercase(); if (seenHashtags.add(tag)) hashtags.add(tag) }
                        i = i + raw.length; continue
                    }
                }
            }
            i++
        }
        return ParseResult(body, tokens, mentions, hashtags)
    }
    private fun scanToken(body: String, start: Int): Int { var i = start; while (i < body.length && !body[i].isWhitespace()) { if (body[i] == ':' && i > start) { i++; while (i < body.length && !body[i].isWhitespace()) i++; return i }; i++ }; return i }
    private fun classifyMention(raw: String): Mention {
        val payload = raw.substring(1)
        val lower = payload.lowercase()
        // Reject multi-colon station mentions like `@station:Red:Hills`.
        // A single colon is the documented format; two or more colons
        // is almost certainly a typo (e.g. the user typed `:name:` in
        // the middle of a station name). Treating it as NAME keeps the
        // mention visible in the UI without silently sending the
        // broadcast to the wrong station.
        val stationPayload = lower.removePrefix("station:")
        return when {
            lower == "all" -> Mention(raw, Mention.Prefix.ALL, "all")
            lower.startsWith("station:") && !stationPayload.contains(':') ->
                Mention(raw, Mention.Prefix.STATION, stationPayload)
            KNOWN_DESIGNATIONS.contains(lower) -> Mention(raw, Mention.Prefix.DESIGNATION, lower)
            else -> Mention(raw, Mention.Prefix.NAME, lower)
        }
    }
    val KNOWN_DESIGNATIONS: Set<String> = setOf("inspector", "si", "asi", "sho", "hc", "head constable", "constable", "sp", "superintendent", "dig", "ig", "addl sp", "additional sp", "dsp", "asp", "sub-inspector")
    private val TRAILING_PUNCTUATION = charArrayOf(',', '.', '!', '?', ';', ':', '\n', '\r')
}
