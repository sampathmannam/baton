package com.kaavalan.note.data.local

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v2.1.0 (PM rating): the database-corruption flag.
 * Lives in a plain (unencrypted) [SharedPreferences]
 * because the value is a single boolean — not PII,
 * not a secret — and the SecurePreferences backend
 * requires AndroidKeyStore which is not available in
 * unit tests (Robolectric shadows don't implement it).
 *
 * **Why a separate class.** Splitting the "is the DB
 * corrupt" flag from [com.kaavalan.note.data.auth.SecurePreferences]
 * has two wins:
 *
 *  1. The flag is testable in unit tests (a plain
 *     [SharedPreferences] works under Robolectric).
 *  2. The flag survives even if the SQLCipher-encrypted
 *     [com.kaavalan.note.data.auth.SecurePreferences] is
 *     wiped (e.g. the user taps "Erase all data" +
 *     "Erase secure prefs"). The corruption state is
 *     re-detected on the next preflight.
 *
 * The flag is reset on every [runPreflight] call. A
 * preflight that throws sets it to `true`; a preflight
 * that succeeds sets it to `false`. A preflight that
 * can't even open the SharedPreferences (e.g. the
 * app's data dir is unreadable) leaves the previous
 * value in place — the safest default.
 */
@Singleton
class DatabaseHealth @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        FILE_NAME,
        Context.MODE_PRIVATE,
    )

    fun markCorrupt() {
        prefs.edit().putBoolean(KEY_CORRUPT, true).apply()
    }

    fun clearCorrupt() {
        prefs.edit().remove(KEY_CORRUPT).apply()
    }

    fun isCorrupt(): Boolean = prefs.getBoolean(KEY_CORRUPT, false)

    private companion object {
        const val FILE_NAME = "baton_db_health"
        const val KEY_CORRUPT = "corrupt_v1"
    }
}
