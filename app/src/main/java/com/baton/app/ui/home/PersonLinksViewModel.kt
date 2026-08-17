package com.baton.app.ui.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baton.app.data.links.PersonLinkRepository
import com.baton.app.data.local.PersonDao
import com.baton.app.data.local.entities.PersonEntity
import com.baton.app.data.local.entities.PersonLinkEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * v2.0 Tier 2 (§2.12): ViewModel for the person-to-person
 * links row in [PersonDetailScreen]. Combines the links for
 * the person (both directions) with the person snapshot so
 * each link can render the target's name.
 */
@HiltViewModel
class PersonLinksViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val linkRepo: PersonLinkRepository,
    private val personDao: PersonDao,
) : ViewModel() {

    private val personId: String = savedStateHandle.get<String>(ARG_PERSON_ID) ?: ""

    val state: StateFlow<PersonLinksState> = combine(
        linkRepo.observeForPerson(personId),
        personDao.observeAll(),
    ) { links, people ->
        val byId = people.associateBy { it.id }
        PersonLinksState(
            links = links.mapNotNull { link ->
                val targetId = if (link.fromId == personId) link.toId else link.fromId
                byId[targetId]?.let { target ->
                    LinkRow(
                        targetId = targetId,
                        targetName = target.name,
                        relation = link.relation,
                        isOutgoing = link.fromId == personId,
                    )
                }
            },
            people = people.filter { it.id != personId },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PersonLinksState(),
    )

    fun add(targetId: String, relation: String) {
        if (personId.isEmpty() || targetId == personId) return
        viewModelScope.launch { linkRepo.add(personId, targetId, relation) }
    }

    fun delete(link: LinkRow) {
        if (personId.isEmpty()) return
        viewModelScope.launch {
            linkRepo.delete(
                fromId = if (link.isOutgoing) personId else link.targetId,
                toId = if (link.isOutgoing) link.targetId else personId,
                relation = link.relation,
            )
        }
    }

    companion object {
        const val ARG_PERSON_ID = "personId"
        val DEFAULT_RELATIONS: List<String> = listOf(
            "Reports to", "Knows", "Family of", "Classmate of",
        )
    }
}

data class PersonLinksState(
    val links: List<LinkRow> = emptyList(),
    val people: List<PersonEntity> = emptyList(),
)

data class LinkRow(
    val targetId: String,
    val targetName: String,
    val relation: String,
    val isOutgoing: Boolean,
)
