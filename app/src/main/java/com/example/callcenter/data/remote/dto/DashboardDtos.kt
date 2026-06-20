package com.example.callcenter.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * GET /api/dialer/dashboard/ — top-of-screen KPIs. Tolerant: only the `totals`
 * block is mapped to the agent stats; `agent_performance` / `recent_calls` are
 * ignored for now. All fields nullable, unknown keys ignored.
 */
@Serializable
data class DashboardResponse(
    val totals: Totals? = null,
) {
    @Serializable
    data class Totals(
        val leads: Int? = null,
        val agents: Int? = null,
        @SerialName("calls_today") val callsToday: Int? = null,
        @SerialName("calls_yesterday") val callsYesterday: Int? = null,
        @SerialName("followups_tomorrow") val followupsTomorrow: Int? = null,
        @SerialName("connected_calls") val connectedCalls: Int? = null,
        @SerialName("pending_calls") val pendingCalls: Int? = null,
        val sales: Int? = null,
        val recordings: Int? = null,
        @SerialName("total_talk_time") val totalTalkTime: String? = null,
    )
}
