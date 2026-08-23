package com.baton.app.ui.settings

import com.baton.app.data.auth.AuthRepository
import com.baton.app.data.auth.SecurePreferences
import com.baton.app.data.local.AppInitializer
import com.baton.app.data.local.InstructionDao
import com.baton.app.data.local.PersonDao
import com.baton.app.data.local.SyncEngine
import com.baton.app.data.local.TagDao
import com.baton.app.data.sync.RealtimeSync
import com.baton.app.data.tags.RoomTagRepository
import com.baton.app.data.vault.IdentityCrypto
import com.baton.app.data.vault.VaultMode
import com.baton.app.data.vault.VaultModeHolder
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * v2.0 T3-1 (deniable vault) unit tests for the vault-mode
 * + PIN flows on [SettingsViewModel].
 *
 * The "filter entities correctly" test in the task spec is
 * the holder test (see [VaultModeHolderTest]). This file
 * exercises the higher-level ViewModel: setting a PIN,
 * matching a PIN, and switching the vault mode through the
 * VM (which is what the Settings sheet calls).
 *
 * The [SecurePreferences] dependency is mocked: we want the
 * test to drive the VM's logic without touching the
 * EncryptedSharedPreferences (which fails under Robolectric
 * because the AndroidKeyStore is not available). The mock
 * returns `null` for `vaultPinHash()` by default and a
 * computed SHA-256 hex when `setVaultPinHash` is called.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsVaultPinTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeVm(initialPin: String? = null): Triple<SettingsViewModel, VaultModeHolder, SecurePreferences> {
        val init = mockk<AppInitializer>(relaxed = true)
        val auth = mockk<AuthRepository>(relaxed = true)
        val realtime = mockk<RealtimeSync>(relaxed = true)
        val syncEngine = mockk<SyncEngine>(relaxed = true)
        val vaultModeHolder = VaultModeHolder()
        val securePreferences = mockk<SecurePreferences>(relaxed = true)
        // The mock SecurePreferences holds the current PIN
        // hash as a mutable field so the VM's setVaultPin /
        // pinMatches calls see the latest value.
        var storedHash: String? = initialPin?.let { IdentityCrypto.sha256Hex(it) }
        every { securePreferences.vaultPinHash() } answers { storedHash }
        every { securePreferences.setVaultPinHash(any()) } answers {
            storedHash = firstArg()
        }
        every { securePreferences.clearVaultPinHash() } answers {
            storedHash = null
        }
        val vm = SettingsViewModel(
            authRepository = auth,
            appInitializer = init,
            tagRepository = mockk<RoomTagRepository>(relaxed = true),
            realtimeSync = realtime,
            syncEngine = syncEngine,
            personDao = mockk<PersonDao>(relaxed = true),
            instructionDao = mockk<InstructionDao>(relaxed = true),
            tagDao = mockk<TagDao>(relaxed = true),
            vaultModeHolder = vaultModeHolder,
            securePreferences = securePreferences,
            preferences = mockk<com.baton.app.data.preferences.BatonPreferences>(relaxed = true),
            plainExporter = mockk<com.baton.app.data.export.PlainExporter>(relaxed = true),
            backupManager = mockk<com.baton.app.data.export.BackupManager>(relaxed = true),
            appContext = mockk<android.content.Context>(relaxed = true),
            fixtureLoader = mockk<com.baton.app.data.dev.FixtureLoader>(relaxed = true),
            // v1.8.0 (PROD-READINESS-P2-#2): the sync-conflict
            // DAO. Relaxed mock; the vault-pin tests don't touch
            // the conflict flow.
            syncConflictDao = mockk<com.baton.app.data.local.SyncConflictDao>(relaxed = true),
            // v1.9.0 (PROD-READINESS-P3-P1-#3): the in-app update
            // channel. Relaxed mock.
            updateChecker = mockk<com.baton.app.data.update.UpdateChecker>(relaxed = true),
        )
        return Triple(vm, vaultModeHolder, securePreferences)
    }

    @Test
    fun `setVaultPin rejects PINs that are too short`() = runTest(testDispatcher) {
        val (vm, _, _) = makeVm()
        assertFalse(vm.setVaultPin(""))
        assertFalse(vm.setVaultPin("12"))
        assertFalse(vm.setVaultPin("123"))
    }

    @Test
    fun `setVaultPin rejects non-digit PINs`() = runTest(testDispatcher) {
        val (vm, _, _) = makeVm()
        assertFalse(vm.setVaultPin("1234a"))
        assertFalse(vm.setVaultPin("abcd"))
        assertFalse(vm.setVaultPin("12 4"))
    }

    @Test
    fun `setVaultPin accepts a valid 4-digit PIN and re-emits hasVaultPin`() = runTest(testDispatcher) {
        val (vm, _, _) = makeVm()
        assertFalse(vm.hasVaultPin.value)
        assertTrue(vm.setVaultPin("1234"))
        assertTrue(vm.hasVaultPin.value)
    }

    @Test
    fun `setVaultPin accepts a valid 6-digit PIN`() = runTest(testDispatcher) {
        val (vm, _, _) = makeVm()
        assertTrue(vm.setVaultPin("654321"))
        assertTrue(vm.hasVaultPin.value)
    }

    @Test
    fun `setVaultPin rejects a 7-digit PIN (too long)`() = runTest(testDispatcher) {
        val (vm, _, _) = makeVm()
        assertFalse(vm.setVaultPin("1234567"))
    }

    @Test
    fun `pinMatches returns true for the just-set PIN`() = runTest(testDispatcher) {
        val (vm, _, _) = makeVm()
        vm.setVaultPin("4242")
        assertTrue(vm.pinMatches("4242"))
    }

    @Test
    fun `pinMatches returns false for a wrong PIN`() = runTest(testDispatcher) {
        val (vm, _, _) = makeVm(initialPin = "4242")
        assertFalse(vm.pinMatches("1234"))
        assertFalse(vm.pinMatches(""))
    }

    @Test
    fun `pinMatches returns false when no PIN is set`() = runTest(testDispatcher) {
        val (vm, _, _) = makeVm()
        assertFalse(vm.pinMatches("1234"))
    }

    @Test
    fun `clearVaultPin flips hasVaultPin to false`() = runTest(testDispatcher) {
        val (vm, _, _) = makeVm(initialPin = "4242")
        assertTrue(vm.hasVaultPin.value)
        vm.clearVaultPin()
        assertFalse(vm.hasVaultPin.value)
    }

    @Test
    fun `setVaultMode Hidden updates the holder and the VM flow`() = runTest(testDispatcher) {
        val (vm, holder, _) = makeVm()
        assertEquals(VaultMode.Visible, vm.vaultMode.value)
        vm.setVaultMode(VaultMode.Hidden)
        assertEquals(VaultMode.Hidden, vm.vaultMode.value)
        assertEquals(VaultMode.Hidden, holder.mode.value)
    }

    @Test
    fun `setVaultMode Visible flips back through the holder`() = runTest(testDispatcher) {
        val (vm, holder, _) = makeVm()
        vm.setVaultMode(VaultMode.Hidden)
        vm.setVaultMode(VaultMode.Visible)
        assertEquals(VaultMode.Visible, vm.vaultMode.value)
        assertEquals(VaultMode.Visible, holder.mode.value)
    }
}
