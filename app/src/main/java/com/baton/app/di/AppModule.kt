package com.baton.app.di

import com.baton.app.data.person.Person
import com.baton.app.data.person.PersonRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * App-wide Hilt module. Real bindings (Supabase client, Room DB, AI engine)
 * are added in later tasks. This module exists so the test can verify
 * the Hilt graph compiles from day one.
 *
 * The [providePersonRepository] binding is a stub that returns an empty list
 * so the Hilt graph compiles and the Home screen renders its empty state.
 * Task 6 (DI wire-up) replaces this with the Supabase-backed implementation.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun providePersonRepository(): PersonRepository = EmptyPersonRepository
}

private object EmptyPersonRepository : PersonRepository {
    override suspend fun observeAll(): List<Person> = emptyList()
}
