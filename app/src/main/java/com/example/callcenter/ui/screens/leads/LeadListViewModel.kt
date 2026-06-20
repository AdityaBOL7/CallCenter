package com.example.callcenter.ui.screens.leads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.callcenter.data.repository.AgentRepository
import com.example.callcenter.data.repository.CallsRepository
import com.example.callcenter.data.repository.CampaignsRepository
import com.example.callcenter.data.repository.LeadsRepository
import com.example.callcenter.domain.model.AgentStatus
import com.example.callcenter.domain.model.CallRouteType
import com.example.callcenter.domain.model.Campaign
import com.example.callcenter.domain.model.Lead
import com.example.callcenter.domain.model.LeadFilters
import com.example.callcenter.domain.model.LeadSort
import com.example.callcenter.domain.model.LeadStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LeadListUiState(
    val leads: List<Lead> = emptyList(),
    val campaigns: List<Campaign> = emptyList(),
    val filters: LeadFilters = LeadFilters(),
    val loading: Boolean = true,
    val importOpen: Boolean = false,
    val error: String? = null,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class LeadListViewModel @Inject constructor(
    private val leadsRepo: LeadsRepository,
    private val campaignsRepo: CampaignsRepository,
    private val callsRepo: CallsRepository,
    private val agentRepo: AgentRepository,
) : ViewModel() {

    private val _filters = MutableStateFlow(LeadFilters())
    private val _importOpen = MutableStateFlow(false)
    private val _loading = MutableStateFlow(true)
    private val _startingAutoDial = MutableStateFlow(false)
    val startingAutoDial: StateFlow<Boolean> = _startingAutoDial.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val debouncedFilters = _filters.debounce(280).distinctUntilChanged()

    val state: StateFlow<LeadListUiState> = combine(
        leadsRepo.observeLeads(debouncedFilters),
        campaignsRepo.observeCampaigns(),
        _filters,
        _importOpen,
        combine(_loading, leadsRepo.error) { loading, error -> loading to error },
    ) { leads, campaigns, filters, importOpen, (loading, error) ->
        LeadListUiState(
            leads = leads,
            campaigns = campaigns,
            filters = filters,
            loading = loading,
            importOpen = importOpen,
            error = error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LeadListUiState())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            leadsRepo.refresh()
            _loading.value = false
        }
        viewModelScope.launch { campaignsRepo.refresh() }
    }

    /** Pull-to-refresh: re-fetch leads + campaigns without the first-load spinner. */
    fun pullRefresh() {
        viewModelScope.launch {
            _refreshing.value = true
            try {
                leadsRepo.refresh()
                campaignsRepo.refresh()
            } finally {
                _refreshing.value = false
            }
        }
    }

    fun setSearch(v: String) = _filters.update { it.copy(search = v) }
    fun setStatus(status: LeadStatus?) = _filters.update { it.copy(status = status) }
    fun setCampaign(campaignId: Int?) = _filters.update { it.copy(campaignId = campaignId) }
    fun setSort(sort: LeadSort) = _filters.update { it.copy(sort = sort) }
    fun openImport() { _importOpen.value = true }
    fun closeImport() { _importOpen.value = false }

    /**
     * Build the auto-dial queue (NEW leads, priority order) and start calling the
     * first one. The dial route (SIP/SIM) is the one the backend assigned to this
     * agent (me/.call_mode) — the agent does not choose it. Subsequent leads dial
     * automatically after each disposition.
     *
     * Calling is gated on presence: the agent must be Available. [onNotAvailable]
     * fires (and nothing is dialed) if they're on Break/Busy/Offline. [onEmpty]
     * fires when there's nothing to call.
     */
    fun startAutoDial(
        onCall: (leadId: Int, callId: String, route: String) -> Unit,
        onEmpty: () -> Unit,
        onNotAvailable: () -> Unit,
    ) {
        viewModelScope.launch {
            _startingAutoDial.value = true
            // call_mode is a stable per-agent setting (changes only when an admin
            // updates the agent), so this is a cached read — loadProfile() no-ops
            // when the profile is already in memory and only fetches on a cold start.
            agentRepo.loadProfile()
            val agent = agentRepo.agent.value
            // Edge case: agents may only place calls while Available.
            if (agent?.status != AgentStatus.AVAILABLE) {
                _startingAutoDial.value = false
                onNotAvailable()
                return@launch
            }
            val route = agent.callMode
            val queue = leadsRepo.queueForAutoDial()
            _startingAutoDial.value = false
            if (queue.isEmpty()) {
                onEmpty()
                return@launch
            }
            callsRepo.startAutoDial(queue.map { it.id }, route)
            val first = queue.first()
            val call = callsRepo.initiate(first, route)
            onCall(first.id, call.id, route.name)
        }
    }
}
