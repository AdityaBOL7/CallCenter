package com.example.callcenter.ui.screens.dashboard

import com.example.callcenter.domain.model.Agent
import com.example.callcenter.domain.model.AgentStats
import com.example.callcenter.domain.model.AgentStatus
import com.example.callcenter.domain.model.Lead

data class DashboardUiState(
    val agent: Agent? = null,
    val stats: AgentStats = AgentStats(),
    val status: AgentStatus = AgentStatus.AVAILABLE,
    val gettingNext: Boolean = false,
    val nextLead: Lead? = null,
    val loading: Boolean = true,
)
