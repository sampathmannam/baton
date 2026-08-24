package com.baton.app.data.instructions

import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class HeaderTemplateTest {
    @Test fun `wrap includes sender and due and ref`() { val due = LocalDateTime.of(2026, 8, 24, 9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(); val out = HeaderTemplate.wrap(body = "Please review the FIR.", inputs = HeaderTemplate.Inputs(senderName = "Sampath", senderDesignation = "SP", senderDivision = "North", dueAtMs = due, shortRef = HeaderTemplate.shortRefFor("abc-1234-5678-9def"))); assertTrue("from", out.contains("From: Sampath, SP, North")); assertTrue("due", out.contains("Due: ")); assertTrue("ref", out.contains("Ref: Kaavalan #")); assertTrue("body", out.contains("Please review the FIR.")); assertTrue("sig", out.contains("\u2014 Kaavalan")) }
    @Test fun `wrap omits blank designation and division`() { val out = HeaderTemplate.wrap(body = "Brief me.", inputs = HeaderTemplate.Inputs(senderName = "Anu", senderDesignation = null, senderDivision = null, dueAtMs = null, shortRef = "XYZ")); assertTrue("from", out.contains("From: Anu")); assertTrue("no null", !out.contains("null")); assertTrue("no due", !out.contains("Due:")) }
}
