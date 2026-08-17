package com.baton.app.di

import com.baton.app.data.vault.PassphraseStrength
import com.baton.app.data.vault.VaultCrypto
import com.baton.app.data.vault.VaultExporter
import com.baton.app.data.vault.VaultImporter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Tier 1.1 (v2.0): Hilt module for the .baton-vault feature.
 *
 * The Hilt graph already provides [com.baton.app.data.local.AppDatabase]
 * (via [DatabaseModule.provideDatabase]) and the per-feature
 * ViewModels are `@HiltViewModel`-injected. The exporter +
 * importer pull the AppDatabase directly so they can do the
 * WAL checkpoint + close-reopen dance.
 */
@Module
@InstallIn(SingletonComponent::class)
object VaultModule {

    @Provides
    @Singleton
    fun provideVaultCrypto(): VaultCrypto = VaultCrypto()

    @Provides
    @Singleton
    fun providePassphraseStrength(): PassphraseStrength = PassphraseStrength()
}
