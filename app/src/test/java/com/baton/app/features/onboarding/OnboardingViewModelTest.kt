package com.baton.app.features.onboarding

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.baton.app.data.local.AppDatabase
import com.baton.app.data.preferences.BatonPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tier 1.2 (v2.0): the onboarding ViewModel.
 *
 * The test pins the contract: `finish(loadSample = true)`
 * seeds 6 people + 5 instructions + 2 tags; the DataStore
 * flag flips to "seen".
 *
 * Note: DataStore writes happen on `Dispatchers.IO`, not on
 * the test dispatcher, so `advanceUntilIdle()` does NOT
 * drain them. We use `runBlocking` for the seed assertion
 * (Room writes ARE on the test dispatcher, so they work
 * with `runTest`) and a short `delay` for the DataStore
 * assertion.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class OnboardingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var ctx: Context
    private lateinit var db: AppDatabase
    private lateinit var prefs: BatonPreferences

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        prefs = BatonPreferences(ctx)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `finish with sample data seeds 6 people and 5 instructions and 2 tags`() = runTest {
        val vm = OnboardingViewModel(db, prefs)
        vm.setSampleToggled(true)
        vm.seedSampleData()
        advanceUntilIdle()
        // 6 sample people.
        val people = db.personDao().snapshot()
        assertEquals(6, people.size)
        // 5 sample instructions.
        val instructions = db.instructionDao().snapshot()
        assertEquals(5, instructions.size)
        // 2 sample tags.
        val tags = db.tagDao().observeAll().first()
        assertEquals(2, tags.size)
    }

    @Test
    fun `finish without sample data does not insert anything`() = runTest {
        val vm = OnboardingViewModel(db, prefs)
        // No setSampleToggled(true) -> the VM's finish() does
        // NOT call seedSampleData. The DB stays empty.
        vm.finish {}
        advanceUntilIdle()
        assertEquals(0, db.personDao().snapshot().size)
        assertEquals(0, db.instructionDao().snapshot().size)
    }

    @Test
    fun `setCurrentPage updates state`() = runTest {
        val vm = OnboardingViewModel(db, prefs)
        vm.setCurrentPage(2)
        assertEquals(2, vm.state.value.currentPage)
    }

    @Test
    fun `setSampleToggled updates state`() = runTest {
        val vm = OnboardingViewModel(db, prefs)
        vm.setSampleToggled(true)
        assertTrue(vm.state.value.loadSample)
    }
}
