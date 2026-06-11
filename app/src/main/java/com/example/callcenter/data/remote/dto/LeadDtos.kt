package com.example.callcenter.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * One lead row from /api/dialer/leads/. Tolerant: unknown keys ignored, every
 * field nullable. We'll tighten names once a real response is seen in logcat.
 */
@Serializable
data class LeadDto(
    val id: Int? = null,
    val name: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val status: String? = null,
    val priority: String? = null,
    val notes: String? = null,
    val source: String? = null,
    val tags: List<String>? = null,

    val campaign: Int? = null,
    @SerialName("campaign_id") val campaignId: Int? = null,
    @SerialName("campaign_name") val campaignName: String? = null,

    @SerialName("assigned_agent") val assignedAgent: Int? = null,
    @SerialName("assigned_agent_id") val assignedAgentId: Int? = null,

    @SerialName("next_callback_at") val nextCallbackAt: String? = null,
    @SerialName("last_contacted_at") val lastContactedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,

    // Arbitrary extra fields the backend may attach.
    val extra: JsonElement? = null,
) {
    val resolvedCampaignId: Int?
        get() = campaign ?: campaignId
}

/**
 * The list endpoint may return a bare array OR a DRF-paginated envelope
 * ({count,next,previous,results:[...]}). We expose a custom deserialization
 * path in the repository; this type covers the paginated shape.
 */
@Serializable
data class LeadPage(
    val count: Int? = null,
    val next: String? = null,
    val previous: String? = null,
    val results: List<LeadDto> = emptyList(),
)

/** PATCH body for /leads/:id/ — only non-null fields are sent. */
@Serializable
data class UpdateLeadRequest(
    val status: String? = null,
    val priority: String? = null,
    val notes: String? = null,
    val tags: List<String>? = null,
)

/** POST body for /leads/:id/assign/ — agent_id=null self-unassigns. */
@Serializable
data class AssignLeadRequest(
    @SerialName("agent_id") val agentId: Int? = null,
)
