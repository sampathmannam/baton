package com.baton.app.features.audit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baton.app.data.audit.AuditChainVerifier
import com.baton.app.data.audit.VerifyResult
import com.baton.app.data.local.AuditChainEventDao
import com.baton.app.data.local.entities.AuditChainEventEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * v2.0 (PM rating): the audit-log ViewModel.
 *
 * Exposes:
 *  - [events]: a StateFlow of the most recent audit rows
 *    (newest first). The DAO's [AuditChainEventDao.observeAll]
 *    returns the rows in id-ASC order, so we reverse on the
 *    consumer side to put the newest at the top.
 *  - [state]: the verify-chain result. The user taps
 *    "Verify chain" → we run [AuditChainVerifier.verify] on
 *    the IO dispatcher and update the state.
 *
 * The chain walk is O(n) but n is small (the chain is
 * expected to be a few thousand rows over a year of normal
 * use). We don't paginate — the LazyColumn virtualises the
 * list and the verifier result is a single sealed-class
 * value.
 */
@HiltViewModel
class AuditLogViewModel @Inject constructor(
    private val dao: AuditChainEventDao,
    private val verifier: AuditChainVerifier,
) : ViewModel() {

    /**
     * The full chain, oldest-first. The screen reverses
     * for display ("newest first") in the LazyColumn.
     */
    val events: StateFlow<List<AuditChainEventEntity>> = dao.observeAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    private val _state = MutableStateFlow<VerifyState>(VerifyState.Idle)
    val state: StateFlow<VerifyState> = _state.asStateFlow()

    fun verify() {
        if (_state.value is VerifyState.Running) return
        _state.value = VerifyState.Running
        viewModelScope.launch {
            _state.value = when (val r = verifier.verify()) {
                is VerifyResult.Intact -> VerifyState.Intact(r.eventCount)
                is VerifyResult.BrokenAt -> VerifyState.Broken(r)
            }
        }
    }

    sealed class VerifyState {
        object Idle : VerifyState()
        object Running : VerifyState()
        data class Intact(val eventCount: Int) : VerifyState()
        data class Broken(val result: VerifyResult.BrokenAt) : VerifyState()
    }
}
