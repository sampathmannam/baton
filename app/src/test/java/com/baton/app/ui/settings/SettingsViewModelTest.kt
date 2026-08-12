package com.baton.app.ui.settings

import com.baton.app.data.auth.AuthRepository
import com.baton.app.data.local.AppInitializer
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.slot
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
 * M3-T4 tests for [SettingsViewModel]. The sign-out flow has two
 * strict ordering invariants: (1) the AppInitializer wipe must run
 * before the AuthRepository.signOut, otherwise the in-flight Compose
 * tree still references the encrypted DB and SQLCipher throws.
 * (2) re-entering signOut() while a previous call is in flight is a
 * no-op (the second call returns immediately).
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

    @Test
    fun `signOut wipes local DB then signs out of Supabase`() = runTest(testDispatcher) {
        val init = mockk<AppInitializer>(relaxed = true)
        val auth = mockk<AuthRepository>(relaxed = true)
        val vm = SettingsViewModel(auth, init)

        vm.signOut()
        advanceUntilIdle()

        coVerifyOrder {
            init.runOnSignOut()
            auth.signOut()
        }
    }

    @Test
    fun `signOut flipping the signing-out flag is observable`() = runTest(testDispatcher) {
        val init = mockk<AppInitializer>(relaxed = true)
        val auth = mockk<AuthRepository>(relaxed = true)
        val vm = SettingsViewModel(auth, init)

        assertFalse(vm.signingOut.value)
        vm.signOut()
        advanceUntilIdle()
        // After the work completes, the flag flips back to false so
        // the button is re-enabled — but the activity has already
        // swapped to the AuthScreen by then.
        assertFalse(vm.signingOut.value)
        coVerify(exactly = 1) { init.runOnSignOut() }
        coVerify(exactly = 1) { auth.signOut() }
    }

    @Test
    fun `second signOut while in flight is a no-op`() = runTest(testDispatcher) {
        val init = mockk<AppInitializer>(relaxed = true)
        val auth = mockk<AuthRepository>(relaxed = true)
        val vm = SettingsViewModel(auth, init)

        // Fire twice in a row before the first one completes.
        vm.signOut()
        vm.signOut()
        advanceUntilIdle()

        coVerify(exactly = 1) { init.runOnSignOut() }
        coVerify(exactly = 1) { auth.signOut() }
    }

    @Test
    fun `signOut survives a thrown AppInitializer error`() = runTest(testDispatcher) {
        val init = mockk<AppInitializer>(relaxed = true)
        val auth = mockk<AuthRepository>(relaxed = true)
        // AppInitializer wipes the DB best-effort; the VM must still
        // call auth.signOut even if the wipe throws (e.g. the file
        // is already gone). The user can still sign out.
        coEvery { init.runOnSignOut() } throws RuntimeException("file not found")
        val vm = SettingsViewModel(auth, init)

        vm.signOut()
        advanceUntilIdle()

        coVerify(exactly = 1) { auth.signOut() }
    }

    @Test
    fun `signOut survives a thrown AuthRepository error`() = runTest(testDispatcher) {
        val init = mockk<AppInitializer>(relaxed = true)
        val auth = mockk<AuthRepository>(relaxed = true)
        coEvery { auth.signOut() } throws RuntimeException("network down")
        val vm = SettingsViewModel(auth, init)

        vm.signOut()
        advanceUntilIdle()

        // The flag should reset even on error so the UI isn't stuck.
        assertFalse(vm.signingOut.value)
        coVerify(exactly = 1) { init.runOnSignOut() }
    }
}
