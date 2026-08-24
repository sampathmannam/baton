package com.baton.app.data.instructions
import com.baton.app.data.person.Person
import org.junit.Assert.assertEquals
import org.junit.Test
class AudienceResolverTest {
    private fun p(id: String, name: String, designation: String? = null, station: String? = null) = Person(id = id, name = name, designation = designation, station = station, phone = null)
    private val roster = RosterBuilder.build(listOf(p("p1", "Senthil", "SI", "RedHills"), p("p2", "Ramesh", "SI", "RedHills"), p("p3", "Anu", "Constable", "Tambaram")))
    @Test fun `ByPerson resolves to that one person`() { val r = AudienceResolver.resolve(AudienceRef.ByPerson("p2", "Ramesh"), roster); assertEquals(listOf("p2"), r.map { it.id }) }
    @Test fun `ByDesignation resolves to all matching`() { val r = AudienceResolver.resolve(AudienceRef.ByDesignation("SI", "SI"), roster); assertEquals(setOf("p1", "p2"), r.map { it.id }.toSet()) }
    @Test fun `ByStation resolves to everyone at that station`() { val r = AudienceResolver.resolve(AudienceRef.ByStation("RedHills", "RedHills"), roster); assertEquals(setOf("p1", "p2"), r.map { it.id }.toSet()) }
    @Test fun `ByAll resolves to everyone on the roster`() { val r = AudienceResolver.resolve(AudienceRef.ByAll("all", "all"), roster); assertEquals(setOf("p1", "p2", "p3"), r.map { it.id }.toSet()) }
    @Test fun `unknown person returns empty list`() { val r = AudienceResolver.resolve(AudienceRef.ByPerson("nope", "Nope"), roster); assertEquals(emptyList<Person>(), r) }
}
