package com.example.callcenter.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.callcenter.data.repository.AgentRepository
import com.example.callcenter.data.repository.CallsRepository
import com.example.callcenter.data.repository.LeadsRepository
import com.example.callcenter.domain.model.AgentStatus
import com.example.callcenter.domain.model.CallRouteType
import com.example.callcenter.domain.model.Disposition
import com.example.callcenter.domain.model.LeadStatus
import java.time.LocalDate
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val agentRepo: AgentRepository,
    private val leadsRepo: LeadsRepository,
    private val callsRepo: CallsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                agentRepo.agent,
                agentRepo.stats,
                leadsRepo.leads,
                callsRepo.observeHistory(),
            ) { agent, stats, leads, history ->
                // The "Today's overview" tiles + performance card are TODAY-scoped
                // and derived client-side from the real call history (works for SIM
                // calls, where the backend can't report connection status). The
                // /dashboard/ payload only carries account totals; /leads/ has no
                // date filter.
                //  • newLeads   = leads still NEW
                //  • totalLeads = leads CREATED today            ("Leads today")
                //  • talkTime   = summed duration of TODAY's calls ("Talk time")
                //  • totalCalls = TODAY's calls  } "Today's performance" card:
                //  • connectedCalls = TODAY's WINS } the card shows wins / calls and
                //    connectionRate = wins ÷ calls. A "win" = disposition
                //    interested or converted (matches the Reports screen).
                //  stats.talkTime (account total) is kept on the model; we only
                //  override the tile/card values here.
                val today = LocalDate.now()
                val todaysCalls = history.filter { it.startedAt.toLocalDate() == today }
                val wins = todaysCalls.count {
                    it.disposition == Disposition.INTERESTED || it.disposition == Disposition.CONVERTED
                }
                val newLeads = leads.count { it.status == LeadStatus.NEW }
                val leadsToday = leads.count { it.createdAt?.toLocalDate() == today }
                val talkTimeToday = formatTalkTime(todaysCalls.sumOf { it.durationSec })
                // Like the other tiles, recordings must be TODAY + THIS AGENT —
                // the /dashboard/ payload's `recordings` is an all-time tenant
                // total (showed "3" after two calls, 2026-07-18).
                val recordingsToday = todaysCalls.count { it.hasRecording }
                Triple(
                    agent,
                    stats.copy(
                        newLeads = newLeads,
                        totalLeads = leadsToday,
                        talkTime = talkTimeToday,
                        totalCalls = todaysCalls.size,
                        connectedCalls = wins,
                        recordings = recordingsToday,
                    ),
                    agent?.status,
                )
            }.collect { (agent, stats, status) ->
                _state.update {
                    it.copy(
                        agent = agent,
                        stats = stats,
                        status = status ?: it.status,
                        loading = false,
                    )
                }
            }
        }
        viewModelScope.launch { agentRepo.loadProfile() }
        viewModelScope.launch { agentRepo.loadDashboard() }
        viewModelScope.launch { callsRepo.refreshHistory() }
        viewModelScope.launch { leadsRepo.refresh() }
    }

    /** Pull-to-refresh / manual refresh of the dashboard KPIs. */
    fun refreshStats() {
        viewModelScope.launch { agentRepo.loadDashboard() }
    }

    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()

    /** Pull-to-refresh: KPIs + leads + call history together. */
    fun pullRefresh() {
        viewModelScope.launch {
            _refreshing.value = true
            try {
                agentRepo.loadDashboard()
                leadsRepo.refresh()
                callsRepo.refreshHistory()
            } finally {
                _refreshing.value = false
            }
        }
    }

    // One-shot error message for a failed status change; the screen toasts it.
    // Without this the chip fails silently and the agent has no idea (the
    // 2026-07-16 dead-session incident looked exactly like "status is broken").
    private val _statusError = MutableStateFlow<String?>(null)
    val statusError: StateFlow<String?> = _statusError.asStateFlow()
    fun clearStatusError() { _statusError.value = null }

    fun setStatus(status: AgentStatus) {
        viewModelScope.launch {
            agentRepo.setStatus(status).onFailure { e ->
                _statusError.value = if ((e as? retrofit2.HttpException)?.code() == 401) {
                    "Session expired — please log in again."
                } else {
                    "Couldn't change status. Check your connection."
                }
            }
        }
    }

    fun getNextLead(
        onCall: (Int, String, String) -> Unit,
        onNotAvailable: () -> Unit = {},
    ) {
        viewModelScope.launch {
            // Agents may only call while Available.
            if (!callsRepo.canCall()) {
                onNotAvailable()
                return@launch
            }
            _state.update { it.copy(gettingNext = true) }
            val lead = leadsRepo.nextLead()
            _state.update { it.copy(gettingNext = false, nextLead = lead) }
            if (lead != null) {
                val route = agentRepo.agent.value?.callMode ?: CallRouteType.SIP
                val call = callsRepo.initiate(lead, route)
                onCall(lead.id, call.id, call.routeType.name)
            }
        }
    }
}

/** Seconds → compact talk-time label: "0m", "13m", "1h 5m". */
private fun formatTalkTime(totalSeconds: Int): String {
    if (totalSeconds <= 0) return "0m"
    val minutes = totalSeconds / 60
    val hours = minutes / 60
    val mins = minutes % 60
    return if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
}
