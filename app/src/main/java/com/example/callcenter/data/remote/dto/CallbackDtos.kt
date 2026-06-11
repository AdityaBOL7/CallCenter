package com.example.callcenter.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * POST /api/dialer/callbacks/ — schedule a callback.
 * At least one of `lead` or `customer_number` must be set (serializer rejects neither).
 * `scheduled_at` is an ISO-8601 UTC timestamp, e.g. "2026-06-15T10:00:00Z".
 */
@Serializable
data class ScheduleCallbackRequest(
    val lead: Int? = null,
    @SerialName("customer_name") val customerName: String? = null,
    @SerialName("customer_number") val customerNumber: String? = null,
    @SerialName("scheduled_at") val scheduledAt: String,
    val note: String? = null,
)

/** PATCH /api/dialer/callbacks/:id/ — reschedule / update. */
@Serializable
data class UpdateCallbackRequest(
    @SerialName("scheduled_at") val scheduledAt: String? = null,
    val note: String? = null,
    val status: String? = null,
)

/** POST /api/dialer/callbacks/:id/complete/ — mark complete (+optional linked call). */
@Serializable
data class CompleteCallbackRequest(
    @SerialName("call_id") val callId: Int? = null,
    val note: String? = null,
)

/** One callback row. Tolerant: exact shape undocumented, all fields nullable. */
@Serializable
data class CallbackDto(
    val id: Int? = null,
    val lead: Int? = null,
    @SerialName("lead_id") val leadId: Int? = null,
    @SerialName("customer_name") val customerName: String? = null,
    @SerialName("lead_name") val leadName: String? = null,
    @SerialName("customer_number") val customerNumber: String? = null,
    val phone: String? = null,
    @SerialName("campaign_name") val campaignName: String? = null,
    val status: String? = null,
    val note: String? = null,
    @SerialName("scheduled_at") val scheduledAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
) {
    val resolvedLeadId: Int?
        get() = lead ?: leadId
    val resolvedName: String?
        get() = customerName ?: leadName
    val resolvedPhone: String?
        get() = phone ?: customerNumber
}
