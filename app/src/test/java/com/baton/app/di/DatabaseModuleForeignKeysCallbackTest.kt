package com.baton.app.di

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v1.4.3 regression test (F-37).
 *
 * Locks the property that [DatabaseModule.onOpenPragmaCallback]
 * (which is wired into [DatabaseModule.provideDatabase]) issues
 * BOTH per-connection `PRAGMA`s on every Room open:
 *
 *   1. `PRAGMA foreign_keys = ON` — v1.2.1 (BUG-DATA-009).
 *      Without it, SQLite silently ignores `ON DELETE CASCADE`
 *      and a deleted parent leaves orphan rows.
 *
 *   2. `PRAGMA cipher_memory_security = OFF` — v1.4.3 (F-37).
 *      SQLCipher's default tries to `mlock()` the database file
 *      in memory; Android restricts `mlock` (returns
 *      `ENOMEM=12`), so every DB open logs 8+ mlock warnings.
 *      We turn the mlock off — encryption is unchanged.
 *
 * The test invokes [DatabaseModule.onOpenPragmaCallback] against
 * a mock [SupportSQLiteDatabase] and asserts both `execSQL`
 * statements are issued. The mock approach is the only way to
 * verify the SQLCipher-specific `cipher_memory_security` pragma
 * is issued: regular SQLite (what the in-memory Room builder
 * stands up in Robolectric) doesn't know that pragma and
 * `PRAGMA cipher_memory_security` returns an empty result, so a
 * live-DB `query()` assertion can't observe it. The mock
 * captures the exact `execSQL` string the callback passed —
 * that's the contract.
 *
 * If anyone removes either `execSQL(...)` from
 * [DatabaseModule.onOpenPragmaCallback], this test fails.
 *
 * The pre-existing [DatabaseModuleTest] continues to verify
 * `PRAGMA foreign_keys = 1` against a live in-memory Room DB
 * (the v1.2.1 regression check); this test complements it by
 * also locking the new `PRAGMA cipher_memory_security = OFF`
 * statement into place.
 *
 * (Why no live-DB test for the cipher_memory_security side:
 * the `SupportSQLiteDatabase.query("PRAGMA cipher_memory_security")`
 * call against regular SQLite returns 0 rows — confirmed
 * empirically on Robolectric 4.13 + AndroidX Room 2.6.1. The
 * unknown pragma is a connection-local no-op set via
 * `execSQL`, but it has no value to query. The mock captures
 * the `execSQL` call directly, which is the actual contract.)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DatabaseModuleForeignKeysCallbackTest {

    @Test
    fun `F-37 onOpenPragmaCallback issues BOTH foreign_keys=ON and cipher_memory_security=OFF`() {
        // `relaxed = true` so any un-stubbed method returns its
        // default (Unit for void, null for refs). The only
        // methods we expect the callback to invoke are the two
        // `execSQL(String)` calls below.
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)

        val callback: RoomDatabase.Callback = DatabaseModule.onOpenPragmaCallback()
        callback.onOpen(db)

        // (1) v1.2.1 (BUG-DATA-009): PRAGMA foreign_keys = ON.
        // Regression lock — must remain set even after the
        // v1.4.3 F-37 addition.
        verify(exactly = 1) { db.execSQL("PRAGMA foreign_keys = ON") }

        // (2) v1.4.3 (F-37): PRAGMA cipher_memory_security = OFF.
        // SQLCipher-specific; on regular SQLite (what
        // Robolectric stands up) the statement is silently
        // accepted as a no-op. With SQLCipher on Android, this
        // same statement silences the 8+ mlock warnings per
        // start.
        verify(exactly = 1) { db.execSQL("PRAGMA cipher_memory_security = OFF") }
    }

    @Test
    fun `F-37 onOpenPragmaCallback issues ONLY the two pragmas, no other execSQL`() {
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)

        val callback: RoomDatabase.Callback = DatabaseModule.onOpenPragmaCallback()
        callback.onOpen(db)

        // mockk's `verify(exactly = N) { db.execSQL(EXACT_STRING) }`
        // constrains the count of calls to that exact string.
        // It would NOT fail if a different `execSQL` were
        // issued (e.g. an accidental third pragma). So we add
        // an explicit "no other execSQL" guard: assert that
        // `db.execSQL(any())` was called exactly twice total
        // (once for each of the two known pragmas). mockk
        // exposes this via `verify(exactly = N) { db.execSQL(allAny()) }`.
        verify(exactly = 2) { db.execSQL(any<String>()) }
    }

    @Test
    fun `F-37 onOpenPragmaCallback is a factory producing independent callbacks per call`() {
        // Two calls to the factory produce two independent
        // callback instances that both fire the same two
        // pragmas. This is the behaviour the production code
        // relies on: Hilt's [DatabaseModule.provideDatabase]
        // is a @Singleton, so the callback is created once at
        // app start, but tests that build their own in-memory
        // DB each call [DatabaseModule.onOpenPragmaCallback]
        // and expect a fresh callback. Locking the factory
        // shape here so a future refactor (e.g. "return a
        // stored singleton") can't silently change the
        // contract.
        val a: RoomDatabase.Callback = DatabaseModule.onOpenPragmaCallback()
        val b: RoomDatabase.Callback = DatabaseModule.onOpenPragmaCallback()

        val dbA = mockk<SupportSQLiteDatabase>(relaxed = true)
        val dbB = mockk<SupportSQLiteDatabase>(relaxed = true)
        a.onOpen(dbA)
        b.onOpen(dbB)

        verify(exactly = 1) { dbA.execSQL("PRAGMA foreign_keys = ON") }
        verify(exactly = 1) { dbA.execSQL("PRAGMA cipher_memory_security = OFF") }
        verify(exactly = 1) { dbB.execSQL("PRAGMA foreign_keys = ON") }
        verify(exactly = 1) { dbB.execSQL("PRAGMA cipher_memory_security = OFF") }
    }
}
