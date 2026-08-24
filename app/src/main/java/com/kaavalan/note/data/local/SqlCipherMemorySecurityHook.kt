package com.kaavalan.note.data.local

import net.zetetic.database.sqlcipher.SQLiteConnection
import net.zetetic.database.sqlcipher.SQLiteDatabaseHook

/**
 * v1.9.11 (Obs-3 mlock): a custom [SQLiteDatabaseHook] that
 * disables SQLCipher's `mlock()` attempts via
 * `PRAGMA cipher_memory_security = OFF` in [preKey].
 *
 * **Why this exists.** SQLCipher's default
 * `cipher_memory_security` is `ON`, which calls `mlock()` on
 * the database file to prevent pages being paged to disk.
 * Android restricts `mlock` (no `CAP_IPC_LOCK` for non-root
 * processes), so every cold start logs ~30+ "mlock() returned
 * -1 errno=12" warnings. The v1.4.3 fix (see
 * [com.kaavalan.note.di.DatabaseModule.onOpenPragmaCallback])
 * already sets the pragma in `onOpen`, but that runs
 * **after** the keying phase — the keying is when the mlock
 * attempts actually fire. The warnings still appear in the
 * logcat.
 *
 * This hook closes the gap. SQLCipher's
 * [SupportOpenHelperFactory] takes a [SQLiteDatabaseHook] in
 * its constructor and calls [SQLiteDatabaseHook.preKey]
 * **before** the keying phase. Setting the pragma in [preKey]
 * is the earliest point we can intervene without forking
 * SQLCipher.
 *
 * **What this is NOT.** It is not a security regression.
 * `cipher_memory_security = OFF` is the same pragma the
 * v1.4.3 fix already set in `onOpen`; the only difference is
 * timing. The encryption is unchanged — the passphrase is
 * still required to decrypt the database. The mlock
 * guarantee was the only thing that changed, and Android
 * never allowed it anyway.
 *
 * **The mlock warnings.** With this hook in place, the
 * v1.9.10 logcat showed ~31 mlock warnings per cold start.
 * v1.9.11 should show **0** — the pragma is set before
 * keying, so SQLCipher doesn't even try to mlock. A drive-
 * verify on the connected device should confirm.
 *
 * **Configuration.** Pass this hook to
 * [net.zetetic.database.sqlcipher.SupportOpenHelperFactory]'s
 * constructor that takes a hook (the 3-arg overload).
 */
class SqlCipherMemorySecurityHook : SQLiteDatabaseHook {

    /**
     * Called by SQLCipher **before** the user-supplied
     * passphrase is applied to the database. This is the
     * earliest point we can set connection-local pragmas.
     */
    override fun preKey(connection: SQLiteConnection) {
        // mlock is the "pin pages in physical RAM" syscall.
        // It is unachievable on Android (no CAP_IPC_LOCK for
        // non-root), and SQLCipher logs 31+ ENOMEM=12
        // warnings per cold start when it tries. Disable it
        // explicitly. The pragma is per-connection; setting
        // it here applies to the keying phase AND all
        // subsequent operations on this connection.
        runCatching {
            connection.execute(
                "PRAGMA cipher_memory_security = OFF",
                emptyArray<Any>(),
                null,
            )
        }
    }

    /**
     * Called by SQLCipher **after** the keying phase. We
     * don't need to do anything here — the pragma set in
     * [preKey] persists for the connection's lifetime.
     */
    override fun postKey(connection: SQLiteConnection) {
        // Intentionally empty. The pragma is connection-local
        // and persists from [preKey] through the connection
        // close.
    }
}
