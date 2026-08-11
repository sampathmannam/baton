package com.baton.app.data.person

interface PersonRepository {
    suspend fun observeAll(): List<Person>
    suspend fun create(name: String, designation: String?, station: String?): Person
}
