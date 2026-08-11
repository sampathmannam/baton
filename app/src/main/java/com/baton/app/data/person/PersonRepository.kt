package com.baton.app.data.person

interface PersonRepository {
    suspend fun observeAll(): List<Person>
}
