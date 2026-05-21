package com.example.callcenter.domain.model

import java.time.LocalDateTime

enum class NotificationKind { CALLBACK, LEAD, SYSTEM, CAMPAIGN }

data class AppNotification(
    val id: Int,
    val kind: NotificationKind,
    val title: String,
    val body: String,
    val createdAt: LocalDateTime,
    val read: Boolean = false,
    val targetLeadId: Int? = null,
    val targetCallbackId: Int? = null,
)
