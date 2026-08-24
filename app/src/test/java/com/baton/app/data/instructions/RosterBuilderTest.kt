package com.baton.app.data.instructions

import com.baton.app.data.person.Person
import org.junit.Assert.assertEquals
import org.junit.Test

class RosterBuilderTest {
    private fun person(id: String, name: String, designation: String? = null, station: String? = null) = Person(id = id, name = name, designation = designation, station = station, phone = null)
    @Test fun `empty list yields empty picker`() { val picker = RosterBuilder.build(emptyList()); assertEquals(0, picker.totalPeople); assertEquals(emptyList<String>(), picker.allDesignations) }
    @Test fun `people group by station then designation`() { val picker = RosterBuilder.build(listOf(person("p1", "Senthil", "Inspector", "RedHills"), person("p2", "Ramesh", "SI", "RedHills"), person("p3", "Anu", "Constable", "Tambaram"))); assertEquals(2, picker.stations.size); assertEquals(3, picker.totalPeople) }
    @Test fun `peopleByDesignation aggregates across stations`() { val picker = RosterBuilder.build(listOf(person("p1", "Senthil", "SI", "RedHills"), person("p2", "Ramesh", "SI", "Tambaram"))); val sis = picker.peopleByDesignation("SI"); assertEquals(2, sis.size); assertEquals(setOf("p1", "p2"), sis.map { it.id }.toSet()) }
}
