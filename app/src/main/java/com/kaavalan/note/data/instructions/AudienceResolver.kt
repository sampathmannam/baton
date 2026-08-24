package com.kaavalan.note.data.instructions

import com.kaavalan.note.data.person.Person

object AudienceResolver {
    fun resolve(audience: AudienceRef, roster: RosterPicker): List<Person> = when (audience) {
        is AudienceRef.ByPerson -> roster.allPeople.firstOrNull { it.id == audience.personId }?.let { listOf(it) } ?: emptyList()
        is AudienceRef.ByDesignation -> roster.peopleByDesignation(audience.designation)
        is AudienceRef.ByStation -> roster.stations.firstOrNull { it.station.equals(audience.station, ignoreCase = true) }?.byDesignation?.values?.flatten() ?: emptyList()
        is AudienceRef.ByAll -> roster.allPeople
    }
    fun count(audience: AudienceRef, roster: RosterPicker): Int = resolve(audience, roster).size
}
