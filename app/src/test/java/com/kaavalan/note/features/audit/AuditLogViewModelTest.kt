package com.kaavalan.note.features.audit

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kaavalan.note.data.audit.AuditChainVerifier
import com.kaavalan.note.data.audit.AuditChainWriter
import com.kaavalan.note.data.local.AppDatabase
import com.kaavalan.note.data.local.AuditChainEventDao
import com.kaavalan.note.data.local.entities.AuditChainEventEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v2.0 (PM rating): the in-app audit-log ViewModel. The
 * audit chain has been writing rows since v1.8.0; this
 * test pins the v2.0 surface (events flow + verify()
 * contract).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AuditLogViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var db: AppDatabase
    private lateinit var dao: AuditChainEventDao

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        // v2.0 (test isolation): bind the Room query +
        // transaction executors to the test dispatcher so
        // the verify() coroutine's DAO calls run on the
        // same scheduler as viewModelScope. Without this,
        // the verify() coroutine suspends on a real
        // executor and the assertion sees Running.
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(testDispatcher.asExecutor())
            .setTransactionExecutor(testDispatcher.asExecutor())
            .build()
        dao = db.auditChainEventDao()
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `empty chain verifies as Intact with 0 events`() = runTest {
        val verifier = AuditChainVerifier(dao)
        val vm = AuditLogViewModel(dao, verifier)
        advanceUntilIdle()
        assertEquals(emptyList<AuditChainEventEntity>(), vm.events.value)

        vm.verify()
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue("expected Intact, got $state", state is AuditLogViewModel.VerifyState.Intact)
        assertEquals(0, (state as AuditLogViewModel.VerifyState.Intact).eventCount)
    }

    @Test
    fun `writer + verifier see the same Intact chain`() = runTest {
        // The writer is the canonical producer of audit rows;
        // using it in the test mirrors what production does.
        val writer = AuditChainWriter(dao, signingKeyProvider = { "test-signing-key" })
        val verifier = AuditChainVerifier(dao)
        val vm = AuditLogViewModel(dao, verifier)
        advanceUntilIdle()

        writer.append(tableName = "persons", rowId = "p1", kind = "INSERT", payload = """{"name":"Ramu"}""")
        writer.append(tableName = "persons", rowId = "p1", kind = "UPDATE", payload = """{"name":"Ramu Reddy"}""")
        writer.append(tableName = "instructions", rowId = "i1", kind = "INSERT", payload = """{"title":"Buy milk"}""")
        advanceUntilIdle()

        // The VM observes 3 events.
        val events = vm.events.first()
        assertEquals(3, events.size)

        // The chain verifies as Intact.
        vm.verify()
        advanceUntilIdle()
        val state = vm.state.value
        assertTrue("expected Intact, got $state", state is AuditLogViewModel.VerifyState.Intact)
        assertEquals(3, (state as AuditLogViewModel.VerifyState.Intact).eventCount)
    }

    @Test
    fun `verify reports BrokenAt when a row has a bad thisHash`() = runTest {
        val writer = AuditChainWriter(dao, signingKeyProvider = { "test-signing-key" })
        val verifier = AuditChainVerifier(dao)
        val vm = AuditLogViewModel(dao, verifier)
        advanceUntilIdle()

        writer.append(tableName = "persons", rowId = "p1", kind = "INSERT", payload = """{"name":"Ramu"}""")
        writer.append(tableName = "persons", rowId = "p1", kind = "UPDATE", payload = """{"name":"Ramu Reddy"}""")
        writer.append(tableName = "instructions", rowId = "i1", kind = "INSERT", payload = """{"title":"Buy milk"}""")
        advanceUntilIdle()

        // Simulate a tamper: insert a 4th row directly via
        // the DAO with a thisHash that does NOT match
        // SHA-256(payload || prevHash || signingKey). The
        // verifier walks the chain in id-ASC order and
        // reports the first mismatch. (We can't easily
        // mutate an existing row in this DAO because
        // there's no @Update method, and a direct UPDATE
        // via the Room supportSQLiteOpenHelper would
        // skip the test's logical contract. Inserting a
        // bad row exercises the same verifier path.)
        val previous = dao.snapshot().last()
        val tampered = AuditChainEventEntity(
            id = 0,  // autoGenerate
            tableName = "persons",
            rowId = "p1",
            kind = "UPDATE",
            payload = """{"name":"TAMPERED"}""",
            signingKey = "test-signing-key",
            createdAtMs = System.currentTimeMillis() + 1000,
            prevHash = previous.thisHash,
            thisHash = "0000000000000000000000000000000000000000000000000000000000000000",  // bogus
        )
        dao.insert(tampered)
        advanceUntilIdle()

        vm.verify()
        advanceUntilIdle()
        val state = vm.state.value
        assertTrue("expected Broken, got $state", state is AuditLogViewModel.VerifyState.Broken)
        val broken = (state as AuditLogViewModel.VerifyState.Broken).result
        assertEquals(3, broken.index)
        assertEquals("persons", broken.tableName)
        assertEquals("p1", broken.rowPk)
    }
}
