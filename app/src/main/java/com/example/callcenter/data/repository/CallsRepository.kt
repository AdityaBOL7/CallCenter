package com.example.callcenter.data.repository

import com.example.callcenter.data.mock.MockData
import com.example.callcenter.domain.model.Call
import com.example.callcenter.domain.model.CallRouteType
import com.example.callcenter.domain.model.CallStatus
import com.example.callcenter.domain.model.Disposition
import com.example.callcenter.domain.model.Lead
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Singleton
class CallsRepository @Inject constructor() {

    private val _active = MutableStateFlow<Call?>(null)
    val active: StateFlow<Call?> = _active.asStateFlow()

    private val _history = MutableStateFlow(MockData.callHistory)

    fun observeHistory(): Flow<List<Call>> = _history

    suspend fun initiate(lead: Lead, route: CallRouteType): Call {
        delay(150)
        val call = Call(
            id = "c-live-${System.currentTimeMillis()}",
            leadId = lead.id,
            leadName = lead.name,
            leadPhone = lead.phone,
            campaignName = lead.campaignName,
            status = CallStatus.INITIATING,
            routeType = route,
            startedAt = LocalDateTime.now(),
        )
        _active.value = call
        return call
    }

    fun updateActive(call: Call) {
        _active.value = call
    }

    suspend fun hangup(disposition: Disposition? = null, note: String? = null) {
        delay(150)
        val completed = _active.value?.copy(
            status = CallStatus.COMPLETED,
            endedAt = LocalDateTime.now(),
            disposition = disposition,
            note = note,
        )
        _active.value = completed
        if (completed != null) {
            _history.update { listOf(completed) + it }
        }
    }

    fun clearActive() {
        _active.value = null
    }
}
