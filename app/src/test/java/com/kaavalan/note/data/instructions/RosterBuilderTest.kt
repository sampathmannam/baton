package com.kaavalan.note.data.instructions
import com.kaavalan.note.data.person.Person
import org.junit.Assert.assertEquals
import org.junit.Test
class RosterBuilderTest {
    private fun p(id: String, name: String, designation: String? = null, station: String? = null) = Person(id = id, name = name, designation = designation, station = station, phone = null)
    @Test fun `empty list yields empty picker`() { val picker = RosterBuilder.build(emptyList()); assertEquals(0, picker.totalPeople); assertEquals(emptyList<String>(), picker.allDesignations) }
    @Test fun `people group by station then designation`() { val picker = RosterBuilder.build(listOf(p("p1", "Senthil", "Inspector", "RedHills"), p("p2", "Ramesh", "SI", "RedHills"), p("p3", "Anu", "Constable", "Tambaram"))); assertEquals(2, picker.stations.size); assertEquals(3, picker.totalPeople) }
    @Test fun `peopleByDesignation aggregates across stations`() { val picker = RosterBuilder.build(listOf(p("p1", "Senthil", "SI", "RedHills"), p("p2", "Ramesh", "SI", "Tambaram"))); val sis = picker.peopleByDesignation("SI"); assertEquals(2, sis.size); assertEquals(setOf("p1", "p2"), sis.map { it.id }.toSet()) }

    @Test fun `peopleByDesignation lookup is case-insensitive`() {
        // User types @si in the capture; the stored designation is
        // uppercase "SI" (the canonical form in the contacts list).
        // The lookup must match across cases or the broadcast fails
        // silently. v2.0 (Hierarchy) — this is the bug the
        // end-to-end test surfaced; we lock the fix here.
        val picker = RosterBuilder.build(listOf(p("p1", "Senthil", "SI", "RedHills")))
        assertEquals(1, picker.peopleByDesignation("si").size)
        assertEquals(1, picker.peopleByDesignation("SI").size)
        assertEquals(1, picker.peopleByDesignation("Si").size)
    }
}
