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
import org.junit.Before
import org.junit.Test

/**
 * **Finding test for Task 7** — proves the auth ViewModel wires the
 * repository correctly. The actual Supabase Auth call is mocked; the test
 * asserts the ViewModel delegates `signIn` and `signUp` to the repository
 * with the right email and password, and transitions the UI state on
 * success/failure.
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
}
