package com.baton.app.data.audit

import androidx.test.core.app.ApplicationProvider
import com.baton.app.data.local.AuditChainEventDao
import com.baton.app.data.local.AppDatabase
import com.baton.app.data.local.entities.AuditChainEventEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v1.8.0 (PROD-READINESS-P2-#4): the audit-chain
 * writer + verifier round-trip test. Uses a real
 * Robolectric Room DB so the SHA-256 chain hash is
 * end-to-end (writer -> insert -> read -> verify).
 *
 * What we assert:
 *  1. The genesis row's prevHash is the all-zeros
 *     sentinel and its thisHash is the SHA-256 of
 *     `payload || GENESIS_HASH || signingKey`.
 *  2. The second row's prevHash is the first row's
 *     thisHash (the chain is anchored).
 *  3. The verifier reports [VerifyResult.Intact] on
 *     a clean chain.
 *  4. The verifier reports [VerifyResult.BrokenAt]
 *     when a middle row's payload is edited.
 *  5. computeHash() is stable across runs (no
 *     random salt).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AuditChainWriterTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: AuditChainEventDao
    private lateinit var writer: AuditChainWriter
    private lateinit var verifier: AuditChainVerifier

    private val signingKey = "test-signing-key"

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = androidx.room.Room.inMemoryDatabaseBuilder(
            context, AppDatabase::class.java,
        ).allowMainThreadQueries()
            // v1.8.0: the in-memory test builder does
            // not run the production migrations; we
            // want a fresh schema for the chain so the
            // test is hermetic. v14 with the
            // audit_chain_events table.
            .addMigrations(AppDatabase.MIGRATION_13_14)
            .build()
        dao = db.auditChainEventDao()
        writer = AuditChainWriter(dao) { signingKey }
        verifier = AuditChainVerifier(dao)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `append first event uses GENESIS_HASH as prevHash`() = runTest {
        writer.append(tableName = "persons", rowId = "p1", kind = "INSERT", payload = "{\"name\":\"DSP\"}")
        val row = dao.latest()!!
        assertEquals(AuditChainEventEntity.GENESIS_HASH, row.prevHash)
        // Expected thisHash = SHA-256(payload || GENESIS_HASH || signingKey)
        val expected = AuditChainWriter.computeHash(
            payload = "{\"name\":\"DSP\"}",
            prevHash = AuditChainEventEntity.GENESIS_HASH,
            signingKey = signingKey,
        )
        assertEquals(expected, row.thisHash)
    }

    @Test
    fun `second event chains from the first events thisHash`() = runTest {
        writer.append("persons", "p1", "INSERT", "{\"a\":1}")
        val first = dao.latest()!!
        writer.append("persons", "p1", "UPDATE", "{\"a\":2}")
        val second = dao.latest()!!
        assertEquals(first.thisHash, second.prevHash)
    }

    @Test
    fun `verifier reports Intact on a clean chain`() = runTest {
        writer.append("persons", "p1", "INSERT", "{}")
        writer.append("persons", "p1", "UPDATE", "{\"name\":\"X\"}")
        writer.append("persons", "p2", "INSERT", "{}")
        val result = verifier.verify()
        assertTrue("expected Intact, got $result", result is VerifyResult.Intact)
        assertEquals(3, (result as VerifyResult.Intact).eventCount)
    }

    @Test
    fun `verifier reports BrokenAt when a middle row is edited`() = runTest {
        writer.append("persons", "p1", "INSERT", "{\"a\":1}")
        writer.append("persons", "p1", "UPDATE", "{\"a\":2}")
        writer.append("persons", "p2", "INSERT", "{\"a\":3}")
        // Tamper: edit the middle row's payload.
        val rows = dao.snapshot()
        val middle = rows[1]
        val tampered = middle.copy(payload = "{\"a\":999}")
        // Direct write bypassing the writer (simulates
        // a forensic adversary with SQL access).
        db.clearAllTables()
        // Re-insert everything with the tampered middle row.
        dao.insert(rows[0].copy(id = 0))
        dao.insert(tampered.copy(id = 0))
        dao.insert(rows[2].copy(id = 0))
        // After clear+insert the IDs are renumbered;
        // the chain is broken at the second row.
        val result = verifier.verify()
        assertTrue("expected BrokenAt, got $result", result is VerifyResult.BrokenAt)
    }

    @Test
    fun `computeHash is stable across runs`() {
        val h1 = AuditChainWriter.computeHash("payload", "prev", "key")
        val h2 = AuditChainWriter.computeHash("payload", "prev", "key")
        assertEquals(h1, h2)
        // 64-char lowercase hex = SHA-256
        assertEquals(64, h1.length)
        assertTrue("expected lowercase hex", h1.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `computeHash changes when any input changes`() {
        val a = AuditChainWriter.computeHash("payload", "prev", "key")
        val b = AuditChainWriter.computeHash("payload2", "prev", "key")
        val c = AuditChainWriter.computeHash("payload", "prev2", "key")
        val d = AuditChainWriter.computeHash("payload", "prev", "key2")
        assertNotEquals(a, b)
        assertNotEquals(a, c)
        assertNotEquals(a, d)
    }
}
