package com.example.callcenter.ui.screens.calls

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.callcenter.data.repository.CallsRepository
import com.example.callcenter.data.repository.LeadsRepository
import com.example.callcenter.domain.model.CallRouteType
import com.example.callcenter.domain.model.CallStatus
import com.example.callcenter.navigation.Dest
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class CallUiState(
    val customerName: String = "",
    val customerPhone: String = "",
    val campaignName: String = "",
    val status: CallStatus = CallStatus.INITIATING,
    val durationSec: Int = 0,
    val muted: Boolean = false,
    val onHold: Boolean = false,
    val speaker: Boolean = false,
    val recording: Boolean = true,
    val keypadOpen: Boolean = false,
    val noteOpen: Boolean = false,
    val transferOpen: Boolean = false,
    val dialed: String = "",
    val note: String = "",
    val transferTo: String = "",
)

@HiltViewModel
class CallViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val callsRepo: CallsRepository,
    private val leadsRepo: LeadsRepository,
) : ViewModel() {

    private val callId: String = savedStateHandle[Dest.Call.ARG_CALL] ?: ""
    private val leadId: Int = savedStateHandle.get<String>(Dest.Call.ARG_LEAD)?.toIntOrNull() ?: 0
    val route: CallRouteType = runCatching {
        CallRouteType.valueOf(savedStateHandle[Dest.Call.ARG_ROUTE] ?: "SIP")
    }.getOrDefault(CallRouteType.SIP)

    /** SIM mode places a real carrier call; SIP runs the in-app VOIP screen. */
    val isSim: Boolean = route == CallRouteType.MOBILE

    private val _state = MutableStateFlow(CallUiState())
    val state: StateFlow<CallUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val lead = leadsRepo.byId(leadId)
            _state.update {
                it.copy(
                    customerName = lead?.name ?: "Unknown",
                    customerPhone = lead?.phone ?: "",
                    campaignName = lead?.campaignName ?: "",
                )
            }
            // SIM mode hands the call to the system dialer (see CallScreen). We do
            // NOT mark it connected here — the carrier call hasn't been placed yet,
            // and may never be (permission denied / blank number). SimCallScreen
            // calls onPlaced() → markSimConnected() once the dial actually fires,
            // so the backend never records a phantom connected call.
            if (isSim) return@launch
            // Scripted state machine: initiating → ringing → connected.
            // Each transition is mirrored to the backend call record (best-effort).
            delay(1500)
            _state.update { it.copy(status = CallStatus.RINGING) }
            callsRepo.patchCall(status = CallStatus.RINGING)
            delay(2000)
            _state.update { it.copy(status = CallStatus.CONNECTED) }
            callsRepo.patchCall(status = CallStatus.CONNECTED)
            startTimer()
        }
    }

    // True once we've fired (or attempted) the SIM carrier call. Lives in the
    // ViewModel — which survives configuration changes (rotation) and the system
    // dialer coming to the foreground — so the call is placed exactly once and a
    // recomposition can't re-dial the customer. (See bug: re-dial on rotation.)
    private var simDialAttempted = false

    /**
     * Returns true exactly once, the first time SimCallScreen is ready to place
     * the carrier call. Subsequent calls (after rotation / recomposition) return
     * false, so PhoneCaller.call() never fires a second time.
     */
    fun claimSimDial(): Boolean {
        if (simDialAttempted) return false
        simDialAttempted = true
        return true
    }

    /**
     * Called by SimCallScreen once the real carrier call has actually been placed
     * (PhoneCaller.call returned true). Only now do we record the call as connected
     * on the backend — so a denied permission or failed dial leaves no phantom
     * connected call. Idempotent: safe if invoked more than once.
     */
    fun markSimConnected() {
        if (_state.value.status == CallStatus.CONNECTED) return
        _state.update { it.copy(status = CallStatus.CONNECTED) }
        viewModelScope.launch { callsRepo.patchCall(status = CallStatus.CONNECTED) }
    }

    private fun startTimer() {
        viewModelScope.launch {
            while (isActive && _state.value.status == CallStatus.CONNECTED) {
                delay(1000)
                _state.update {
                    if (it.status == CallStatus.CONNECTED && !it.onHold)
                        it.copy(durationSec = it.durationSec + 1)
                    else it
                }
            }
        }
    }

    fun toggleMute() {
        val muted = !_state.value.muted
        _state.update { it.copy(muted = muted) }
        viewModelScope.launch { callsRepo.patchCall(muted = muted) }
    }

    fun toggleHold() {
        val onHold = !_state.value.onHold
        _state.update { it.copy(onHold = onHold) }
        viewModelScope.launch {
            callsRepo.patchCall(
                status = if (onHold) CallStatus.ON_HOLD else CallStatus.CONNECTED,
                onHold = onHold,
            )
        }
    }

    fun toggleSpeaker() = _state.update { it.copy(speaker = !it.speaker) }

    fun toggleRecording() {
        val recording = !_state.value.recording
        _state.update { it.copy(recording = recording) }
        viewModelScope.launch { callsRepo.patchCall(recording = recording) }
    }
    fun openKeypad() = _state.update { it.copy(keypadOpen = true) }
    fun closeKeypad() = _state.update { it.copy(keypadOpen = false) }
    fun openNote() = _state.update { it.copy(noteOpen = true) }
    fun closeNote() = _state.update { it.copy(noteOpen = false) }
    fun openTransfer() = _state.update { it.copy(transferOpen = true) }
    fun closeTransfer() = _state.update { it.copy(transferOpen = false) }
    fun pressKey(k: String) = _state.update { it.copy(dialed = it.dialed + k) }
    fun backspaceKey() = _state.update { it.copy(dialed = it.dialed.dropLast(1)) }
    fun setNote(v: String) = _state.update { it.copy(note = v) }
    fun saveNote() = _state.update { it.copy(noteOpen = false) }
    fun setTransfer(v: String) = _state.update { it.copy(transferTo = v) }

    fun hangup(onEnded: () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(status = CallStatus.COMPLETED) }
            // Persist any in-call note via a plain PATCH (not a hangup), so it's
            // saved without ending the call. The authoritative backend hangup
            // (POST /calls/:id/hangup/) happens exactly once, in
            // DispositionViewModel.submit() with the disposition — calling it here
            // too would double-hang-up and could drop the outcome.
            _state.value.note.takeIf { it.isNotBlank() }?.let { inCallNote ->
                callsRepo.patchCall(note = inCallNote)
            }
            // Brief pause so the in-app SIP call screen can flash "Completed" before
            // navigating. SIM mode has no visible call screen (just a blank handoff
            // box), so skip the delay — otherwise the agent stares at a blank screen
            // for 700ms before the disposition page appears.
            if (!isSim) delay(700)
            onEnded()
        }
    }
}
