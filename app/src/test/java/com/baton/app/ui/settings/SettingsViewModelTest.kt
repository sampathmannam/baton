package com.baton.app.ui.settings

import com.baton.app.data.auth.AuthRepository
import com.baton.app.data.local.AppInitializer
import com.baton.app.data.local.InstructionDao
import com.baton.app.data.local.PersonDao
import com.baton.app.data.local.SyncEngine
import com.baton.app.data.local.TagDao
import com.baton.app.data.sync.RealtimeSync
import com.baton.app.data.tags.RoomTagRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
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
 * M3-T4 tests for [SettingsViewModel]. The sign-out flow has three
 * strict ordering invariants: (1) [RealtimeSync.stop] must run
 * BEFORE [AppInitializer.runOnSignOut] so the previous user's JWT
 * is not on the WebSocket when the activity is torn down (see
 * BUG-AUTH-002 / BATON-WIRE-002 in the v1.2 audit). (2) the
 * AppInitializer wipe must run before the AuthRepository.signOut,
 * otherwise the in-flight Compose tree still references the
 * encrypted DB and SQLCipher throws. (3) re-entering signOut()
 * while a previous call is in flight is a no-op (the second call
 * returns immediately).
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

    private data class VmMocks(
        val init: AppInitializer,
        val auth: AuthRepository,
        val realtime: RealtimeSync,
        val syncEngine: SyncEngine,
        val vm: SettingsViewModel,
    )

    private fun mockVm(): VmMocks {
        val init = mockk<AppInitializer>(relaxed = true)
        val auth = mockk<AuthRepository>(relaxed = true)
        val realtime = mockk<RealtimeSync>(relaxed = true)
        val syncEngine = mockk<SyncEngine>(relaxed = true)
        // v1.5.4: relaxed mocks for the two model managers
        // the Settings → Models section now reads. The unit
        // tests below don't drive the model lifecycle — they
        // exercise signOut + tag management — so a relaxed
        // mock is sufficient and keeps the existing test
        // surface untouched.
        val modelManager = mockk<com.baton.app.ai.llama.ModelManager>(relaxed = true)
        val whisperManager = mockk<com.baton.app.ai.whisper.WhisperModelManager>(relaxed = true)
        // v2.0 T3-1: a real [com.baton.app.data.vault.VaultModeHolder]
        // singleton + a relaxed [com.baton.app.data.auth.SecurePreferences]
        // so the constructor compiles. The PIN / vault-mode
        // tests live in `SettingsVaultModeTest.kt` and own
        // their own VM instance.
        val vaultModeHolder = com.baton.app.data.vault.VaultModeHolder()
        val securePreferences = mockk<com.baton.app.data.auth.SecurePreferences>(relaxed = true)
        val vm = SettingsViewModel(
            authRepository = auth,
            appInitializer = init,
            tagRepository = mockk<RoomTagRepository>(relaxed = true),
            realtimeSync = realtime,
            syncEngine = syncEngine,
            personDao = mockk<PersonDao>(relaxed = true),
            instructionDao = mockk<InstructionDao>(relaxed = true),
            tagDao = mockk<TagDao>(relaxed = true),
            modelManager = modelManager,
            whisperModelManager = whisperManager,
            vaultModeHolder = vaultModeHolder,
            securePreferences = securePreferences,
        )
        return VmMocks(init, auth, realtime, syncEngine, vm)
    }

    @Test
    fun `signOut closes realtime then wipes local DB then signs out of Supabase`() = runTest(testDispatcher) {
        val (init, auth, realtime, syncEngine, vm) = mockVm()

        vm.signOut()
        advanceUntilIdle()

        coVerifyOrder {
            // v1.2: Realtime MUST be closed first so the previous
            // user's JWT is not on the WebSocket when the activity
            // is torn down.
            realtime.stop()
            init.runOnSignOut()
            auth.signOut()
        }
    }

    @Test
    fun `signOut flipping the signing-out flag is observable`() = runTest(testDispatcher) {
        val (_, _, _, _, vm) = mockVm()

        assertFalse(vm.signingOut.value)
        vm.signOut()
        // v1.2 BUG-AUTH-023: the flag stays true after the work
        // completes; the session observer tears down the activity.
        // The button must NOT flicker back to enabled.
        assertTrue(vm.signingOut.value)
        advanceUntilIdle()
    }

    @Test
    fun `second signOut while in flight is a no-op`() = runTest(testDispatcher) {
        val (init, auth, _, _, vm) = mockVm()

        // Fire twice in a row before the first one completes.
        vm.signOut()
        vm.signOut()
        advanceUntilIdle()

        coVerify(exactly = 1) { init.runOnSignOut() }
        coVerify(exactly = 1) { auth.signOut() }
    }

    @Test
    fun `signOut survives a thrown AppInitializer error`() = runTest(testDispatcher) {
        val (init, auth, _, _, vm) = mockVm()
        // AppInitializer wipes the DB best-effort; the VM must still
        // call auth.signOut even if the wipe throws (e.g. the file
        // is already gone). The user can still sign out.
        coEvery { init.runOnSignOut() } throws RuntimeException("file not found")

        vm.signOut()
        advanceUntilIdle()

        coVerify(exactly = 1) { auth.signOut() }
    }

    @Test
    fun `signOut survives a thrown AuthRepository error`() = runTest(testDispatcher) {
        val (init, auth, _, _, vm) = mockVm()
        coEvery { auth.signOut() } returns Result.failure(RuntimeException("network down"))

        vm.signOut()
        advanceUntilIdle()

        // v1.2 BUG-AUTH-023: the flag stays true on error too; the
        // local DB is wiped so the user is signed out client-side.
        // AuthRepository.signOut() now returns Result<Unit>, not
        // throws, so the failure path is exercised via the return value.
        assertTrue(vm.signingOut.value)
        coVerify(exactly = 1) { init.runOnSignOut() }
        coVerify(exactly = 1) { auth.signOut() }
    }

    @Test
    fun `retryStuckOutbox calls syncEngine retryPermanentlyFailed`() = runTest(testDispatcher) {
        val (_, _, _, syncEngine, vm) = mockVm()
        coEvery { syncEngine.retryPermanentlyFailed() } returns 3

        vm.retryStuckOutbox()
        advanceUntilIdle()

        coVerify(exactly = 1) { syncEngine.retryPermanentlyFailed() }
    }

    @Test
    fun `retryStuckOutbox swallows a thrown syncEngine error`() = runTest(testDispatcher) {
        val (_, _, _, syncEngine, vm) = mockVm()
        coEvery { syncEngine.retryPermanentlyFailed() } throws RuntimeException("db locked")

        // The VM runCatches — the exception must not propagate.
        vm.retryStuckOutbox()
        advanceUntilIdle()

        coVerify(exactly = 1) { syncEngine.retryPermanentlyFailed() }
    }
}
