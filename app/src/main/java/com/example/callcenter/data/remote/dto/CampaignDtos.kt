package com.example.callcenter.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One campaign row from /api/dialer/campaigns/. Tolerant: undocumented exact
 * shape, all fields nullable, unknown keys ignored. Stats come from the
 * refresh-stats endpoint (target/contacted/conversion counts).
 */
@Serializable
data class CampaignDto(
    val id: Int? = null,
    val name: String? = null,
    val description: String? = null,
    val status: String? = null,
    @SerialName("start_date") val startDate: String? = null,
    @SerialName("end_date") val endDate: String? = null,
    @SerialName("target_count") val targetCount: Int? = null,
    @SerialName("contacted_count") val contactedCount: Int? = null,
    @SerialName("conversion_count") val conversionCount: Int? = null,
    // Alternate names some backends use.
    @SerialName("total_leads") val totalLeads: Int? = null,
    @SerialName("contacted_leads") val contactedLeads: Int? = null,
    val conversions: Int? = null,
) {
    val resolvedTotal: Int
        get() = targetCount ?: totalLeads ?: 0
    val resolvedContacted: Int
        get() = contactedCount ?: contactedLeads ?: 0
    val resolvedConversions: Int
        get() = conversionCount ?: conversions ?: 0
    val isActive: Boolean
        get() = status?.lowercase() == "active"
}
