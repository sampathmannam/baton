package com.kaavalan.note.data.groups

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kaavalan.note.data.local.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GroupLabelRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: RoomGroupLabelRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = RoomGroupLabelRepository(db.groupLabelDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `creates private labels sorted by name with an optional responsible person`() = runTest {
        repository.create("Night patrol", responsiblePersonId = "person-1")
        repository.create("Control room", responsiblePersonId = null)

        val labels = repository.observeAll().first()

        assertEquals(listOf("Control room", "Night patrol"), labels.map { it.name })
        assertEquals("person-1", labels.last().responsiblePersonId)
        assertEquals(null, labels.first().responsiblePersonId)
    }

    @Test
    fun `case insensitive duplicate returns the existing private label`() = runTest {
        val first = repository.create("District Group", responsiblePersonId = "person-1")
        val second = repository.create("district group", responsiblePersonId = "person-2")

        assertEquals(first.id, second.id)
        assertEquals("person-1", second.responsiblePersonId)
        assertEquals(1, repository.observeAll().first().size)
    }

    @Test
    fun `delete removes only the selected label`() = runTest {
        val keep = repository.create("Keep", null)
        val remove = repository.create("Remove", null)

        repository.delete(remove.id)

        assertEquals(listOf(keep.id), repository.observeAll().first().map { it.id })
    }
}
