package com.baton.app.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * App-wide Hilt module. Real bindings (Supabase client, Room DB, AI engine)
 * are added in later tasks. This module exists so the test can verify
 * the Hilt graph compiles from day one.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule
