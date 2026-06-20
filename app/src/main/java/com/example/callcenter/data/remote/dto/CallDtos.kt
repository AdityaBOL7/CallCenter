package com.example.callcenter.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** POST /api/dialer/calls/ — start a call (metadata; SIP audio handled by the app). */
@Serializable
data class StartCallRequest(
    @SerialName("lead_id") val leadId: Int? = null,
    @SerialName("to_number") val toNumber: String? = null,
    val direction: String = "outbound",
    val note: String? = null,
)

/** PATCH /api/dialer/calls/:id/ — reflect in-call state transitions / toggles. */
@Serializable
data class UpdateCallRequest(
    val status: String? = null,
    @SerialName("is_muted") val isMuted: Boolean? = null,
    @SerialName("is_on_hold") val isOnHold: Boolean? = null,
    @SerialName("is_recorded") val isRecorded: Boolean? = null,
    val note: String? = null,
)

/** POST /api/dialer/calls/:id/hangup/ — final hangup with disposition + note. */
@Serializable
data class HangupRequest(
    val disposition: String? = null,
    val note: String? = null,
    val status: String = "completed",
)

/**
 * One call row from the backend. Tolerant: undocumented exact shape, so all
 * fields are nullable and unknown keys are ignored.
 */
@Serializable
data class CallDto(
    val id: Int? = null,
    @SerialName("rpdp_uid") val rpdpUid: String? = null,
    @SerialName("lead_id") val leadId: Int? = null,
    val lead: Int? = null,
    @SerialName("lead_name") val leadName: String? = null,
    // The backend nests the lead's identity under lead_summary
    // (e.g. {"lead":107,"lead_summary":{"id":107,"name":"Dev","phone":"..."}}).
    @SerialName("lead_summary") val leadSummary: LeadSummary? = null,
    @SerialName("to_number") val toNumber: String? = null,
    @SerialName("from_number") val fromNumber: String? = null,
    @SerialName("phone") val phone: String? = null,
    @SerialName("campaign_name") val campaignName: String? = null,
    val direction: String? = null,
    val status: String? = null,
    val disposition: String? = null,
    val note: String? = null,
    @SerialName("is_muted") val isMuted: Boolean? = null,
    @SerialName("is_on_hold") val isOnHold: Boolean? = null,
    @SerialName("is_recorded") val isRecorded: Boolean? = null,
    @SerialName("duration_seconds") val durationSeconds: Int? = null,
    @SerialName("recording_url") val recordingUrl: String? = null,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("answered_at") val answeredAt: String? = null,
    @SerialName("ended_at") val endedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
) {
    @Serializable
    data class LeadSummary(
        val id: Int? = null,
        val name: String? = null,
        val phone: String? = null,
        val status: String? = null,
    )

    val resolvedLeadId: Int?
        get() = leadId ?: lead ?: leadSummary?.id
    val resolvedName: String?
        get() = leadName ?: leadSummary?.name
    val resolvedPhone: String?
        get() = phone ?: leadSummary?.phone ?: toNumber ?: fromNumber
}
