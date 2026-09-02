package com.kaavalan.note.di

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.kaavalan.note.data.local.AppDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class Migration17To18Test {

    private lateinit var helper: SupportSQLiteOpenHelper
    private lateinit var db: SupportSQLiteDatabase
    private val databaseName = "migration-17-18-${System.nanoTime()}.db"

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(object : SupportSQLiteOpenHelper.Callback(17) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            """
                            CREATE TABLE persons (
                                id TEXT NOT NULL PRIMARY KEY,
                                name TEXT NOT NULL,
                                designation TEXT,
                                station TEXT,
                                phone TEXT,
                                userId TEXT NOT NULL,
                                createdAt TEXT NOT NULL,
                                updatedAt TEXT NOT NULL,
                                isSensitive INTEGER NOT NULL,
                                syncStatus TEXT NOT NULL,
                                tier TEXT NOT NULL,
                                cadenceOverrideDays INTEGER,
                                lastInteractionAt INTEGER,
                                vaultMode TEXT NOT NULL
                            )
                            """.trimIndent(),
                        )
                        db.execSQL(
                            """
                            INSERT INTO persons VALUES (
                                'person-1', 'K. Ramu', 'Inspector', 'North Unit', '+919876543210',
                                'legacy-user', '2025-01-01T00:00:00Z', '2026-09-01T00:00:00Z',
                                1, 'SYNCED', 'Inner', 14, 1788200000000, 'hidden'
                            )
                            """.trimIndent(),
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        db = helper.writableDatabase
    }

    @After
    fun tearDown() {
        helper.close()
        ApplicationProvider.getApplicationContext<Context>().deleteDatabase(databaseName)
    }

    @Test
    fun `migration adds private group labels without rewriting legacy person identity or contacts`() {
        AppDatabase.MIGRATION_17_18.migrate(db)

        db.query(
            "SELECT id, name, designation, station, phone, tier, cadenceOverrideDays, " +
                "lastInteractionAt, vaultMode FROM persons WHERE id = 'person-1'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("person-1", cursor.getString(0))
            assertEquals("K. Ramu", cursor.getString(1))
            assertEquals("Inspector", cursor.getString(2))
            assertEquals("North Unit", cursor.getString(3))
            assertEquals("+919876543210", cursor.getString(4))
            assertEquals("Inner", cursor.getString(5))
            assertEquals(14, cursor.getInt(6))
            assertEquals(1_788_200_000_000L, cursor.getLong(7))
            assertEquals("hidden", cursor.getString(8))
        }

        db.query("PRAGMA table_info(group_labels)").use { cursor ->
            val columns = buildSet {
                while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
            }
            assertEquals(
                setOf("id", "name", "responsiblePersonId", "createdAt", "updatedAt"),
                columns,
            )
        }
        db.query("SELECT COUNT(*) FROM group_labels").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
    }
}
