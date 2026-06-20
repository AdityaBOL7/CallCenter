package com.example.callcenter.data.repository

import android.util.Log
import com.example.callcenter.data.remote.api.DialerApi
import com.example.callcenter.data.remote.dto.CallbackDto
import com.example.callcenter.data.remote.dto.CompleteCallbackRequest
import com.example.callcenter.data.remote.dto.ScheduleCallbackRequest
import com.example.callcenter.data.remote.dto.UpdateCallbackRequest
import com.example.callcenter.domain.model.Callback
import com.example.callcenter.domain.model.CallbackStatus
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
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
class CallbacksRepository @Inject constructor(
    private val dialerApi: DialerApi,
    private val json: Json,
) {

    private val _items = MutableStateFlow<List<Callback>>(emptyList())

    fun observeAll(): Flow<List<Callback>> =
        _items.map { list -> list.sortedBy { it.scheduledAt } }

    /** Cached lookup (no network). Use [fetchById] to force a backend read. */
    fun byId(id: Int): Callback? = _items.value.firstOrNull { it.id == id }

    /**
     * Refresh the callback list from the backend. Optional filters mirror
     * GET /callbacks/?status=&from=&to= ([from]/[to] are ISO-8601 timestamps).
     */
    suspend fun refresh(
        status: CallbackStatus? = null,
        from: LocalDateTime? = null,
        to: LocalDateTime? = null,
    ): Result<Unit> = try {
        val element = dialerApi.listCallbacks(
            status = status?.toApi(),
            from = from?.let(::toIsoUtc),
            to = to?.let(::toIsoUtc),
        )
        val dtos = parseList(element)
        Log.d(TAG, "callbacks/ ← ${dtos.size} items")
        _items.value = dtos.mapNotNull { it.toCallback() }
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "callbacks/ failed", e)
        Result.failure(e)
    }

    /** Fetch a single callback from the backend (GET /callbacks/:id/) and cache it. */
    suspend fun fetchById(id: Int): Callback? = try {
        dialerApi.getCallback(id).toCallback()?.also { cb -> _items.update { it.upsert(cb) } }
    } catch (e: Exception) {
        Log.e(TAG, "callbacks/$id failed", e)
        null
    }

    suspend fun schedule(
        leadId: Int,
        leadName: String,
        phone: String,
        campaign: String,
        at: LocalDateTime,
        note: String?,
    ): Callback? {
        return try {
            val dto = dialerApi.scheduleCallback(
                ScheduleCallbackRequest(
                    lead = leadId.takeIf { it > 0 },
                    customerName = leadName.takeIf { it.isNotBlank() },
                    customerNumber = phone.takeIf { it.isNotBlank() },
                    scheduledAt = toIsoUtc(at),
                    note = note,
                ),
            )
            val cb = dto.toCallback()
            if (cb != null) _items.update { it.upsert(cb) }
            cb
        } catch (e: Exception) {
            Log.e(TAG, "schedule callback failed", e)
            null
        }
    }

    /**
     * Update / reschedule a callback (PATCH /callbacks/:id/). Any argument left
     * null is omitted from the request. [status] lets a callback be moved back to
     * pending, etc.
     */
    suspend fun update(
        id: Int,
        at: LocalDateTime? = null,
        note: String? = null,
        status: CallbackStatus? = null,
    ) {
        try {
            val dto = dialerApi.updateCallback(
                id,
                UpdateCallbackRequest(
                    scheduledAt = at?.let(::toIsoUtc),
                    note = note,
                    status = status?.toApi(),
                ),
            )
            dto.toCallback()?.let { cb -> _items.update { it.upsert(cb) } }
        } catch (e: Exception) {
            Log.e(TAG, "update callback failed", e)
        }
    }

    suspend fun complete(id: Int, callId: Int? = null, note: String? = null): Result<Unit> = try {
        val dto = dialerApi.completeCallback(id, CompleteCallbackRequest(callId = callId, note = note))
        dto.toCallback()?.let { cb -> _items.update { it.upsert(cb) } }
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "complete callback failed", e)
        Result.failure(e)
    }

    suspend fun cancel(id: Int): Result<Unit> = try {
        dialerApi.cancelCallback(id)
        // Reflect the soft-cancel locally.
        _items.update { list ->
            list.map { if (it.id == id) it.copy(status = CallbackStatus.CANCELLED) else it }
        }
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "cancel callback failed", e)
        Result.failure(e)
    }

    /** Pending callbacks due within the next [minutes]. */
    suspend fun dueSoon(minutes: Int = 30): List<Callback> = try {
        parseList(dialerApi.callbacksDue(minutes)).mapNotNull { it.toCallback() }
    } catch (e: Exception) {
        Log.e(TAG, "callbacks/due failed", e)
        emptyList()
    }

    // --- helpers ---

    private fun List<Callback>.upsert(cb: Callback): List<Callback> {
        val idx = indexOfFirst { it.id == cb.id }
        return if (idx >= 0) toMutableList().also { it[idx] = cb } else this + cb
    }

    private fun parseList(element: JsonElement): List<CallbackDto> = when (element) {
        is JsonArray -> json.decodeFromJsonElement(ListSerializer(CallbackDto.serializer()), element)
        is JsonObject -> {
            val results = element["results"]
            if (results is JsonArray) {
                json.decodeFromJsonElement(ListSerializer(CallbackDto.serializer()), results)
            } else {
                emptyList()
            }
        }
        else -> emptyList()
    }

    private fun CallbackDto.toCallback(): Callback? {
        val cbId = id ?: return null
        return Callback(
            id = cbId,
            leadId = resolvedLeadId ?: 0,
            customerName = resolvedName ?: "Unknown",
            phone = resolvedPhone.orEmpty(),
            campaignName = campaignName.orEmpty(),
            scheduledAt = parseDate(scheduledAt) ?: LocalDateTime.now(),
            status = statusFromApi(status),
            note = note,
        )
    }

    private fun statusFromApi(s: String?): CallbackStatus = when (s?.lowercase()) {
        "pending" -> CallbackStatus.PENDING
        "overdue" -> CallbackStatus.OVERDUE
        "completed", "complete" -> CallbackStatus.COMPLETED
        "cancelled", "canceled" -> CallbackStatus.CANCELLED
        else -> CallbackStatus.PENDING
    }

    private fun CallbackStatus.toApi(): String = when (this) {
        CallbackStatus.PENDING -> "pending"
        CallbackStatus.OVERDUE -> "overdue"
        CallbackStatus.COMPLETED -> "completed"
        CallbackStatus.CANCELLED -> "cancelled"
    }

    /** Local date-time → ISO-8601 UTC ("…Z"), the format the backend expects. */
    private fun toIsoUtc(local: LocalDateTime): String =
        local.atZone(ZoneId.systemDefault())
            .withZoneSameInstant(ZoneOffset.UTC)
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"))

    /** ISO-8601 (UTC or offset) → local date-time. */
    private fun parseDate(raw: String?): LocalDateTime? {
        if (raw.isNullOrBlank()) return null
        return try {
            OffsetDateTime.parse(raw).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
        } catch (_: DateTimeParseException) {
            try {
                Instant.parse(raw).atZone(ZoneId.systemDefault()).toLocalDateTime()
            } catch (_: DateTimeParseException) {
                try {
                    LocalDateTime.parse(raw)
                } catch (_: DateTimeParseException) {
                    null
                }
            }
        }
    }

    private companion object {
        const val TAG = "Bol7Callbacks"
    }
}
