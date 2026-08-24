package com.kaavalan.note.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "delivery_receipts", indices = [Index(value = ["instructionId"]), Index(value = ["status"]), Index(value = ["channel"])])
data class DeliveryReceiptEntity(@PrimaryKey val id: String, val instructionId: String, val recipientPersonId: String, val recipientName: String, val recipientDesignation: String?, val recipientPhone: String?, val channel: String, val status: String, val errorMessage: String?, val sentAt: String, val syncStatus: String = SyncStatus.SYNCED)
