package com.baton.app.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.serialization.json.Json

/**
 * v1.3: [BUG-AUTH-003] — JWT + refresh tokens were being persisted by
 * supabase-kt 3.x's default [io.github.jan.supabase.auth.SettingsSessionManager],
 * which writes a plain-text JSON blob to the device's
 * `shared_prefs/supabase_auth.xml` file. An attacker with file-system
 * access (rooted device, ADB backup of a developer unit, lost phone
 * without FDE) could `adb pull` that file and extract the live
 * access token + refresh token.
 *
 * The fix is this class: a [SessionManager] that persists the
 * [UserSession] through [EncryptedSharedPreferences], which is itself
 * protected by a Keystore-backed [MasterKey] (AES-256-GCM by default).
 * Even with raw file-system access the attacker would need to break
 * AES-256-GCM (the Keystore master key is unwrapped only inside the
 * TEE on hardware-backed devices).
 *
 * **Design notes.**
 *
 *  - The class takes a [SharedPreferences] rather than a [Context] so
 *    unit tests can inject a regular `getSharedPreferences(...)` (or
 *    a Robolectric-backed [EncryptedSharedPreferences] if the Keystore
 *    shadow is available). The encryption is encapsulated in *where*
 *    the [SharedPreferences] comes from, not in this class — the
 *    production factory [create] wires the encrypted store and the
 *    tests wire a plain one. This keeps the session-manager surface
 *    area tiny and unit-testable.
 *
 *  - The session is serialised with the same [Json] configuration the
 *    rest of the app uses (`ignoreUnknownKeys = true`, lenient
 *    encoders). supabase-kt may add fields in a minor release; we
 *    want to be forward-compatible with sessions written by a newer
 *    client reading them back on an older one. The converse — older
 *    serialised session read by newer client — also has to work
 *    because the user might install a build that adds fields.
 *
 *  - The key under which the JSON is stored is
 *    [KEY_SESSION] and lives inside the file
 *    [SESSION_PREFS_FILE_NAME]. This is a *separate* SharedPreferences
 *    file from [SecurePreferences.FILE_NAME] (the SQLCipher passphrase
 *    store) so a sign-out that clears one does not nuke the other
 *    and so the two can be backed up / inspected independently by
 *    an auditor running `adb shell run-as`.
 *
 * **What this class does NOT do.**
 *
 *  - It does not decide *when* to save or delete the session — that
 *    is the Auth plugin's job, driven by the supabase-kt lifecycle
 *    hooks (`autoSaveToStorage`, `autoLoadFromStorage`). The default
 *    is to save after every `signIn*` / `signUp*` / token refresh
 *    and to delete on `signOut`. This class is purely a persistence
 *    adapter.
 *  - It does not protect against a compromised app process — if the
 *    attacker is already inside the process they can read the
 *    unencrypted [UserSession] from memory regardless of how it is
 *    stored at rest. The threat model is "phone is physically lost
 *    or stolen" plus "forensics on a powered-off device".
 */
class SupabaseEncryptedSessionManager(
    private val prefs: SharedPreferences,
    private val json: Json = DEFAULT_JSON,
) : SessionManager {

    /**
     * Returns the persisted session, or `null` if the user has never
     * signed in on this device (or has signed out, which deletes the
     * key). Errors are propagated to the caller — supabase-kt surfaces
     * them as `SessionStatus.RefreshFailure` and the UI re-renders
     * as signed-out, which is the correct behaviour for a corrupted
     * on-disk session.
     */
    override suspend fun loadSession(): UserSession? {
        val raw = prefs.getString(KEY_SESSION, null) ?: return null
        return runCatching { json.decodeFromString<UserSession>(raw) }
            // A corrupted session is treated as "no session" — the
            // user lands on the sign-in screen and the next sign-in
            // overwrites the bad blob. We don't throw because
            // supabase-kt's loadSession contract is `UserSession?` —
            // a throw here would crash the app on every cold start
            // until the user cleared app data.
            .getOrNull()
    }

    /**
     * Serialise and persist the [session]. Called by the supabase-kt
     * Auth plugin after every successful sign-in, sign-up, and
     * token refresh. The serialised JSON is opaque to the Auth
     * plugin — it only round-trips through [loadSession].
     */
    override suspend fun saveSession(session: UserSession) {
        val raw = json.encodeToString(UserSession.serializer(), session)
        prefs.edit().putString(KEY_SESSION, raw).apply()
    }

    /**
     * Wipe the persisted session. Called on `signOut`. We remove
     * only [KEY_SESSION] rather than `clear()`-ing the whole
     * file so a future "remember the last signed-in email" feature
     * (out of scope for v1) can co-exist in the same file without
     * us having to migrate the schema.
     */
    override suspend fun deleteSession() {
        prefs.edit().remove(KEY_SESSION).apply()
    }

    companion object {
        /**
         * JSON instance used for (de)serialising the session. Defaults
         * to `ignoreUnknownKeys = true` so a session written by a
         * newer supabase-kt that adds a field does not fail to
         * deserialise on an older app build.
         */
        val DEFAULT_JSON: Json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        /**
         * The session lives in its own SharedPreferences file, not
         * alongside the SQLCipher passphrase in
         * [SecurePreferences.FILE_NAME]. Two reasons: (1) the two
         * have different lifecycles — the passphrase is wiped on
         * sign-out but the session is too, so they happen to align,
         * but a future "erase the database but keep me signed in"
         * feature would want them separate; (2) it makes the audit
         * story trivial — `adb shell run-as com.baton.app ls
         * shared_prefs/` shows two distinct files, and the auditor
         * can `cat` each one independently.
         */
        const val SESSION_PREFS_FILE_NAME = "baton_secure_session"

        /**
         * Single key inside [SESSION_PREFS_FILE_NAME]. Bumping the
         * `_v1` suffix is the migration path if the on-disk shape
         * ever changes (e.g. switching to CBOR).
         */
        const val KEY_SESSION = "supabase_session_v1"

        /**
         * Build a [SupabaseEncryptedSessionManager] backed by an
         * [EncryptedSharedPreferences] store protected by a
         * Keystore-derived [MasterKey] (AES-256-GCM by default).
         *
         * The master key is created on the first call and persisted
         * in the AndroidKeyStore; subsequent calls reuse it. On
         * Android 6.0+ with hardware-backed Keystore, the master key
         * is bound to the TEE and the on-disk file is safe to
         * exfiltrate.
         */
        fun create(context: Context, json: Json = DEFAULT_JSON): SupabaseEncryptedSessionManager {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val prefs: SharedPreferences = EncryptedSharedPreferences.create(
                context,
                SESSION_PREFS_FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
            return SupabaseEncryptedSessionManager(prefs, json)
        }
    }
}
