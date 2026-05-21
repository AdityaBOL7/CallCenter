package com.example.callcenter.ui.screens.leads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.callcenter.data.repository.CampaignsRepository
import com.example.callcenter.data.repository.LeadsRepository
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

data class LeadListUiState(
    val leads: List<Lead> = emptyList(),
    val campaigns: List<Campaign> = emptyList(),
    val filters: LeadFilters = LeadFilters(),
    val loading: Boolean = true,
    val importOpen: Boolean = false,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class LeadListViewModel @Inject constructor(
    private val leadsRepo: LeadsRepository,
    private val campaignsRepo: CampaignsRepository,
) : ViewModel() {

    private val _filters = MutableStateFlow(LeadFilters())
    private val _importOpen = MutableStateFlow(false)

    private val debouncedFilters = _filters.debounce(280).distinctUntilChanged()

    val state: StateFlow<LeadListUiState> = combine(
        leadsRepo.observeLeads(debouncedFilters),
        campaignsRepo.observeCampaigns(),
        _filters,
        _importOpen,
    ) { leads, campaigns, filters, importOpen ->
        LeadListUiState(
            leads = leads,
            campaigns = campaigns,
            filters = filters,
            loading = false,
            importOpen = importOpen,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LeadListUiState())

    fun setSearch(v: String) = _filters.update { it.copy(search = v) }
    fun setStatus(status: LeadStatus?) = _filters.update { it.copy(status = status) }
    fun setCampaign(campaignId: Int?) = _filters.update { it.copy(campaignId = campaignId) }
    fun setSort(sort: LeadSort) = _filters.update { it.copy(sort = sort) }
    fun openImport() { _importOpen.value = true }
    fun closeImport() { _importOpen.value = false }
}
