package com.baton.app.data.user

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(user: UserEntity)

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): UserEntity?

    @Query("SELECT * FROM users WHERE deviceOwner = 1 LIMIT 1")
    suspend fun deviceOwner(): UserEntity?

    @Query("SELECT * FROM users WHERE deviceOwner = 1 LIMIT 1")
    fun observeDeviceOwner(): Flow<UserEntity?>

    /**
     * v2.1.1 (security): a real `UPDATE` that
     * exercises the write path without deleting +
     * reinserting the row.
     *
     * v2.1.0's [DatabasePreflight] used
     * [upsert] (which is `@Insert(onConflict = REPLACE)`)
     * to "no-op" the device-owner row's `displayName`.
     * REPLACE actually **deletes** the existing row
     * and inserts a new one with a new rowid, which:
     *
     *  1. Skews the `id`-to-rowid mapping (a row
     *     updated 1,000 times has 1,000 different
     *     rowids in WAL).
     *  2. Triggers foreign-key cascades for any child
     *     tables that reference the user — the user
     *     row is briefly gone.
     *  3. Makes the read-back `equals` check in
     *     `DatabasePreflight` unreliable (the rowid
     *     field is part of Room's identity but not
     *     part of the `data class UserEntity`'s
     *     `equals`, so this is benign for the preflight
     *     — but the wasted work isn't).
     *
     * The fix: a real `UPDATE` that touches the row
     * in place. The `displayName = displayName`
     * assignment is a literal no-op in terms of
     * stored values, but SQLite still exercises the
     * write path (and the IO that the preflight is
     * trying to verify).
     *
     * Returns the number of rows updated (0 if the
     * device-owner row is missing, 1 on success).
     */
    @Query("UPDATE users SET displayName = displayName WHERE id = :id")
    suspend fun touch(id: String): Int
}
