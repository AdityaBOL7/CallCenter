package com.example.callcenter.domain.model

enum class AgentStatus(val label: String) {
    AVAILABLE("Available"),
    BUSY("Busy"),
    BREAK("Break"),
    OFFLINE("Offline"),
}

data class Agent(
    val id: Int,
    val name: String,
    val email: String,
    val username: String,
    val extension: String,
    val sipUsername: String,
    val avatarUrl: String? = null,
    val status: AgentStatus = AgentStatus.AVAILABLE,
    val campaignIds: List<Int> = emptyList(),
)

data class AgentStats(
    val totalCalls: Int = 0,
    val connectedCalls: Int = 0,
    val missedCalls: Int = 0,
    val callbacksDue: Int = 0,
    val conversions: Int = 0,
    val newLeads: Int = 0,
) {
    val connectionRate: Float
        get() = if (totalCalls == 0) 0f else connectedCalls.toFloat() / totalCalls
}
