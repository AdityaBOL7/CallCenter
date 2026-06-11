package com.example.callcenter.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One notification row from /api/dialer/notifications/. Tolerant: undocumented
 * exact shape, all fields nullable, unknown keys ignored.
 */
@Serializable
data class NotificationDto(
    val id: Int? = null,
    val kind: String? = null,
    val title: String? = null,
    val body: String? = null,
    val message: String? = null,
    @SerialName("is_read") val isRead: Boolean? = null,
    val read: Boolean? = null,
    val unread: Boolean? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("lead_id") val leadId: Int? = null,
    @SerialName("callback_id") val callbackId: Int? = null,
    // Some payloads nest target ids under "data".
    val data: NotificationData? = null,
) {
    @Serializable
    data class NotificationData(
        @SerialName("lead_id") val leadId: Int? = null,
        @SerialName("callback_id") val callbackId: Int? = null,
    )

    val resolvedBody: String?
        get() = body ?: message
    val resolvedRead: Boolean
        get() = isRead ?: read ?: unread?.not() ?: false
    val resolvedLeadId: Int?
        get() = leadId ?: data?.leadId
    val resolvedCallbackId: Int?
        get() = callbackId ?: data?.callbackId
}

/** Response of /api/dialer/notifications/unread-count/. */
@Serializable
data class UnreadCountDto(
    val count: Int? = null,
    val unread: Int? = null,
) {
    val resolved: Int get() = count ?: unread ?: 0
}
