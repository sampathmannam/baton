package com.kaavalan.note.data.instructions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
class MentionAndTagParserTest {
    @Test fun `empty body returns empty result`() { val r = MentionAndTagParser.parse(""); assertEquals("", r.body); assertTrue(r.tokens.isEmpty()); assertTrue(r.mentions.isEmpty()); assertTrue(r.hashtags.isEmpty()) }
    @Test fun `single at-name mention`() { val r = MentionAndTagParser.parse("Call @ramesh about the file"); assertEquals(1, r.mentions.size); assertEquals("ramesh", r.mentions[0].payload); assertEquals(MentionAndTagParser.Mention.Prefix.NAME, r.mentions[0].prefix) }
    @Test fun `designation mention is classified`() { val r = MentionAndTagParser.parse("Brief @si on the new FIR format"); assertEquals(1, r.mentions.size); assertEquals("si", r.mentions[0].payload); assertEquals(MentionAndTagParser.Mention.Prefix.DESIGNATION, r.mentions[0].prefix) }
    @Test fun `station mention is classified`() { val r = MentionAndTagParser.parse("Forward to @station:RedHills"); assertEquals(1, r.mentions.size); assertEquals("redhills", r.mentions[0].payload); assertEquals(MentionAndTagParser.Mention.Prefix.STATION, r.mentions[0].prefix) }
    @Test fun `at-all broadcast mention`() { val r = MentionAndTagParser.parse("Roll call for @all at 0900"); assertEquals(1, r.mentions.size); assertEquals("all", r.mentions[0].payload); assertEquals(MentionAndTagParser.Mention.Prefix.ALL, r.mentions[0].prefix) }
    @Test fun `hashtags are extracted lowercase`() { val r = MentionAndTagParser.parse("Please #BudgetReview and #FIR-2026 today"); assertEquals(2, r.hashtags.size); assertEquals("budgetreview", r.hashtags[0]); assertEquals("fir-2026", r.hashtags[1]) }
    @Test fun `email is not treated as a mention`() { val r = MentionAndTagParser.parse("Email user@example.com about the case"); assertTrue(r.mentions.isEmpty()) }
}
