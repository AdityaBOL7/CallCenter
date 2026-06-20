package com.example.callcenter.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.callcenter.data.repository.AgentRepository
import com.example.callcenter.data.repository.CallsRepository
import com.example.callcenter.data.repository.LeadsRepository
import com.example.callcenter.domain.model.AgentStatus
import com.example.callcenter.domain.model.CallRouteType
import com.example.callcenter.domain.model.LeadStatus
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
            ) { agent, stats, leads ->
                // newLeads isn't part of the agent stats payload; derive the count
                // of newly-assigned leads from the real leads list instead.
                val newLeads = leads.count { it.status == LeadStatus.NEW }
                Triple(agent, stats.copy(newLeads = newLeads), agent?.status)
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
        viewModelScope.launch { leadsRepo.refresh() }
    }

    /** Pull-to-refresh / manual refresh of the dashboard KPIs. */
    fun refreshStats() {
        viewModelScope.launch { agentRepo.loadDashboard() }
    }

    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()

    /** Pull-to-refresh: KPIs + leads together. */
    fun pullRefresh() {
        viewModelScope.launch {
            _refreshing.value = true
            try {
                agentRepo.loadDashboard()
                leadsRepo.refresh()
            } finally {
                _refreshing.value = false
            }
        }
    }

    fun setStatus(status: AgentStatus) {
        viewModelScope.launch { agentRepo.setStatus(status) }
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
