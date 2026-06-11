package com.example.callcenter.data.repository

import android.util.Log
import com.example.callcenter.data.remote.api.DialerApi
import com.example.callcenter.data.remote.dto.NotificationDto
import com.example.callcenter.domain.model.AppNotification
import com.example.callcenter.domain.model.NotificationKind
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Singleton
class NotificationsRepository @Inject constructor(
    private val dialerApi: DialerApi,
    private val json: Json,
) {

    private val _items = MutableStateFlow<List<AppNotification>>(emptyList())

    fun observe(): Flow<List<AppNotification>> =
        _items.map { list -> list.sortedByDescending { it.createdAt } }

    /** Fetch the inbox from the backend. */
    suspend fun refresh(): Result<Unit> = try {
        val element = dialerApi.listNotifications()
        val dtos = parseList(element)
        Log.d(TAG, "notifications/ ← ${dtos.size} items")
        _items.value = dtos.mapNotNull { it.toNotification() }
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "notifications/ failed", e)
        Result.failure(e)
    }

    suspend fun unreadCount(): Int = try {
        dialerApi.unreadCount().resolved
    } catch (e: Exception) {
        Log.e(TAG, "unread-count failed", e)
        _items.value.count { !it.read }
    }

    suspend fun markRead(id: Int) {
        // Optimistic local update, then sync.
        _items.update { list -> list.map { if (it.id == id) it.copy(read = true) else it } }
        try {
            dialerApi.markNotificationRead(id)
        } catch (e: Exception) {
            Log.w(TAG, "markRead($id) failed (kept local)", e)
        }
    }

    suspend fun markAllRead() {
        _items.update { list -> list.map { it.copy(read = true) } }
        try {
            dialerApi.markAllNotificationsRead()
        } catch (e: Exception) {
            Log.w(TAG, "markAllRead failed (kept local)", e)
        }
    }

    suspend fun dismiss(id: Int) {
        _items.update { list -> list.filterNot { it.id == id } }
        try {
            dialerApi.dismissNotification(id)
        } catch (e: Exception) {
            Log.w(TAG, "dismiss($id) failed (kept local)", e)
        }
    }

    // --- helpers ---

    private fun parseList(element: JsonElement): List<NotificationDto> = when (element) {
        is JsonArray -> json.decodeFromJsonElement(ListSerializer(NotificationDto.serializer()), element)
        is JsonObject -> {
            val results = element["results"]
            if (results is JsonArray) {
                json.decodeFromJsonElement(ListSerializer(NotificationDto.serializer()), results)
            } else {
                emptyList()
            }
        }
        else -> emptyList()
    }

    private fun NotificationDto.toNotification(): AppNotification? {
        val nId = id ?: return null
        return AppNotification(
            id = nId,
            kind = kindFromApi(kind),
            title = title ?: "Notification",
            body = resolvedBody.orEmpty(),
            createdAt = parseDate(createdAt) ?: LocalDateTime.now(),
            read = resolvedRead,
            targetLeadId = resolvedLeadId,
            targetCallbackId = resolvedCallbackId,
        )
    }

    private fun kindFromApi(k: String?): NotificationKind = when (k?.lowercase()) {
        "lead" -> NotificationKind.LEAD
        "callback" -> NotificationKind.CALLBACK
        "campaign" -> NotificationKind.CAMPAIGN
        // "call" and "system" both fall to SYSTEM (no dedicated CALL kind in the model).
        else -> NotificationKind.SYSTEM
    }

    private fun parseDate(raw: String?): LocalDateTime? {
        if (raw.isNullOrBlank()) return null
        return try {
            OffsetDateTime.parse(raw).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
        } catch (_: DateTimeParseException) {
            try {
                LocalDateTime.parse(raw)
            } catch (_: DateTimeParseException) {
                null
            }
        }
    }

    private companion object {
        const val TAG = "Bol7Notifs"
    }
}
