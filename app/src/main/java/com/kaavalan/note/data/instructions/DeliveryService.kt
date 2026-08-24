package com.baton.app.data.instructions

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.baton.app.data.local.DeliveryReceiptDao
import com.baton.app.data.local.entities.DeliveryReceiptEntity
import com.baton.app.data.person.Person
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class DeliveryService @Inject constructor(@ApplicationContext private val context: Context, private val dao: DeliveryReceiptDao) {
    data class DeliveryRequest(val instructionId: String, val title: String, val body: String, val audience: AudienceRef, val dueAtMs: Long?, val channels: Set<Channel>, val senderName: String, val senderDesignation: String?, val senderDivision: String?)
    enum class Channel { SMS, WHATSAPP }
    data class Result(val recipients: Int, val sent: Int, val failed: Int)
    suspend fun dispatch(request: DeliveryRequest, roster: RosterPicker): Result {
        val recipients = AudienceResolver.resolve(request.audience, roster)
        if (recipients.isEmpty()) return Result(0, 0, 0)
        val wrapped = HeaderTemplate.wrap(body = request.body, inputs = HeaderTemplate.Inputs(senderName = request.senderName, senderDesignation = request.senderDesignation, senderDivision = request.senderDivision, dueAtMs = request.dueAtMs, shortRef = HeaderTemplate.shortRefFor(request.instructionId)))
        val now = Instant.now(); var sent = 0; var failed = 0
        for (person in recipients) {
            val phone = person.phone?.takeIf { it.isNotBlank() }
            if (phone == null) { for (channel in request.channels) { dao.upsert(receipt(request.instructionId, person, channel, DeliveryReceipt.Status.FAILED, "No phone on file", now)); failed++ }; continue }
            for (channel in request.channels) {
                val ok = when (channel) { Channel.SMS -> sendSms(phone, wrapped); Channel.WHATSAPP -> sendWhatsApp(phone, wrapped) }
                dao.upsert(receipt(request.instructionId, person, channel, if (ok) DeliveryReceipt.Status.SENT else DeliveryReceipt.Status.FAILED, if (ok) null else "${channel.name} not available", now))
                if (ok) sent++ else failed++
            }
        }
        return Result(recipients.size, sent, failed)
    }
    private fun sendSms(phone: String, body: String): Boolean { val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phone")).apply { putExtra("sms_body", body); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }; return fireIntent(intent) }
    private fun sendWhatsApp(phone: String, body: String): Boolean { val stripped = phone.filter { it.isDigit() || it == '+' }; val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$stripped?text=" + Uri.encode(body))).apply { setPackage("com.whatsapp"); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }; return fireIntent(intent) }
    private fun fireIntent(intent: Intent): Boolean { val resolved = intent.resolveActivity(context.packageManager) ?: return false; return try { context.startActivity(intent); true } catch (_: Throwable) { false } }
    private fun receipt(instructionId: String, person: Person, channel: Channel, status: DeliveryReceipt.Status, error: String?, at: Instant): DeliveryReceiptEntity = DeliveryReceiptEntity(id = newDeliveryReceiptId(), instructionId = instructionId, recipientPersonId = person.id, recipientName = person.name, recipientDesignation = person.designation, recipientPhone = person.phone, channel = channel.name, status = status.name, errorMessage = error, sentAt = at.toString())
}
