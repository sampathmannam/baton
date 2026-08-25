package com.kaavalan.note.data.instructions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
class AudienceRefTest {
    @Test fun `kind maps to canonical string`() { assertEquals("PERSON", AudienceRef.ByPerson("p", "P").kind); assertEquals("DESIGNATION", AudienceRef.ByDesignation("si", "SI").kind); assertEquals("STATION", AudienceRef.ByStation("RedHills", "RedHills").kind); assertEquals("ALL", AudienceRef.ByAll("all", "all").kind) }
    @Test fun `target maps to payload`() { assertEquals("p", AudienceRef.ByPerson("p", "P").target); assertEquals("si", AudienceRef.ByDesignation("si", "SI").target); assertEquals("RedHills", AudienceRef.ByStation("RedHills", "RedHills").target); assertEquals("all", AudienceRef.ByAll("all", "all").target) }
    @Test fun `isBroadcast is true except for ByPerson`() { assertFalse(AudienceRef.ByPerson("p", "P").isBroadcast); assertTrue(AudienceRef.ByDesignation("si", "SI").isBroadcast); assertTrue(AudienceRef.ByStation("X", "X").isBroadcast); assertTrue(AudienceRef.ByAll("a", "a").isBroadcast) }
    @Test fun `toLabel returns the human label`() { assertEquals("Inspector", AudienceRef.ByDesignation("inspector", "Inspector").toLabel()) }
    @Test fun `audienceFromColumns round-trips`() { val a = audienceFromColumns("DESIGNATION", "si", "SI"); assertEquals(AudienceRef.ByDesignation("si", "SI"), a) }
    @Test fun `audienceFromColumns null returns null`() { assertNull(audienceFromColumns(null, null, null)); assertNull(audienceFromColumns("PERSON", null, "P")) }
}
