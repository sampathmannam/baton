package com.kaavalan.note.features.vault

import android.net.Uri
import com.kaavalan.note.data.vault.PassphraseStrength
import com.kaavalan.note.data.vault.VaultError
import com.kaavalan.note.data.vault.VaultExporter
import com.kaavalan.note.data.vault.VaultImporter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tier 1.1 (v2.0): the VaultViewModel. We exercise the
 * happy / error / wrong-passphrase paths through a
 * mocked [VaultExporter] / [VaultImporter].
 *
 * Runs under Robolectric so the [Uri] parser is available
 * (the test was crashing with "Method parse in android.net.Uri
 * not mocked" in a JVM-only JUnit runner).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VaultViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val exporter = mockk<VaultExporter>(relaxed = true)
    private val importer = mockk<VaultImporter>(relaxed = true)
    private val strength = PassphraseStrength()

    @Before
    fun setUp() { Dispatchers.setMain(testDispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `export with too-short passphrase surfaces TooShort`() = runTest {
        val vm = VaultViewModel(exporter, importer, strength)
        vm.setPassphrase("short")
        vm.setConfirm("short")
        vm.export(Uri.parse("file:///tmp/out/kaavalan-note-vault")) {}
        advanceUntilIdle()
        assertEquals(VaultUiError.TooShort, vm.state.value.error)
    }

    @Test
    fun `export with mismatched confirm surfaces Mismatch`() = runTest {
        val vm = VaultViewModel(exporter, importer, strength)
        vm.setPassphrase("longenoughpass")
        vm.setConfirm("differentpass")
        vm.export(Uri.parse("file:///tmp/out/kaavalan-note-vault")) {}
        advanceUntilIdle()
        assertEquals(VaultUiError.Mismatch, vm.state.value.error)
    }

    @Test
    fun `export happy path calls exporter and flips finished`() = runTest {
        coEvery { exporter.export(any(), any()) } returns Result.success(Unit)
        val vm = VaultViewModel(exporter, importer, strength)
        vm.setPassphrase("longenoughpass")
        vm.setConfirm("longenoughpass")
        var called = false
        vm.export(Uri.parse("file:///tmp/out/kaavalan-note-vault")) { called = true }
        advanceUntilIdle()
        coVerify(exactly = 1) { exporter.export(any(), any()) }
        assertTrue("onSuccess should be called", called)
        assertEquals(true, vm.state.value.finished)
        assertNull(vm.state.value.error)
    }

    @Test
    fun `import with wrong passphrase surfaces IncorrectPassphrase`() = runTest {
        coEvery { importer.import(any(), any()) } returns Result.failure(VaultError.IncorrectPassphrase())
        val vm = VaultViewModel(exporter, importer, strength)
        vm.setPassphrase("whatever")
        vm.import(Uri.parse("file:///tmp/in/kaavalan-note-vault")) {}
        advanceUntilIdle()
        assertEquals(VaultUiError.IncorrectPassphrase, vm.state.value.error)
    }

    @Test
    fun `import with non-vault file surfaces NotAVault`() = runTest {
        coEvery { importer.import(any(), any()) } returns Result.failure(VaultError.NotAVault("bad magic"))
        val vm = VaultViewModel(exporter, importer, strength)
        vm.setPassphrase("whatever")
        vm.import(Uri.parse("file:///tmp/in/kaavalan-note-vault")) {}
        advanceUntilIdle()
        assertEquals(VaultUiError.NotAVault, vm.state.value.error)
    }

    @Test
    fun `setPassphrase and setConfirm update state`() = runTest {
        val vm = VaultViewModel(exporter, importer, strength)
        vm.setPassphrase("hello")
        assertEquals("hello", vm.state.value.passphrase)
        vm.setConfirm("hello")
        assertEquals("hello", vm.state.value.confirm)
    }

    @Test
    fun `clear resets the state`() = runTest {
        val vm = VaultViewModel(exporter, importer, strength)
        vm.setPassphrase("hello")
        vm.clear()
        assertEquals("", vm.state.value.passphrase)
        assertEquals("", vm.state.value.confirm)
        assertNull(vm.state.value.error)
    }

    @Test
    fun `finished is set after a successful import`() = runTest {
        coEvery { importer.import(any(), any()) } returns Result.success(Unit)
        val vm = VaultViewModel(exporter, importer, strength)
        vm.setPassphrase("any")
        var called = false
        vm.import(Uri.parse("file:///tmp/in/kaavalan-note-vault")) { called = true }
        advanceUntilIdle()
        assertNotNull(vm.state.value)
        assertTrue("onSuccess should be called", called)
        assertTrue(vm.state.value.finished)
    }
}
