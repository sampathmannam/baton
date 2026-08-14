package com.baton.app.ui.auth

import com.baton.app.data.auth.AuthRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * **Finding test for Task 7** — proves the auth ViewModel wires the
 * repository correctly. The actual Supabase Auth call is mocked; the test
 * asserts the ViewModel delegates `signIn` and `signUp` to the repository
 * with the right email and password, and transitions the UI state on
 * success/failure.
 *
 * **v1.4.5:** added tests for the passwordless OTP flow
 * ([sendOtp] / [verifyOtp]) — the user-requested replacement for
 * "type my own password" auth. The original Google sign-in ask
 * was blocked on Google Cloud setup; OTP gives the same "no
 * password" UX in 15 minutes vs 1-2 hours.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val repo: AuthRepository = mockk(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `signIn delegates to repository with email and password`() = runTest {
        coEvery { repo.signIn(any(), any()) } returns Result.success(Unit)

        val vm = AuthViewModel(repo)
        vm.signIn("sampath@example.com", "hunter2hunter2")

        coVerify { repo.signIn("sampath@example.com", "hunter2hunter2") }
    }

    @Test
    fun `signUp delegates to repository with email and password`() = runTest {
        coEvery { repo.signUp(any(), any()) } returns Result.success(Unit)

        val vm = AuthViewModel(repo)
        vm.signUp("sampath@example.com", "hunter2hunter2")

        coVerify { repo.signUp("sampath@example.com", "hunter2hunter2") }
    }

    // ----- v1.4.5: passwordless OTP flow -----

    @Test
    fun `sendOtp delegates to repository and transitions to CodeSent on success`() = runTest {
        coEvery { repo.sendOtp(any()) } returns Result.success(Unit)

        val vm = AuthViewModel(repo)
        vm.sendOtp("sampath@example.com")

        coVerify { repo.sendOtp("sampath@example.com") }
        val s = vm.state.value
        assertTrue(
            "expected CodeSent, got $s",
            s is AuthUiState.CodeSent,
        )
        assertEquals("sampath@example.com", (s as AuthUiState.CodeSent).codeEmail)
    }

    @Test
    fun `sendOtp failure transitions to OtpError preserving the email`() = runTest {
        coEvery { repo.sendOtp(any()) } returns Result.failure(RuntimeException("Email OTP not enabled"))

        val vm = AuthViewModel(repo)
        vm.sendOtp("sampath@example.com")

        val s = vm.state.value
        assertTrue(
            "expected OtpError, got $s",
            s is AuthUiState.OtpError,
        )
        s as AuthUiState.OtpError
        assertEquals("sampath@example.com", s.codeEmail)
        // SafeError.forUser maps unknown exceptions to a safe default,
        // not the raw "Email OTP not enabled" message.
        assertTrue(
            "expected safe error message, got: ${s.message}",
            s.message.contains("code", ignoreCase = true) ||
                s.message.contains("try again", ignoreCase = true),
        )
    }

    @Test
    fun `verifyOtp delegates to repository and transitions to SignedIn on success`() = runTest {
        coEvery { repo.verifyOtp(any(), any()) } returns Result.success(Unit)

        val vm = AuthViewModel(repo)
        vm.verifyOtp("sampath@example.com", "123456")

        coVerify { repo.verifyOtp("sampath@example.com", "123456") }
        assertEquals(AuthUiState.SignedIn, vm.state.value)
    }

    @Test
    fun `verifyOtp failure stays on OtpError so user can retry without resending`() = runTest {
        coEvery { repo.verifyOtp(any(), any()) } returns Result.failure(RuntimeException("invalid token"))

        val vm = AuthViewModel(repo)
        vm.verifyOtp("sampath@example.com", "000000")

        val s = vm.state.value
        assertTrue(
            "expected OtpError (not Idle), got $s",
            s is AuthUiState.OtpError,
        )
        // Email must be preserved so the screen can re-render the OTP panel.
        assertEquals("sampath@example.com", (s as AuthUiState.OtpError).codeEmail)
    }

    @Test
    fun `reset returns the state machine to Idle so the user can switch paths`() = runTest {
        // Drive the VM into CodeSent first.
        coEvery { repo.sendOtp(any()) } returns Result.success(Unit)
        val vm = AuthViewModel(repo)
        vm.sendOtp("sampath@example.com")
        assertTrue(vm.state.value is AuthUiState.CodeSent)

        vm.reset()
        assertEquals(AuthUiState.Idle, vm.state.value)
    }
}
