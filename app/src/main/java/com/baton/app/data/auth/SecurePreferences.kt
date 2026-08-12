package com.baton.app.data.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * M3-T1: secure storage of the local database encryption key.
 *
 * The Room DB is opened through SQLCipher (see [com.baton.app.di.DatabaseModule]).
 * SQLCipher needs a 32-byte passphrase; we generate it on first launch
 * with [SecureRandom] and persist it in [EncryptedSharedPreferences],
 * which is itself protected by a Keystore-backed master key (Android
 * 6+ hardware-backed where available).
 *
 * **Threat model.** The passphrase protects the on-disk Room DB from
 * being read by:
 *
 *  - An attacker with physical access to the device (cold-boot, ADB
 *    pull of `/data/data/com.baton.app/databases/baton.db`).
 *  - Backup extraction (Android's auto-backup is disabled in this
 *    app via `android:allowBackup="false"`, but a future change that
 *    re-enables it doesn't leak the DB).
 *
 * It does **not** protect against:
 *
 *  - An attacker who has compromised the app process (Keystore
 *    unwraps the key on demand; the running app can read it).
 *  - An attacker who can read the Keystore master key (requires root
 *    + screen-unlock on modern Android, but a strong adversary could
 *    bypass it).
 *
 * For the v1 pilot the threat model is "someone steals the phone and
 * `adb pull`s the SQLite file". SQLCipher + EncryptedSharedPreferences
 * blocks that. The user-visible passphrase flow (Settings → Export /
 * Re-encrypt) is deferred to v1.1 (see spec §16, "Out of scope").
 *
 * **Sign-out.** [clearDatabasePassphrase] deletes the key. The next
 * read of the DB fails (SQLCipher reports "file is not a database");
 * the AppInitializer then catches the failure, opens a fresh
 * unencrypted DB, and the user starts clean. Sync replays from
 * Supabase the next time they're online.
 */
@Singleton
class SecurePreferences @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val masterKey: MasterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        FILE_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    /**
     * Returns the SQLCipher passphrase bytes. On first call (or after
     * a sign-out / clear), generates a fresh 32-byte key, persists
     * it, and returns it. The returned byte array is freshly allocated
     * each call so callers can zero it after use if they care.
     */
    @Synchronized
    fun databasePassphrase(): ByteArray {
        val stored = prefs.getString(KEY_DB_PASSPHRASE, null)
        if (stored != null) {
            return Base64.decode(stored, Base64.NO_WRAP)
        }
        val fresh = ByteArray(PASSPHRASE_BYTES)
        SecureRandom().nextBytes(fresh)
        prefs.edit()
            .putString(KEY_DB_PASSPHRASE, Base64.encodeToString(fresh, Base64.NO_WRAP))
            .apply()
        return fresh
    }

    /**
     * Sign-out path: delete the DB passphrase. The next DB read fails
     * and the caller ([com.baton.app.data.local.AppInitializer]) is
     * expected to wipe the old `baton.db` file so a fresh unencrypted
     * one is created. RLS on the Supabase side ensures the next sign-in
     * pulls back only the new user's data.
     *
     * Returns `true` if a passphrase was present and removed; `false`
     * if the store was already empty.
     */
    @Synchronized
    fun clearDatabasePassphrase(): Boolean {
        if (!prefs.contains(KEY_DB_PASSPHRASE)) return false
        prefs.edit().remove(KEY_DB_PASSPHRASE).apply()
        return true
    }

    /**
     * Has the user set a passphrase yet? `true` after the first
     * [databasePassphrase] call, `false` on a fresh install. Useful
     * for the AppInitializer's first-run branch (the old plain DB
     * needs to be wiped on the M2 -> M3 transition).
     */
    fun hasDatabasePassphrase(): Boolean = prefs.contains(KEY_DB_PASSPHRASE)

    companion object {
        private const val FILE_NAME = "baton_secure_prefs"
        private const val KEY_DB_PASSPHRASE = "db_passphrase_v1"
        private const val PASSPHRASE_BYTES = 32
    }
}
