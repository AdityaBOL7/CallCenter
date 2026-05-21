package com.example.callcenter.data.repository

import com.example.callcenter.data.mock.MockData
import com.example.callcenter.domain.model.Agent
import com.example.callcenter.domain.model.AgentStats
import com.example.callcenter.domain.model.AgentStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class AgentRepository @Inject constructor() {

    private val _agent = MutableStateFlow(MockData.agent)
    val agent: StateFlow<Agent> = _agent.asStateFlow()

    private val _stats = MutableStateFlow(MockData.stats)
    val stats: StateFlow<AgentStats> = _stats.asStateFlow()

    suspend fun setStatus(status: AgentStatus) {
        delay(120)
        _agent.value = _agent.value.copy(status = status)
    }

    suspend fun refreshStats() {
        delay(250)
        _stats.value = _stats.value.copy(
            totalCalls = _stats.value.totalCalls + 1,
            connectedCalls = _stats.value.connectedCalls + 1,
        )
    }

    suspend fun updateProfile(name: String, email: String) {
        delay(200)
        _agent.value = _agent.value.copy(name = name, email = email)
    }
}
