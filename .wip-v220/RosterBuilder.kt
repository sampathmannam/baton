package com.baton.app.data.instructions

import com.baton.app.data.person.Person

data class RosterNode(val station: String, val byDesignation: Map<String, List<Person>>) {
    val designations: List<String> get() = byDesignation.keys.sortedBy { seniority(it) }
    fun peopleFor(designation: String): List<Person> = byDesignation[designation] ?: emptyList()
    val totalPeople: Int get() = byDesignation.values.sumOf { it.size }
}
data class RosterPicker(val stations: List<RosterNode>, val allDesignations: List<String>) {
    val totalPeople: Int get() = stations.sumOf { it.totalPeople }
    val allPeople: List<Person> by lazy { stations.flatMap { it.byDesignation.values.flatten() }.distinctBy { it.id } }
    fun peopleByDesignation(designation: String): List<Person> = stations.flatMap { it.peopleFor(designation) }
}
object RosterBuilder {
    fun build(persons: List<Person>): RosterPicker {
        val grouped = persons.groupBy { it.station?.takeIf { s -> s.isNotBlank() } ?: "Unassigned" }
        val nodes = grouped.map { (station, people) -> val byDesig = people.groupBy { it.designation?.takeIf { d -> d.isNotBlank() } ?: "Unassigned" }; RosterNode(station = station, byDesignation = byDesig) }.sortedBy { it.station.lowercase() }
        return RosterPicker(stations = nodes, allDesignations = nodes.flatMap { it.designations }.distinct())
    }
    private fun seniority(designation: String): Int { val d = designation.lowercase(); return when (d) { "ig" -> 0; "dig" -> 1; "sp", "superintendent" -> 2; "addl sp", "additional sp" -> 3; "dsp", "asp" -> 4; "inspector" -> 5; "sho" -> 6; "si", "sub-inspector" -> 7; "asi" -> 8; "hc", "head constable" -> 9; "constable" -> 10; else -> 50 } }
}
