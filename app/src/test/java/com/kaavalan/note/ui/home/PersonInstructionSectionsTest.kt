package com.kaavalan.note.ui.home

import com.kaavalan.note.data.instructions.Direction
import com.kaavalan.note.data.instructions.Instruction
import com.kaavalan.note.data.instructions.Priority
import com.kaavalan.note.data.instructions.Source
import com.kaavalan.note.data.instructions.Status
import com.kaavalan.note.data.person.Person
import com.kaavalan.note.data.person.PersonProfile
import com.kaavalan.note.data.person.toProfile
import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.Modifier

class PersonInstructionSectionsTest {

    @Test
    fun `active profile exposes only name phone rank role unit and identity`() {
        val profile = Person(
            id = "p1",
            name = "Ramu",
            designation = "Inspector",
            station = "North Unit",
            phone = "+919876543210",
            isSensitive = true,
            tier = "Inner",
            cadenceOverrideDays = 14,
            lastInteractionAt = 123L,
        ).toProfile()

        assertEquals(
            setOf("id", "name", "phone", "rankOrRole", "unit"),
            PersonProfile::class.java.declaredFields
                .filterNot { Modifier.isStatic(it.modifiers) }
                .map { it.name }
                .toSet(),
        )
        assertEquals("Ramu", profile.name)
        assertEquals("+919876543210", profile.phone)
        assertEquals("Inspector", profile.rankOrRole)
        assertEquals("North Unit", profile.unit)
    }

    @Test
    fun `person instructions split into active and completed sections`() {
        val sections = partitionInstructions(
            listOf(
                instruction("todo", Status.TO_DO),
                instruction("waiting", Status.WAITING),
                instruction("done", Status.DONE),
            ),
        )

        assertEquals(listOf("todo", "waiting"), sections.active.map { it.id })
        assertEquals(listOf("done"), sections.completed.map { it.id })
    }

    private fun instruction(id: String, status: Status) = Instruction(
        id = id,
        personId = "p1",
        direction = Direction.OUTGOING,
        status = status,
        source = Source.TEXT,
        priority = Priority.NORMAL,
        title = id,
        rawText = id,
        dueAt = null,
        capturedAt = "2026-09-01T00:00:00Z",
        createdAt = "2026-09-01T00:00:00Z",
        updatedAt = "2026-09-01T00:00:00Z",
    )
}
