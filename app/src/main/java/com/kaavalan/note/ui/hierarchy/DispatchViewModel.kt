package com.kaavalan.note.ui.hierarchy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kaavalan.note.data.instructions.AudienceRef
import com.kaavalan.note.data.instructions.DeliveryService
import com.kaavalan.note.data.instructions.InstructionRepository
import com.kaavalan.note.data.instructions.Priority
import com.kaavalan.note.data.instructions.RosterBuilder
import com.kaavalan.note.data.instructions.RosterPicker
import com.kaavalan.note.data.instructions.Source
import com.kaavalan.note.data.person.Person
import com.kaavalan.note.data.person.PersonRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DispatchViewModel @Inject constructor(
    private val instructionRepository: InstructionRepository,
    private val personRepository: PersonRepository,
    private val deliveryService: DeliveryService,
) : ViewModel() {

    data class State(
        val audience: AudienceRef? = null,
        val dueAtMs: Long? = null,
        val channels: Set<DeliveryService.Channel> = setOf(DeliveryService.Channel.SMS),
        val recipientCount: Int = 0,
        val lastResult: DeliveryService.Result? = null,
        val roster: RosterPicker = RosterPicker(emptyList(), emptyList()),
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init { viewModelScope.launch { refreshRoster() } }

    fun setAudience(a: AudienceRef?) { _state.update { it.copy(audience = a, recipientCount = computeRecipients(a, it.roster)) } }
    fun setDue(dueAtMs: Long?) { _state.update { it.copy(dueAtMs = dueAtMs) } }
    fun toggleChannel(channel: DeliveryService.Channel) { _state.update { val next = if (channel in it.channels) it.channels - channel else it.channels + channel; it.copy(channels = if (next.isEmpty()) setOf(channel) else next) } }

    fun refreshRoster() {
        viewModelScope.launch {
            val people: List<Person> = personRepository.observeAll().first()
            val roster = RosterBuilder.build(people)
            _state.update { it.copy(roster = roster, recipientCount = computeRecipients(it.audience, roster)) }
        }
    }

    fun submit(title: String, rawText: String, senderName: String, senderDesignation: String?, senderDivision: String?, onDone: (String) -> Unit) {
        val s = _state.value
        val audience = s.audience
        if (audience == null) {
            viewModelScope.launch {
                val ins = instructionRepository.create(personId = null, source = Source.TEXT, priority = Priority.NORMAL, title = title.ifBlank { rawText.take(60) }, rawText = rawText, dueAt = null)
                onDone(ins.id)
            }
            return
        }
        viewModelScope.launch {
            val ins = instructionRepository.createWithAudience(personId = null, audience = audience, source = Source.TEXT, priority = Priority.NORMAL, title = title.ifBlank { rawText.take(60) }, rawText = rawText, dueAt = null, dueAtMs = s.dueAtMs, channel = s.channels.joinToString(",") { it.name }.ifBlank { null })
            val request = DeliveryService.DeliveryRequest(instructionId = ins.id, title = ins.title, body = rawText, audience = audience, dueAtMs = s.dueAtMs, channels = s.channels, senderName = senderName, senderDesignation = senderDesignation, senderDivision = senderDivision)
            val result = deliveryService.dispatch(request, s.roster)
            instructionRepository.setChannel(id = ins.id, channel = s.channels.joinToString(",") { it.name }.ifBlank { null })
            _state.update { it.copy(lastResult = result) }
            if (result.failed == 0 && result.sent > 0) instructionRepository.markDone(ins.id, java.time.Instant.now().toString())
            onDone(ins.id)
        }
    }

    private fun computeRecipients(audience: AudienceRef?, roster: RosterPicker): Int {
        if (audience == null) return 0
        return when (audience) {
            is AudienceRef.ByPerson -> if (roster.allPeople.any { it.id == audience.personId }) 1 else 0
            is AudienceRef.ByDesignation -> roster.peopleByDesignation(audience.designation).size
            is AudienceRef.ByStation -> roster.stations.firstOrNull { it.station.equals(audience.station, ignoreCase = true) }?.totalPeople ?: 0
            is AudienceRef.ByAll -> roster.totalPeople
        }
    }
}
