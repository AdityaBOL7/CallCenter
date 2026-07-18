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
    // Backend-assigned dial mode (from me/.call_mode). The agent uses whatever is
    // set here; they don't pick it. Defaults to SIP when unspecified.
    val callMode: CallRouteType = CallRouteType.SIP,
    val mobileNumber: String? = null,
    // The DID the callee sees on SIP calls (from me/.caller_id_number). Display
    // only — click-to-call deliberately sends NO from_number; the backend fills
    // in this provisioned value itself (see CallsRepository.initiate).
    val callerIdNumber: String? = null,
)

data class AgentStats(
    val totalCalls: Int = 0,
    val connectedCalls: Int = 0,
    val missedCalls: Int = 0,
    val callbacksDue: Int = 0,
    val conversions: Int = 0,
    val newLeads: Int = 0,
    // Additional KPIs from the dashboard totals (not "today"-scoped).
    val totalLeads: Int = 0,
    val recordings: Int = 0,
    val talkTime: String = "0m",
) {
    val connectionRate: Float
        get() = if (totalCalls == 0) 0f else connectedCalls.toFloat() / totalCalls
}
