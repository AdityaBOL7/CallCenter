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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State for the call handoff screen. There are intentionally NO mute/hold/speaker
 * or recording toggles: the actual voice call runs in the phone's own telephony
 * stack (SIM: the app-placed carrier call; SIP: the click-to-call agent leg that
 * rings THIS phone), which the app can't drive — dead toggles would be lying.
 *
 * What the app CAN honestly observe for SIP is the agent leg's real call state
 * via telephony (READ_PHONE_STATE): incoming ring → picked up → ended. [status]
 * mirrors that live (INITIATING = originate fired, waiting for the leg;
 * RINGING = this phone is ringing; CONNECTED = agent picked up), and
 * [connectedAtMs] stamps the real pickup so the screen can show a true elapsed
 * timer. SIM never uses these — its call lives in the device dialer UI.
 */
data class CallUiState(
    val customerName: String = "",
    val customerPhone: String = "",
    val campaignName: String = "",
    val status: CallStatus = CallStatus.INITIATING,
    val connectedAtMs: Long? = null,
    val noteOpen: Boolean = false,
    val note: String = "",
    // SIP only: set once the wrap-up work (note PATCH) is done and the screen
    // should navigate to disposition. Navigation is driven by the COMPOSITION
    // observing this flag — never by a captured callback — so a rotation during
    // the wrap-up can't strand the agent: the new composition sees the flag and
    // navigates with the live NavController.
    val leaveForDisposition: Boolean = false,
    // SIP only: reveal the "log outcome & continue" escape hatch. The waiting
    // screen has NO end-call button (the phone's own call UI owns accept/hangup),
    // so when the auto-advance can never fire — telephony watcher unavailable
    // (READ_PHONE_STATE denied) or no agent leg arrived within the timeout
    // (failed/stuck originate) — this is the agent's only way off the screen.
    val showProceedFallback: Boolean = false,
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

    /**
     * SIM places a real carrier call from THIS screen (device dialer). SIP was
     * already originated server-side in CallsRepository.initiate() before we got
     * here, so the SIP handoff has nothing to dial — it just shows the connecting
     * message and proceeds to disposition.
     */
    val isSim: Boolean = route == CallRouteType.SIM

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
            // No fabricated ringing/connected timeline: neither route exposes a
            // voice path we can observe (see CallUiState). The authoritative
            // outcome is written once at hangup — with the real talk duration for
            // SIM (read from the device call log).
        }
        // SIP has no end-call button, so a leg that never arrives (failed/stuck
        // originate) would strand the agent forever. Two escape-hatch triggers:
        //  - originate FAILED (no backend call id — initiate() ran before this
        //    screen, so the repo state is final): no call is EVER coming, show
        //    the hatch immediately;
        //  - originate accepted but nothing rang within the provider's worst
        //    realistic latency: show it after a timeout. Purely additive — the
        //    auto-advance still wins if the leg shows up late.
        if (!isSim) {
            if (!callsRepo.hasActiveCallId()) {
                _state.update { it.copy(showProceedFallback = true) }
            } else {
                viewModelScope.launch {
                    kotlinx.coroutines.delay(45_000)
                    if (_state.value.status == CallStatus.INITIATING) {
                        _state.update { it.copy(showProceedFallback = true) }
                    }
                }
            }
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
     * (PhoneCaller.call returned true). Reflects the call as RINGING on the
     * backend — so a denied permission or failed dial leaves no phantom live
     * call. NOT "connected": at this point the callee hasn't picked up, and the
     * backend stamps answered_at on a connected transition — which fabricated a
     * pickup 0.5s after dialing and a fake duration for never-answered calls
     * (call id=73, 2026-07-16). Pickup truth only exists in the device call log,
     * reported at hangup. Idempotent: safe if invoked more than once. SIP does
     * NOT use this: its lifecycle is owned by the server-side originate.
     */
    fun markSimConnected() {
        if (_state.value.status == CallStatus.RINGING) return
        _state.update { it.copy(status = CallStatus.RINGING) }
        viewModelScope.launch { callsRepo.patchCall(status = CallStatus.RINGING) }
    }

    /** The carrier call just ended (OFFHOOK → IDLE) — see CallsRepository.markSimCallEnded. */
    fun markSimEnded() = callsRepo.markSimCallEnded()

    // One-shot guard for leaving the SIP waiting screen: the auto-advance (agent
    // leg went IDLE) and the manual End button can race — whoever fires first
    // wins, the other becomes a no-op, so the wrap-up never runs twice.
    private val sipProceeded = java.util.concurrent.atomic.AtomicBoolean(false)

    // Facts observed about the agent leg (real transitions only, never the
    // registration snapshot). sipSawLeg = some call rang/connected while this
    // screen watched; sipLegAnswered = it was actually picked up. Together they
    // let proceedSip() report an honest no_answer when the leg rang but was
    // never answered — instead of the backend defaulting to "completed".
    private var sipSawLeg = false
    private var sipLegAnswered = false

    /**
     * Feed of device call states while the SIP waiting screen is up, mirroring
     * the click-to-call agent leg (the backend calls this phone first, then
     * bridges the lead).
     *
     * [isInitial] marks telephony's registration snapshot — the state that was
     * ALREADY in effect when the watcher registered (e.g. OFFHOOK because a
     * previous or personal call is still live). It is not a transition of OUR
     * agent leg and is ignored entirely: acting on it made a brand-new call
     * adopt a stale call's lifecycle (fake "In call", then a bogus auto-advance
     * to disposition when the stale call ended).
     *
     * Transitions:
     *  - RINGING  → (from INITIATING) the agent leg arrived; "your phone is ringing".
     *  - OFFHOOK  → (from RINGING only) the agent picked up; stamp the real
     *               pickup time, mirror connected to the backend (it stamps
     *               answered_at on the first connected PATCH). OFFHOOK without
     *               an observed RINGING is some other call (e.g. an outgoing
     *               one the agent placed themselves) — ignored.
     *  - IDLE     → from CONNECTED: the call ended → record the true talk
     *               seconds and auto-advance to disposition.
     *               From RINGING: the ring stopped without a pickup — the agent
     *               declined the leg or let it ring out. The screen has NO end
     *               button (the phone's call UI owns accept/hangup — product
     *               decision 2026-07-17), so this also auto-advances, reported
     *               honestly as no_answer via [proceedSip]. Accepted trade-off:
     *               a personal call declined during the waiting window is
     *               indistinguishable from the leg and advances too.
     */
    fun onSipCallState(state: Int, isInitial: Boolean) {
        if (isInitial) return
        when (state) {
            android.telephony.TelephonyManager.CALL_STATE_RINGING ->
                if (_state.value.status == CallStatus.INITIATING) {
                    sipSawLeg = true
                    _state.update { it.copy(status = CallStatus.RINGING) }
                }
            android.telephony.TelephonyManager.CALL_STATE_OFFHOOK ->
                if (_state.value.status == CallStatus.RINGING) {
                    sipLegAnswered = true
                    _state.update {
                        it.copy(
                            status = CallStatus.CONNECTED,
                            connectedAtMs = System.currentTimeMillis(),
                        )
                    }
                    // Honest state mirror: the agent REALLY picked up. The
                    // backend stamps answered_at on the first connected PATCH.
                    viewModelScope.launch { callsRepo.patchCall(status = CallStatus.CONNECTED) }
                }
            android.telephony.TelephonyManager.CALL_STATE_IDLE ->
                when (_state.value.status) {
                    CallStatus.CONNECTED -> {
                        // True talk seconds = real pickup → real hang-up. Handed
                        // to the repository so the disposition hangup reports it
                        // (the backend would otherwise compute duration up to
                        // the disposition SUBMIT, inflating talk time).
                        _state.value.connectedAtMs?.let { connectedAt ->
                            val talk = ((System.currentTimeMillis() - connectedAt) / 1000L)
                                .toInt().coerceAtLeast(0)
                            callsRepo.markSipTalkSeconds(talk)
                        }
                        proceedSip()
                    }
                    CallStatus.RINGING -> proceedSip()
                    else -> Unit
                }
        }
    }

    /**
     * The telephony watcher can't run (READ_PHONE_STATE denied / no telephony) —
     * the auto-advance will never fire, so reveal the escape hatch immediately.
     */
    fun onWatcherUnavailable() {
        _state.update { it.copy(showProceedFallback = true) }
    }

    /**
     * Wrap up the SIP waiting screen, exactly once — used by both the
     * auto-advance (leg ended) and the manual End button. Persists the
     * never-answered fact and any typed note, THEN flips [CallUiState.leaveForDisposition];
     * the composition observes that flag and navigates. No captured navigation
     * callback: a rotation during the note PATCH must not lose the handoff.
     */
    fun proceedSip() {
        if (!sipProceeded.compareAndSet(false, true)) return
        // The leg rang while we watched but was never picked up → the eventual
        // hangup must say no_answer, not "completed".
        if (sipSawLeg && !sipLegAnswered) callsRepo.markSipNeverAnswered()
        viewModelScope.launch {
            _state.update { it.copy(status = CallStatus.COMPLETED) }
            try {
                _state.value.note.takeIf { it.isNotBlank() }?.let { inCallNote ->
                    callsRepo.patchCall(note = inCallNote)
                }
            } finally {
                _state.update { it.copy(leaveForDisposition = true) }
            }
        }
    }

    fun openNote() = _state.update { it.copy(noteOpen = true) }
    fun closeNote() = _state.update { it.copy(noteOpen = false) }
    fun setNote(v: String) = _state.update { it.copy(note = v) }
    fun saveNote() = _state.update { it.copy(noteOpen = false) }

    fun hangup(onEnded: () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(status = CallStatus.COMPLETED) }
            // Persist any pre-disposition note via a plain PATCH (not a hangup), so
            // it's saved without ending the call. The authoritative backend hangup
            // (POST /calls/:id/hangup/) happens exactly once, in
            // DispositionViewModel.submit() with the disposition — calling it here
            // too would double-hang-up and could drop the outcome.
            _state.value.note.takeIf { it.isNotBlank() }?.let { inCallNote ->
                callsRepo.patchCall(note = inCallNote)
            }
            onEnded()
        }
    }
}
