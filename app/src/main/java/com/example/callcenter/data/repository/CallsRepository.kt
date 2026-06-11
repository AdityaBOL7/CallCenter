package com.example.callcenter.data.repository

import android.util.Log
import com.example.callcenter.data.remote.api.DialerApi
import com.example.callcenter.data.remote.dto.CallDto
import com.example.callcenter.data.remote.dto.HangupRequest
import com.example.callcenter.data.remote.dto.StartCallRequest
import com.example.callcenter.data.remote.dto.UpdateCallRequest
import com.example.callcenter.domain.model.Call
import com.example.callcenter.domain.model.CallDirection
import com.example.callcenter.domain.model.CallRouteType
import com.example.callcenter.domain.model.CallStatus
import com.example.callcenter.domain.model.Disposition
import com.example.callcenter.domain.model.Lead
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Singleton
class CallsRepository @Inject constructor(
    private val dialerApi: DialerApi,
    private val json: Json,
) {

    private val _active = MutableStateFlow<Call?>(null)
    val active: StateFlow<Call?> = _active.asStateFlow()

    // Backend integer id of the active call, for PATCH/hangup. Null if start failed.
    private var activeCallId: Int? = null

    private val _history = MutableStateFlow<List<Call>>(emptyList())

    fun observeHistory(): Flow<List<Call>> = _history

    /** Refresh call history from the backend. */
    suspend fun refreshHistory(): Result<Unit> = try {
        val element = dialerApi.listCalls()
        val dtos = parseCallList(element)
        Log.d(TAG, "calls/ ← ${dtos.size} calls")
        _history.value = dtos.mapNotNull { it.toCall() }
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "calls/ failed", e)
        Result.failure(e)
    }

    /**
     * Start a call. POSTs metadata to the backend; the returned integer id is
     * stored for later PATCH/hangup. Falls back to a local-only Call if the
     * backend call fails, so the in-call UI still works (Path B: app drives SIP).
     */
    suspend fun initiate(lead: Lead, route: CallRouteType): Call {
        val local = Call(
            id = "local-${lead.id}",
            leadId = lead.id,
            leadName = lead.name,
            leadPhone = lead.phone,
            campaignName = lead.campaignName,
            status = CallStatus.INITIATING,
            routeType = route,
            startedAt = LocalDateTime.now(),
        )
        return try {
            val dto = dialerApi.startCall(
                StartCallRequest(
                    leadId = lead.id,
                    toNumber = lead.phone.takeIf { it.isNotBlank() },
                    direction = "outbound",
                ),
            )
            activeCallId = dto.id
            Log.d(TAG, "calls/ start ← id=${dto.id} rpdp_uid=${dto.rpdpUid}")
            val call = local.copy(id = dto.id?.toString() ?: local.id)
            _active.value = call
            call
        } catch (e: Exception) {
            Log.e(TAG, "calls/ start failed — running call locally", e)
            activeCallId = null
            _active.value = local
            local
        }
    }

    fun updateActive(call: Call) {
        _active.value = call
    }

    /** PATCH an in-call state transition (best-effort; no-op without a backend id). */
    suspend fun patchCall(
        status: CallStatus? = null,
        muted: Boolean? = null,
        onHold: Boolean? = null,
        recording: Boolean? = null,
        note: String? = null,
    ) {
        val id = activeCallId ?: return
        try {
            dialerApi.updateCall(
                id,
                UpdateCallRequest(
                    status = status?.toApi(),
                    isMuted = muted,
                    isOnHold = onHold,
                    isRecorded = recording,
                    note = note,
                ),
            )
        } catch (e: Exception) {
            Log.w(TAG, "calls/$id PATCH failed (ignored)", e)
        }
    }

    suspend fun hangup(disposition: Disposition? = null, note: String? = null) {
        val id = activeCallId
        val completedLocally = _active.value?.copy(
            status = CallStatus.COMPLETED,
            endedAt = LocalDateTime.now(),
            disposition = disposition,
            note = note,
        )
        if (id != null) {
            try {
                val dto = dialerApi.hangupCall(
                    id,
                    HangupRequest(disposition = disposition?.toApi(), note = note),
                )
                Log.d(TAG, "calls/$id hangup ← status=${dto.status} dur=${dto.durationSeconds}")
                val merged = dto.toCall() ?: completedLocally
                _active.value = merged
                if (merged != null) _history.update { listOf(merged) + it.filter { c -> c.id != merged.id } }
                return
            } catch (e: Exception) {
                Log.e(TAG, "calls/$id hangup failed — recording locally", e)
            }
        }
        // Fallback: no backend id or the call failed — keep a local record.
        _active.value = completedLocally
        if (completedLocally != null) _history.update { listOf(completedLocally) + it }
    }

    fun clearActive() {
        _active.value = null
        activeCallId = null
    }

    // --- mapping helpers ---

    private fun parseCallList(element: JsonElement): List<CallDto> = when (element) {
        is JsonArray -> json.decodeFromJsonElement(ListSerializer(CallDto.serializer()), element)
        is JsonObject -> {
            val results = element["results"]
            if (results is JsonArray) {
                json.decodeFromJsonElement(ListSerializer(CallDto.serializer()), results)
            } else {
                emptyList()
            }
        }
        else -> emptyList()
    }

    private fun CallDto.toCall(): Call? {
        val callId = id ?: return null
        return Call(
            id = callId.toString(),
            leadId = resolvedLeadId ?: 0,
            leadName = leadName ?: "Unknown",
            leadPhone = resolvedPhone.orEmpty(),
            campaignName = campaignName.orEmpty(),
            direction = directionFromApi(direction),
            status = statusFromApi(status),
            routeType = CallRouteType.SIP,
            startedAt = parseDate(startedAt ?: createdAt) ?: LocalDateTime.now(),
            endedAt = parseDate(endedAt),
            durationSec = durationSeconds ?: 0,
            recordingUrl = recordingUrl,
            disposition = dispositionFromApi(disposition),
            note = note,
        )
    }

    private fun directionFromApi(d: String?): CallDirection = when (d?.lowercase()) {
        "inbound", "incoming" -> CallDirection.INCOMING
        else -> CallDirection.OUTGOING
    }

    private fun statusFromApi(s: String?): CallStatus = when (s?.lowercase()) {
        "initiating" -> CallStatus.INITIATING
        "ringing" -> CallStatus.RINGING
        "connected" -> CallStatus.CONNECTED
        "on_hold", "on-hold", "hold" -> CallStatus.ON_HOLD
        "completed" -> CallStatus.COMPLETED
        "failed" -> CallStatus.FAILED
        "no_answer", "no-answer", "noanswer" -> CallStatus.NO_ANSWER
        "busy" -> CallStatus.BUSY
        else -> CallStatus.COMPLETED
    }

    private fun CallStatus.toApi(): String = when (this) {
        CallStatus.INITIATING -> "initiating"
        CallStatus.RINGING -> "ringing"
        CallStatus.CONNECTED -> "connected"
        CallStatus.ON_HOLD -> "on_hold"
        CallStatus.COMPLETED -> "completed"
        CallStatus.FAILED -> "failed"
        CallStatus.NO_ANSWER -> "no_answer"
        CallStatus.BUSY -> "busy"
    }

    private fun dispositionFromApi(d: String?): Disposition? = when (d?.lowercase()) {
        null, "" -> null
        "interested" -> Disposition.INTERESTED
        "not_interested", "not-interested" -> Disposition.NOT_INTERESTED
        "follow_up", "follow-up", "followup" -> Disposition.FOLLOW_UP
        "no_answer", "no-answer" -> Disposition.NO_ANSWER
        "busy" -> Disposition.BUSY
        "wrong_number", "wrong-number" -> Disposition.WRONG_NUMBER
        "converted" -> Disposition.CONVERTED
        "closed" -> Disposition.CLOSED
        else -> null
    }

    private fun Disposition.toApi(): String = when (this) {
        Disposition.INTERESTED -> "interested"
        Disposition.NOT_INTERESTED -> "not_interested"
        Disposition.FOLLOW_UP -> "follow_up"
        Disposition.NO_ANSWER -> "no_answer"
        Disposition.BUSY -> "busy"
        Disposition.WRONG_NUMBER -> "wrong_number"
        Disposition.CONVERTED -> "converted"
        Disposition.CLOSED -> "closed"
    }

    private fun parseDate(raw: String?): LocalDateTime? {
        if (raw.isNullOrBlank()) return null
        return try {
            OffsetDateTime.parse(raw).toLocalDateTime()
        } catch (_: DateTimeParseException) {
            try {
                LocalDateTime.parse(raw)
            } catch (_: DateTimeParseException) {
                null
            }
        }
    }

    private companion object {
        const val TAG = "Bol7Calls"
    }
}
