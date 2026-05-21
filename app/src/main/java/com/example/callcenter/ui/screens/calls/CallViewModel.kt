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
            // Scripted state machine: initiating → ringing → connected
            delay(1500)
            _state.update { it.copy(status = CallStatus.RINGING) }
            delay(2000)
            _state.update { it.copy(status = CallStatus.CONNECTED) }
            startTimer()
        }
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

    fun toggleMute() = _state.update { it.copy(muted = !it.muted) }
    fun toggleHold() = _state.update { it.copy(onHold = !it.onHold) }
    fun toggleSpeaker() = _state.update { it.copy(speaker = !it.speaker) }
    fun toggleRecording() = _state.update { it.copy(recording = !it.recording) }
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
            callsRepo.hangup(note = _state.value.note.takeIf { it.isNotBlank() })
            delay(700)
            onEnded()
        }
    }
}
