package com.baton.app.ui.home

import app.cash.turbine.test
import com.baton.app.data.person.Person
import com.baton.app.data.person.PersonRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val repo: PersonRepository = mockk()

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `empty state shown when repository returns no persons`() = runTest(testDispatcher) {
        coEvery { repo.observeAll() } returns emptyList()

        val vm = HomeViewModel(repo)
        advanceUntilIdle()

        vm.state.test {
            assertEquals(HomeUiState.Empty, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loaded state shown when repository returns persons`() = runTest(testDispatcher) {
        val persons = listOf(
            Person(id = "p1", name = "Ramu", designation = "SHO", station = "Bandipora", phone = null),
            Person(id = "p2", name = "Priya", designation = "DSP", station = "Srinagar", phone = null),
        )
        coEvery { repo.observeAll() } returns persons

        val vm = HomeViewModel(repo)
        advanceUntilIdle()

        vm.state.test {
            val state = awaitItem()
            assertEquals(HomeUiState.Loaded(persons), state)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
