package com.baton.app.ui.settings

import com.baton.app.data.auth.SecurePreferences
import com.baton.app.data.dev.FixtureLoader
import com.baton.app.data.export.PlainExporter
import com.baton.app.data.local.AppInitializer
import com.baton.app.data.local.InstructionDao
import com.baton.app.data.local.PersonDao
import com.baton.app.data.local.SyncConflictDao
import com.baton.app.data.local.TagDao
import com.baton.app.data.preferences.BatonPreferences
import com.baton.app.data.tags.RoomTagRepository
import com.baton.app.data.vault.VaultModeHolder
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * v2.0.0 (drop Supabase): the sign-out flow is local-only. The
 * SettingsViewModel no longer takes an AuthRepository (no remote
 * to sign out of), a RealtimeSync (no realtime to stop), or a
 * SyncEngine (no outbox to drain). The local-only "sign out" is a
 * `runCatching { appInitializer.runOnSignOut() }` which wipes the
 * SQLCipher-encrypted DB and clears the passphrase.
 *
 * The behavioural invariants that survive from M3-T4 / v1.2:
 *
 *  1. The `_signingOut` flag flips to `true` on the first call.
 *  2. A second `signOut()` while the first is in flight is a
 *     no-op (the flag guard at the top of the function).
 *  3. A thrown [AppInitializer.runOnSignOut] error is swallowed
 *     (the runCatching wrapper) so the caller doesn't crash.
 *
 * `retryStuckOutbox` is a no-op in v2.0.0 (no sync engine). It's
 * pinned here as a no-op contract.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun mockVm(): Pair<AppInitializer, SettingsViewModel> {
        val init = mockk<AppInitializer>(relaxed = true)
        val appContext = mockk<android.content.Context>(relaxed = true)
        val vm = SettingsViewModel(
            appInitializer = init,
            tagRepository = mockk<RoomTagRepository>(relaxed = true),
            personDao = mockk<PersonDao>(relaxed = true),
            instructionDao = mockk<InstructionDao>(relaxed = true),
            tagDao = mockk<TagDao>(relaxed = true),
            vaultModeHolder = mockk<VaultModeHolder>(relaxed = true),
            securePreferences = mockk<SecurePreferences>(relaxed = true),
            preferences = mockk<BatonPreferences>(relaxed = true),
            plainExporter = mockk<PlainExporter>(relaxed = true),
            backupManager = mockk<com.baton.app.data.export.BackupManager>(relaxed = true),
            updateChecker = mockk<com.baton.app.data.update.UpdateChecker>(relaxed = true),
            fixtureLoader = mockk<FixtureLoader>(relaxed = true),
            // v1.8.0 (PROD-READINESS-P2-#2): the sync-conflict
            // DAO. The table is always empty in v2.0.0 (no
            // cloud sync), but the DAO is still in the schema
            // and the VM still observes it. Relaxed mock.
            syncConflictDao = mockk<SyncConflictDao>(relaxed = true),
            appContext = appContext,
        )
        return init to vm
    }

    @Test
    fun `signOut calls runOnSignOut to wipe the local DB`() = runTest(testDispatcher) {
        val (init, vm) = mockVm()

        vm.signOut()
        advanceUntilIdle()

        coVerify(exactly = 1) { init.runOnSignOut() }
    }

    @Test
    fun `signOut flipping the signing-out flag is observable`() = runTest(testDispatcher) {
        val (_, vm) = mockVm()

        assertFalse(vm.signingOut.value)
        vm.signOut()
        // v1.2 BUG-AUTH-023: the flag stays true after the work
        // completes; the local DB is wiped and the activity is
        // torn down. The button must NOT flicker back to enabled.
        assertTrue(vm.signingOut.value)
        advanceUntilIdle()
    }

    @Test
    fun `second signOut while in flight is a no-op`() = runTest(testDispatcher) {
        val (init, vm) = mockVm()

        // Fire twice in a row before the first one completes.
        vm.signOut()
        vm.signOut()
        advanceUntilIdle()

        coVerify(exactly = 1) { init.runOnSignOut() }
    }

    @Test
    fun `signOut survives a thrown AppInitializer error`() = runTest(testDispatcher) {
        val (init, vm) = mockVm()
        // AppInitializer wipes the DB best-effort; the VM must
        // not propagate the exception. (v2.0.0: there's no
        // downstream `auth.signOut()` to call — the local
        // wipe is the entire sign-out path.)
        coEvery { init.runOnSignOut() } throws RuntimeException("file not found")

        vm.signOut()
        advanceUntilIdle()

        // The exception is swallowed — the test passes if the
        // runTest block returns without re-throwing.
        coVerify(exactly = 1) { init.runOnSignOut() }
        assertTrue(vm.signingOut.value)
    }

    @Test
    fun `retryStuckOutbox is a no-op in v2_0_0 (no sync engine)`() = runTest(testDispatcher) {
        val (_, vm) = mockVm()

        // v2.0.0: the sync_queue table is in the schema for
        // forward-compat but no rows are written to it and the
        // SyncEngine is gone. The retryStuckOutbox action on
        // the Settings sheet is a no-op; this test pins the
        // contract so a future v2.x cloud-sync re-enable knows
        // to look here.
        vm.retryStuckOutbox()
        advanceUntilIdle()

        // No exceptions thrown. The flag stays false (no work
        // was started).
        assertFalse(vm.signingOut.value)
    }
}
