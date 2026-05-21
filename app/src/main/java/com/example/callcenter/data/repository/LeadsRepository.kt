package com.example.callcenter.data.repository

import com.example.callcenter.data.mock.MockData
import com.example.callcenter.domain.model.Lead
import com.example.callcenter.domain.model.LeadFilters
import com.example.callcenter.domain.model.LeadSort
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

@Singleton
class LeadsRepository @Inject constructor() {

    private val _leads = MutableStateFlow(MockData.leads)
    val leads: StateFlow<List<Lead>> = _leads.asStateFlow()

    fun observeLeads(filters: Flow<LeadFilters>): Flow<List<Lead>> =
        combine(_leads, filters) { source, f -> applyFilters(source, f) }

    fun observeLeads(filters: LeadFilters): Flow<List<Lead>> =
        _leads.map { applyFilters(it, filters) }

    fun byId(id: Int): Lead? = _leads.value.firstOrNull { it.id == id }

    suspend fun nextLead(): Lead? {
        delay(300)
        return _leads.value.firstOrNull()
    }

    private fun applyFilters(source: List<Lead>, filters: LeadFilters): List<Lead> {
        val text = filters.search.trim().lowercase()
        return source.asSequence()
            .filter { filters.status == null || it.status == filters.status }
            .filter { filters.campaignId == null || it.campaignId == filters.campaignId }
            .filter {
                text.isEmpty() ||
                    it.name.lowercase().contains(text) ||
                    it.phone.contains(text) ||
                    (it.email?.lowercase()?.contains(text) ?: false)
            }
            .toList()
            .let { list ->
                when (filters.sort) {
                    LeadSort.PRIORITY -> list.sortedByDescending { it.priority.weight }
                    LeadSort.RECENT -> list.sortedByDescending { it.lastContactedAt }
                    LeadSort.FOLLOW_UP -> list.sortedBy { it.nextCallbackAt ?: java.time.LocalDateTime.MAX }
                }
            }
    }
}
