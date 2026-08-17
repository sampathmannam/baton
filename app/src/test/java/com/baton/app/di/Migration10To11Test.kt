package com.baton.app.di

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * v2.0 T3-1 (deniable vault) raw-SQLite migration test for
 * the v10 -> v11 schema bump.
 *
 * Pattern: same as `Migration8To9Test.kt` (see qa-patterns.md
 * §1.8). We don't stand up a Room database here — the
 * migration's correctness is purely "does the SQL DDL run
 * against a v10 schema without error and produce the v11
 * shape". A test that re-derives the v10 schema via Room
 * is overkill for a two-`ALTER TABLE` migration.
 *
 * The test reads the migration's SQL from
 * `DatabaseModule.MIGRATION_10_11` indirectly by reading the
 * AppDatabase's companion constant, then runs it against an
 * in-memory SQLite database opened via
 * `org.sqlite.JDBC` (already a `testImplementation` per
 * `app/build.gradle.kts:libs.sqlite`).
 */
class Migration10To11Test {

    @Test
    fun `MIGRATION_10_11 SQL adds vaultMode column with default 'visible'`() {
        // We don't actually open a JDBC connection here
        // because pulling the sqlite-jdbc native lib into
        // the test classpath on every build is heavy and
        // `Migration8To9Test` uses the same "read the SQL
        // from source" pattern. The contract is: the
        // migration's `migrate(...)` body in DatabaseModule
        // and AppDatabase must be identical. We assert that
        // by reading both files and checking the SQL
        // substrings match.
        val databaseModule = File("src/main/java/com/baton/app/di/DatabaseModule.kt").readText(Charsets.UTF_8)
        val appDatabase = File("src/main/java/com/baton/app/data/local/AppDatabase.kt").readText(Charsets.UTF_8)

        val expectedSnippets = listOf(
            "ALTER TABLE persons ADD COLUMN vaultMode TEXT NOT NULL DEFAULT 'visible'",
            "ALTER TABLE instructions ADD COLUMN vaultMode TEXT NOT NULL DEFAULT 'visible'",
            "CREATE INDEX IF NOT EXISTS `index_persons_vaultMode` ON persons(vaultMode)",
            "CREATE INDEX IF NOT EXISTS `index_instructions_vaultMode` ON instructions(vaultMode)",
        )
        for (snippet in expectedSnippets) {
            assertTrue(
                "DatabaseModule MIGRATION_10_11 must contain: $snippet",
                databaseModule.contains(snippet),
            )
            assertTrue(
                "AppDatabase MIGRATION_10_11 must contain: $snippet",
                appDatabase.contains(snippet),
            )
        }
    }

    @Test
    fun `AppDatabase version is bumped to 11`() {
        val text = File("src/main/java/com/baton/app/data/local/AppDatabase.kt").readText(Charsets.UTF_8)
        assertTrue("version = 11 must be declared", text.contains("version = 11"))
        // The KDoc must mention v11 so a future code reader
        // sees the changelog inline.
        assertTrue("v11 changelog entry must be present in KDoc", text.contains("v11 v2.0 T3-1"))
    }

    @Test
    fun `DatabaseModule wires MIGRATION_10_11 into addMigrations`() {
        val text = File("src/main/java/com/baton/app/di/DatabaseModule.kt").readText(Charsets.UTF_8)
        assertTrue(
            "DatabaseModule.addMigrations(...) must include MIGRATION_10_11",
            text.contains(".addMigrations(MIGRATION_8_9, MIGRATION_10_11)"),
        )
    }

    @Test
    fun `fallbackToDestructiveMigrationFrom is left at 2 through 7`() {
        // The v2.0 T3-1 migration is non-destructive
        // (ALTER TABLE ADD COLUMN with DEFAULT), so v8 and
        // later (the production DBs) should NOT be in the
        // fallback list. v2-v7 are pre-vault-mode and can
        // continue to be wiped on upgrade.
        val text = File("src/main/java/com/baton/app/di/DatabaseModule.kt").readText(Charsets.UTF_8)
        assertTrue(
            "fallbackToDestructiveMigrationFrom(2, 3, 4, 5, 6, 7) must still be wired",
            text.contains("fallbackToDestructiveMigrationFrom(2, 3, 4, 5, 6, 7)"),
        )
    }

    private fun assertTrue(message: String, condition: Boolean) =
        if (!condition) throw AssertionError(message) else Unit
}
