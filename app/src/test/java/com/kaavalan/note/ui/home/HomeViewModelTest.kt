package com.kaavalan.note.ui.home

import com.kaavalan.note.data.groups.GroupLabel
import com.kaavalan.note.data.groups.GroupLabelRepository
import com.kaavalan.note.data.person.ContactSyncService
import com.kaavalan.note.data.person.Person
import com.kaavalan.note.data.person.PersonRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val personRepository: PersonRepository = mockk(relaxed = true)
    private val groupLabelRepository: GroupLabelRepository = mockk(relaxed = true)
    private val contactSyncService: ContactSyncService = mockk(relaxed = true)
    private val people = MutableStateFlow<List<Person>>(emptyList())
    private val groupLabels = MutableStateFlow<List<GroupLabel>>(emptyList())
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { personRepository.observeAll() } returns people.asStateFlow()
        every { groupLabelRepository.observeAll() } returns groupLabels.asStateFlow()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeViewModel() = HomeViewModel(
        personRepository = personRepository,
        groupLabelRepository = groupLabelRepository,
        contactSyncService = contactSyncService,
    )

    @Test
    fun `empty state is shown when there are no people or private labels`() = runTest(dispatcher) {
        val viewModel = makeViewModel()
        advanceUntilIdle()

        assertEquals(HomeUiState.Empty, viewModel.state.value)
    }

    @Test
    fun `loaded state exposes only active person profiles and private labels`() = runTest(dispatcher) {
        people.value = listOf(
            Person(
                id = "person-1",
                name = "K. Ramu",
                designation = "Inspector",
                station = "North Unit",
                phone = "+919876543210",
                isSensitive = true,
                tier = "Inner",
                cadenceOverrideDays = 14,
                lastInteractionAt = 123L,
            ),
        )
        groupLabels.value = listOf(groupLabel("group-1", "Night patrol", "person-1"))

        val loaded = makeViewModel().state.first { it is HomeUiState.Loaded } as HomeUiState.Loaded

        assertEquals(listOf("K. Ramu"), loaded.persons.map { it.name })
        assertEquals("+919876543210", loaded.persons.single().phone)
        assertEquals("Inspector", loaded.persons.single().rankOrRole)
        assertEquals("North Unit", loaded.persons.single().unit)
        assertEquals(listOf("Night patrol"), loaded.groupLabels.map { it.name })
    }

    @Test
    fun `create person saves name phone rank role and unit`() = runTest(dispatcher) {
        coEvery {
            personRepository.create(
                name = any(),
                designation = any(),
                station = any(),
                phone = any(),
                clientId = any(),
            )
        } returns person("person-1", "K. Ramu")
        val viewModel = makeViewModel()

        viewModel.createPerson(
            name = "K. Ramu",
            phone = "+919876543210",
            rankOrRole = "Inspector",
            unit = "North Unit",
        )
        advanceUntilIdle()

        coVerify(exactly = 1) {
            personRepository.create(
                name = "K. Ramu",
                designation = "Inspector",
                station = "North Unit",
                phone = "+919876543210",
                clientId = null,
            )
        }
    }

    @Test
    fun `contact import preserves the selected phone number`() = runTest(dispatcher) {
        coEvery {
            personRepository.create(any(), any(), any(), any(), any())
        } returns person("person-1", "Priya")
        val viewModel = makeViewModel()

        viewModel.importContact("Priya", "+919123456789")
        advanceUntilIdle()

        coVerify(exactly = 1) {
            personRepository.create(
                name = "Priya",
                designation = null,
                station = null,
                phone = "+919123456789",
                clientId = null,
            )
        }
    }

    @Test
    fun `private label create and delete are delegated to the label repository`() = runTest(dispatcher) {
        coEvery { groupLabelRepository.create(any(), any()) } returns
            groupLabel("group-1", "Night patrol", "person-1")
        val viewModel = makeViewModel()

        viewModel.createGroupLabel("Night patrol", "person-1")
        viewModel.deleteGroupLabel("group-1")
        advanceUntilIdle()

        coVerify(exactly = 1) { groupLabelRepository.create("Night patrol", "person-1") }
        coVerify(exactly = 1) { groupLabelRepository.delete("group-1") }
    }

    @Test
    fun `flow error never exposes the underlying database message`() = runTest(dispatcher) {
        val secret = "leaky-secret-/data/data/com.kaavalan.note/databases/kaavalan-note.db"
        every { personRepository.observeAll() } returns flow { throw IOException(secret) }

        val error = makeViewModel().state.first { it is HomeUiState.Error } as HomeUiState.Error

        assertFalse(error.message.contains(secret, ignoreCase = true))
        assertFalse(error.message.contains("/data/data", ignoreCase = true))
        assertTrue(error.message.isNotBlank())
    }

    private fun person(id: String, name: String) = Person(
        id = id,
        name = name,
        designation = null,
        station = null,
        phone = null,
    )

    private fun groupLabel(id: String, name: String, responsiblePersonId: String?) = GroupLabel(
        id = id,
        name = name,
        responsiblePersonId = responsiblePersonId,
        createdAt = "2026-09-02T00:00:00Z",
        updatedAt = "2026-09-02T00:00:00Z",
    )
}
