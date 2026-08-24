package com.kaavalan.note.data.retention

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v1.8.0 (PROD-READINESS-P2-#5): the retention-policy
 * test. Asserts the BNSS / state IT Act defaults are
 * correct and the cutoff computation is right.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RetentionPolicyTest {

    @Test
    fun `defaults are BNSS-aligned`() {
        val p = RetentionPolicy.DEFAULT
        assertEquals(7, p.auditChainEventsYears)
        assertEquals(3, p.capturesYears)
        assertEquals(3, p.importantDatesYears)
        assertEquals(7, p.instructionsYears)
    }

    @Test
    fun `redactBeforeMs computes the right cutoffs for a known now`() {
        val p = RetentionPolicy.DEFAULT
        val now = 1_000_000_000_000L  // arbitrary epoch ms
        val yearMs = RetentionPolicy.MS_PER_YEAR
        assertEquals(
            now - 3 * yearMs,
            p.redactBeforeMs(RetentionTable.CAPTURES, now),
        )
        assertEquals(
            now - 7 * yearMs,
            p.redactBeforeMs(RetentionTable.AUDIT_CHAIN_EVENTS, now),
        )
    }

    @Test
    fun `MS_PER_YEAR is the right constant`() {
        // 365 days * 24 * 60 * 60 * 1000
        val expected = 365L * 24L * 60L * 60L * 1000L
        assertEquals(expected, RetentionPolicy.MS_PER_YEAR)
    }

    @Test
    fun `different categories produce different cutoffs`() {
        val p = RetentionPolicy.DEFAULT
        val now = 1_000_000_000_000L
        val cap = p.redactBeforeMs(RetentionTable.CAPTURES, now)
        val aud = p.redactBeforeMs(RetentionTable.AUDIT_CHAIN_EVENTS, now)
        val dat = p.redactBeforeMs(RetentionTable.IMPORTANT_DATES, now)
        val ins = p.redactBeforeMs(RetentionTable.INSTRUCTIONS, now)
        // CAPTURES (3y) and IMPORTANT_DATES (3y) share
        // the same cutoff; AUDIT (7y) and INSTRUCTIONS
        // (7y) share theirs. So cap == dat, ins == aud,
        // and cap != ins.
        assertEquals(cap, dat)
        assertEquals(ins, aud)
        assertNotEquals(cap, ins)
    }
}
