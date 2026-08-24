package com.baton.app.data.instructions
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
class HeaderTemplateTest {
    @Test fun `wrap includes sender and due and ref`() { val due = LocalDateTime.of(2026, 8, 24, 9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(); val out = HeaderTemplate.wrap(body = "Please review the FIR.", inputs = HeaderTemplate.Inputs(senderName = "Sampath", senderDesignation = "SP", senderDivision = "North", dueAtMs = due, shortRef = HeaderTemplate.shortRefFor("abc-1234-5678-9def"))); assertTrue(out.contains("From: Sampath, SP, North")); assertTrue(out.contains("Due: ")); assertTrue(out.contains("Ref: Kaavalan #")); assertTrue(out.contains("Please review the FIR.")); assertTrue(out.contains("\u2014 Kaavalan")) }
    @Test fun `wrap omits blank designation and division`() { val out = HeaderTemplate.wrap(body = "Brief me.", inputs = HeaderTemplate.Inputs(senderName = "Anu", senderDesignation = null, senderDivision = null, dueAtMs = null, shortRef = "XYZ")); assertTrue(out.contains("From: Anu")); assertTrue(!out.contains("null")); assertTrue(!out.contains("Due:")) }
}
