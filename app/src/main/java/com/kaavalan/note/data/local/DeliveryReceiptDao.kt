package com.kaavalan.note.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kaavalan.note.data.local.entities.DeliveryReceiptEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DeliveryReceiptDao {
    @Query("SELECT * FROM delivery_receipts WHERE instructionId = :instructionId ORDER BY recipientName COLLATE NOCASE ASC")
    fun observeForInstruction(instructionId: String): Flow<List<DeliveryReceiptEntity>>
    @Query("SELECT * FROM delivery_receipts WHERE instructionId = :instructionId")
    suspend fun snapshotForInstruction(instructionId: String): List<DeliveryReceiptEntity>
    @Query("SELECT COUNT(*) FROM delivery_receipts WHERE instructionId = :instructionId AND status = 'SENT'")
    fun observeSentCount(instructionId: String): Flow<Int>
    @Query("SELECT COUNT(*) FROM delivery_receipts WHERE instructionId = :instructionId AND status = 'FAILED'")
    fun observeFailedCount(instructionId: String): Flow<Int>
    @Query("SELECT COUNT(*) FROM delivery_receipts WHERE instructionId = :instructionId")
    fun observeTotalCount(instructionId: String): Flow<Int>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(receipt: DeliveryReceiptEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(receipts: List<DeliveryReceiptEntity>)
    @Query("DELETE FROM delivery_receipts WHERE instructionId = :instructionId") suspend fun deleteForInstruction(instructionId: String)
    @Query("DELETE FROM delivery_receipts") suspend fun deleteAll()
}
