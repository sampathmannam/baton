package com.kaavalan.note.data.vault

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * v2.0 T3-2 (recovery phrase): Hilt module that loads the
 * vendored BIP39 English word list from
 * `app/src/main/assets/bip39-wordlist.txt` (2048 words, MIT-
 * licensed, copied verbatim from the canonical BIP39 spec) and
 * exposes a [MnemonicGenerator] singleton.
 *
 * **Loading the word list.** We read the file lazily inside
 * [providesWordList] and cache the result in a `lazy {}` field
 * so the asset is only opened on the first call. The list is
 * 12.8 KB; parsing is O(1) in startup.
 *
 * **Why not ship the list as a Kotlin `List<String>` literal?**
 *  The canonical list is 2048 words and lives at a stable
 * upstream URL; vendoring as an asset keeps the binary clean
 * and lets us swap to a Tamil / Hindi list in a follow-up by
 * replacing one file.
 */
@Module
@InstallIn(SingletonComponent::class)
object VaultCryptoModule {

    private const val WORDLIST_ASSET = "bip39-wordlist.txt"

    @Provides
    @Singleton
    fun providesWordList(@ApplicationContext context: Context): List<String> {
        val words = context.assets.open(WORDLIST_ASSET)
            .bufferedReader(Charsets.UTF_8)
            .useLines { seq -> seq.toList() }
        // The vendored file is one word per line, no trailing
        // blank line; filter defensively in case a future
        // editor appends a newline.
        return words.filter { it.isNotBlank() }
    }

    @Provides
    @Singleton
    fun providesMnemonicGenerator(
        wordList: List<String>,
    ): MnemonicGenerator = MnemonicGenerator(wordList)
}
