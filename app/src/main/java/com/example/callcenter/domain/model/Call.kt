package com.example.callcenter.domain.model

import java.time.LocalDateTime

enum class CallStatus(val label: String) {
    INITIATING("Initiating"),
    RINGING("Ringing"),
    CONNECTED("Connected"),
    ON_HOLD("On hold"),
    COMPLETED("Completed"),
    FAILED("Failed"),
    NO_ANSWER("No answer"),
    BUSY("Busy"),
}

enum class CallDirection { OUTGOING, INCOMING }

enum class CallRouteType(val label: String) {
    SIP("SIP / VOIP"),
    PSTN("PSTN (Carrier)"),
    SIM("SIM"),
}

enum class Disposition(val label: String) {
    INTERESTED("Interested"),
    NOT_INTERESTED("Not interested"),
    FOLLOW_UP("Follow-up"),
    NO_ANSWER("No answer"),
    BUSY("Busy"),
    WRONG_NUMBER("Wrong number"),
    CONVERTED("Converted"),
    CLOSED("Closed"),
}

data class Call(
    val id: String,
    val leadId: Int,
    val leadName: String,
    val leadPhone: String,
    val campaignName: String,
    val direction: CallDirection = CallDirection.OUTGOING,
    val status: CallStatus = CallStatus.INITIATING,
    val routeType: CallRouteType = CallRouteType.SIP,
    val startedAt: LocalDateTime,
    val endedAt: LocalDateTime? = null,
    val durationSec: Int = 0,
    val recordingUrl: String? = null,
    // True when a recording exists for this call (device upload or provider),
    // even if recordingUrl is blank — device recordings report a source + size
    // rather than a URL.
    val hasRecording: Boolean = false,
    val disposition: Disposition? = null,
    val note: String? = null,
)

/**
 * True when a human actually ANSWERED this call — the single "Connected"
 * definition every screen must share (Reports, Profile snapshot). Product rule
 * agreed 2026-07-16:
 *  1. HARD EVIDENCE of no pickup wins over everything: a no_answer/busy/failed
 *     status is only ever recorded when the device call log CONFIRMED nobody
 *     talked — so even marking "Interested" on such a call can't count it as
 *     Connected (keeps agents from inflating their numbers, by mistake or not).
 *  2. The agent's explicit "no human spoke" is trusted next: a voicemail
 *     pickup logs a few talk seconds, but the agent knows a machine answered —
 *     No answer / Busy are never overruled by duration.
 *  3. Recorded talk time counts: connected status, or completed with real
 *     seconds.
 *  4. No evidence either way (e.g. SIP calls without duration): a
 *     conversation-only outcome (Interested / Not interested / Follow-up /
 *     Converted) decides — those make no sense without talking.
 */
fun Call.reachedCustomer(): Boolean = when {
    status == CallStatus.NO_ANSWER || status == CallStatus.BUSY || status == CallStatus.FAILED -> false
    disposition == Disposition.NO_ANSWER || disposition == Disposition.BUSY -> false
    status == CallStatus.CONNECTED || (status == CallStatus.COMPLETED && durationSec > 0) -> true
    else -> disposition == Disposition.INTERESTED ||
        disposition == Disposition.NOT_INTERESTED ||
        disposition == Disposition.FOLLOW_UP ||
        disposition == Disposition.CONVERTED
}
