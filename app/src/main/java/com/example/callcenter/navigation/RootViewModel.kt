package com.example.callcenter.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.callcenter.data.prefs.AppPreferences
import com.example.callcenter.data.repository.AuthRepository
import com.example.callcenter.telephony.IncomingCallCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class RootViewModel @Inject constructor(
    authRepository: AuthRepository,
    private val appPrefs: AppPreferences,
    incomingCalls: IncomingCallCoordinator,
    private val leadsRepo: com.example.callcenter.data.repository.LeadsRepository,
    private val callbacksRepo: com.example.callcenter.data.repository.CallbacksRepository,
    private val agentRepo: com.example.callcenter.data.repository.AgentRepository,
) : ViewModel() {
    val authStatus: StateFlow<com.example.callcenter.domain.model.AuthStatus> = authRepository.authStatus

    /**
     * Silent re-fetch of the stale-prone data when the app returns to the
     * foreground: leads (statuses may have been re-bucketed server-side),
     * callbacks, and the agent profile (an admin may have flipped call_mode).
     * Also self-heals a launch that happened with no connectivity (the initial
     * fetches fail with UnknownHost and would otherwise sit stale until a
     * manual pull-to-refresh). No-op when not logged in. Each fetch is
     * independent — one failing must not stop the others.
     */
    fun refreshOnForeground() {
        if (authStatus.value != com.example.callcenter.domain.model.AuthStatus.AUTHENTICATED) return
        viewModelScope.launch { leadsRepo.refresh() }
        viewModelScope.launch { callbacksRepo.refresh() }
        viewModelScope.launch { agentRepo.loadProfile() }
    }

    /**
     * Fires when an answered incoming SIM call needs its outcome logged — the UI
     * routes straight to the incoming disposition screen (same accountability as
     * app-dialed calls, but cancellable: a personal call isn't business).
     */
    val incomingDispositions: SharedFlow<Unit> = incomingCalls.dispositionRequests

    /**
     * A disposition the agent still owes from a prior session (call id + lead id),
     * or null if none. Used on cold start to route straight back to the disposition
     * screen so a call can't be abandoned by killing the app.
     */
    suspend fun pendingDisposition(): Pair<String, Int>? {
        val prefs = appPrefs.current()
        val callId = prefs.pendingDispositionCall
        return if (callId.isNotBlank()) callId to prefs.pendingDispositionLead else null
    }

    /**
     * True if an answered incoming call is still awaiting its outcome (app killed
     * before the agent submitted or cancelled). Cold start routes back to the
     * incoming disposition screen, which holds the un-POSTed call.
     */
    suspend fun hasPendingIncoming(): Boolean =
        appPrefs.current().pendingIncomingNumber.isNotBlank()
}
