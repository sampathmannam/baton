package com.kaavalan.note.ui.settings

import com.kaavalan.note.data.auth.SecurePreferences
import com.kaavalan.note.data.local.AppInitializer
import com.kaavalan.note.data.local.InstructionDao
import com.kaavalan.note.data.local.PersonDao
import com.kaavalan.note.data.local.TagDao
import com.kaavalan.note.data.tags.RoomTagRepository
import com.kaavalan.note.data.vault.IdentityCrypto
import com.kaavalan.note.data.vault.VaultMode
import com.kaavalan.note.data.vault.VaultModeHolder
import io.mockk.every
import io.mockk.mockk
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
        // v2.0.0 (drop Supabase): the SettingsViewModel no
        // longer takes AuthRepository, RealtimeSync, or
        // SyncEngine (no remote to sign out of, no realtime
        // to stop, no outbox to drain).
        val vm = SettingsViewModel(
            appInitializer = init,
            tagRepository = mockk<RoomTagRepository>(relaxed = true),
            personDao = mockk<PersonDao>(relaxed = true),
            instructionDao = mockk<InstructionDao>(relaxed = true),
            tagDao = mockk<TagDao>(relaxed = true),
            vaultModeHolder = vaultModeHolder,
            securePreferences = securePreferences,
            preferences = mockk<com.kaavalan.note.data.preferences.KaavalanPreferences>(relaxed = true),
            plainExporter = mockk<com.kaavalan.note.data.export.PlainExporter>(relaxed = true),
            // v2.0.1: the importer is the inverse of the
            // exporter. The vault-pin tests don't touch
            // the import path; a relaxed mock is sufficient.
            plainImporter = mockk<com.kaavalan.note.data.export.PlainImporter>(relaxed = true),
            // v2.1.0 (PM rating): the Google Drive backup
            // + OAuth client. The vault-pin tests don't
            // exercise the Drive flow; relaxed mocks.
            driveBackupManager = mockk<com.kaavalan.note.data.backup.DriveBackupManager>(relaxed = true),
            googleOAuthClient = mockk<com.kaavalan.note.data.backup.GoogleOAuthClient>(relaxed = true),
            backupManager = mockk<com.kaavalan.note.data.export.BackupManager>(relaxed = true),
            updateChecker = mockk<com.kaavalan.note.data.update.UpdateChecker>(relaxed = true),
            fixtureLoader = mockk<com.kaavalan.note.data.dev.FixtureLoader>(relaxed = true),
            // v1.8.0 (PROD-READINESS-P2-#2): the sync-conflict
            // DAO. Relaxed mock; the vault-pin tests don't touch
            // the conflict flow. The table is always empty in
            // v2.0.0 (no cloud sync), but the DAO is still in
            // the schema and the VM still observes it.
            syncConflictDao = mockk<com.kaavalan.note.data.local.SyncConflictDao>(relaxed = true),
            // v2.1.0 (PM rating): the database-health flag.
            // The vault-pin tests don't exercise the preflight
            // path; a relaxed mock is sufficient.
            databaseHealth = mockk<com.kaavalan.note.data.local.DatabaseHealth>(relaxed = true),
            appContext = mockk<android.content.Context>(relaxed = true),
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
