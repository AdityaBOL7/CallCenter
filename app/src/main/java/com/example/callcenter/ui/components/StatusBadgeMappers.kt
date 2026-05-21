package com.example.callcenter.ui.components

import androidx.compose.ui.graphics.Color
import com.example.callcenter.domain.model.AgentStatus
import com.example.callcenter.domain.model.CallStatus
import com.example.callcenter.domain.model.CallbackStatus
import com.example.callcenter.domain.model.Disposition
import com.example.callcenter.domain.model.LeadPriority
import com.example.callcenter.domain.model.LeadStatus
import com.example.callcenter.ui.theme.AccentAmber
import com.example.callcenter.ui.theme.AccentMint
import com.example.callcenter.ui.theme.AccentRose
import com.example.callcenter.ui.theme.AccentSky
import com.example.callcenter.ui.theme.AccentViolet
import com.example.callcenter.ui.theme.Danger
import com.example.callcenter.ui.theme.Ink500
import com.example.callcenter.ui.theme.Success
import com.example.callcenter.ui.theme.Warn

fun colorForLeadStatus(status: LeadStatus): Color = when (status) {
    LeadStatus.NEW -> AccentSky
    LeadStatus.CONTACTED -> AccentViolet
    LeadStatus.INTERESTED -> Success
    LeadStatus.NOT_INTERESTED -> Danger
    LeadStatus.FOLLOW_UP -> Warn
    LeadStatus.CONVERTED -> AccentMint
    LeadStatus.CLOSED -> Ink500
}

fun colorForLeadPriority(p: LeadPriority): Color = when (p) {
    LeadPriority.LOW -> Ink500
    LeadPriority.MEDIUM -> AccentSky
    LeadPriority.HIGH -> AccentAmber
    LeadPriority.URGENT -> AccentRose
}

fun colorForCallStatus(s: CallStatus): Color = when (s) {
    CallStatus.INITIATING, CallStatus.RINGING -> AccentSky
    CallStatus.CONNECTED -> Success
    CallStatus.ON_HOLD -> Warn
    CallStatus.COMPLETED -> AccentMint
    CallStatus.FAILED, CallStatus.BUSY -> Danger
    CallStatus.NO_ANSWER -> Ink500
}

fun colorForCallbackStatus(s: CallbackStatus): Color = when (s) {
    CallbackStatus.PENDING -> AccentSky
    CallbackStatus.OVERDUE -> Danger
    CallbackStatus.COMPLETED -> Success
    CallbackStatus.CANCELLED -> Ink500
}

fun colorForDisposition(d: Disposition): Color = when (d) {
    Disposition.INTERESTED -> Success
    Disposition.NOT_INTERESTED -> Danger
    Disposition.FOLLOW_UP -> Warn
    Disposition.NO_ANSWER -> Ink500
    Disposition.BUSY -> AccentAmber
    Disposition.WRONG_NUMBER -> AccentRose
    Disposition.CONVERTED -> AccentMint
    Disposition.CLOSED -> Ink500
}

fun colorForAgentStatus(s: AgentStatus): Color = when (s) {
    AgentStatus.AVAILABLE -> Success
    AgentStatus.BUSY -> Danger
    AgentStatus.BREAK -> Warn
    AgentStatus.OFFLINE -> Ink500
}
