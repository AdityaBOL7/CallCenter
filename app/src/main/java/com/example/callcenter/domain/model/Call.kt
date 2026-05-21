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
    MOBILE("Mobile dial"),
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
    val disposition: Disposition? = null,
    val note: String? = null,
)
