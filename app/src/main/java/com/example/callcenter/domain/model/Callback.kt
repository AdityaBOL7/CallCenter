package com.example.callcenter.domain.model

import java.time.LocalDateTime

enum class CallbackStatus(val label: String) {
    PENDING("Pending"),
    OVERDUE("Overdue"),
    COMPLETED("Completed"),
    CANCELLED("Cancelled"),
}

data class Callback(
    val id: Int,
    val leadId: Int,
    val customerName: String,
    val phone: String,
    val campaignName: String,
    val scheduledAt: LocalDateTime,
    val status: CallbackStatus = CallbackStatus.PENDING,
    val note: String? = null,
)
