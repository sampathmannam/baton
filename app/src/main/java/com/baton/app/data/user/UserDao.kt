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
}
