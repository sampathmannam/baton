package com.kaavalan.note.data.instructions

import com.kaavalan.note.data.local.entities.DeliveryReceiptEntity
import com.kaavalan.note.data.local.entities.SyncStatus
import java.time.Instant
import java.util.UUID

data class DeliveryReceipt(val id: String, val instructionId: String, val recipientPersonId: String, val recipientName: String, val recipientDesignation: String?, val recipientPhone: String?, val channel: Channel, val status: Status, val errorMessage: String?, val sentAt: Instant) {
    enum class Channel { SMS, WHATSAPP }
    enum class Status { SENT, FAILED }
}
internal fun DeliveryReceiptEntity.toDomain(): DeliveryReceipt = DeliveryReceipt(id = id, instructionId = instructionId, recipientPersonId = recipientPersonId, recipientName = recipientName, recipientDesignation = recipientDesignation, recipientPhone = recipientPhone, channel = DeliveryReceipt.Channel.valueOf(channel), status = DeliveryReceipt.Status.valueOf(status), errorMessage = errorMessage, sentAt = Instant.parse(sentAt))
internal fun DeliveryReceipt.toEntity(): DeliveryReceiptEntity = DeliveryReceiptEntity(id = id, instructionId = instructionId, recipientPersonId = recipientPersonId, recipientName = recipientName, recipientDesignation = recipientDesignation, recipientPhone = recipientPhone, channel = channel.name, status = status.name, errorMessage = errorMessage, sentAt = sentAt.toString(), syncStatus = SyncStatus.SYNCED)
internal fun newDeliveryReceiptId(): String = UUID.randomUUID().toString()
