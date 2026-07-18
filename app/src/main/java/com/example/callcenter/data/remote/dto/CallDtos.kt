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

/**
 * POST /api/dialer/calls/click-to-call/ — SIP-mode server-side originate. The
 * backend rings the agent's SIP extension, then the lead; the app just fires
 * this and records the returned call id. Used when call_mode == sip/voip
 * (the SIP analogue of sim-dial).
 *
 * Body is exactly {to_number, from_number, note} — NO `direction` (the working
 * curl omits it, and with encodeDefaults=true a defaulted field would leak onto
 * the wire). ALL THREE keys must be present: the caller sends from_number and
 * note as EMPTY strings — a blank from_number tells the server to fill in the
 * agent's provisioned caller id (omitting keys risks a 422; nulls are dropped
 * from the wire by explicitNulls=false). The real note is PATCHed later at
 * disposition.
 */
@Serializable
data class ClickToCallRequest(
    @SerialName("to_number") val toNumber: String? = null,
    @SerialName("from_number") val fromNumber: String? = null,
    val note: String? = null,
)

/**
 * POST /api/dialer/calls/sim-incoming/ — log an inbound carrier (SIM) call after
 * it ends. The backend finds-or-creates a Lead from [fromNumber] and returns the
 * created Call row (with lead_summary), which the app then dispositions through
 * the normal hangup/ flow. [durationSeconds] is only sent for answered calls
 * (status "completed"); missed calls report status "no_answer" with no duration
 * (explicitNulls=false drops the null on the wire).
 */
@Serializable
data class SimIncomingRequest(
    @SerialName("from_number") val fromNumber: String,
    val status: String,
    @SerialName("started_at") val startedAt: String,
    @SerialName("duration_seconds") val durationSeconds: Int? = null,
)

/** PATCH /api/dialer/calls/:id/ — reflect in-call state transitions / toggles. */
@Serializable
data class UpdateCallRequest(
    val status: String? = null,
    @SerialName("is_muted") val isMuted: Boolean? = null,
    @SerialName("is_on_hold") val isOnHold: Boolean? = null,
    @SerialName("is_recorded") val isRecorded: Boolean? = null,
    val note: String? = null,
    // Real pickup time (ISO-8601). Backend contract 2026-07-16: the server no
    // longer guesses answered_at for SIM calls — the app PATCHes it, derived
    // from the device call log (call end − true talk seconds). Only sent when
    // a pickup actually happened; null is dropped from the wire.
    @SerialName("answered_at") val answeredAt: String? = null,
)

/**
 * POST /api/dialer/calls/:id/hangup/ — final hangup with disposition + note.
 *
 * status/duration_seconds are SIM-ONLY (true talk seconds from the device call
 * log; contract 2026-07-16 — the backend honours them instead of computing
 * ended_at − started_at, which would count disposition-screen time). For SIP
 * calls BOTH are omitted (null → dropped from the wire): the backend/provider
 * CDR owns status and duration there (user decision 2026-07-18), so the SIP
 * body is just {disposition, note?}.
 */
@Serializable
data class HangupRequest(
    val disposition: String? = null,
    val note: String? = null,
    val status: String? = null,
    @SerialName("duration_seconds") val durationSeconds: Int? = null,
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
    // How the call was carried: "sim" | "sip" | "voip" (backend field `mode`).
    // Must be read from the server — hardcoding SIP made every history row of a
    // SIM agent show a "SIP" badge (2026-07-16).
    val mode: String? = null,
    val status: String? = null,
    val disposition: String? = null,
    val note: String? = null,
    @SerialName("is_muted") val isMuted: Boolean? = null,
    @SerialName("is_on_hold") val isOnHold: Boolean? = null,
    @SerialName("is_recorded") val isRecorded: Boolean? = null,
    @SerialName("duration_seconds") val durationSeconds: Int? = null,
    @SerialName("recording_url") val recordingUrl: String? = null,
    // Device recordings come back with recording_url="" but a real source +
    // size, so those are the reliable "has a recording" signal.
    @SerialName("recording_source") val recordingSource: String? = null,
    @SerialName("recording_size_bytes") val recordingSizeBytes: Long? = null,
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
