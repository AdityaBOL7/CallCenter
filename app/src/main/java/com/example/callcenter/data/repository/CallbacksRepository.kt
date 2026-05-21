package com.example.callcenter.data.repository

import com.example.callcenter.data.mock.MockData
import com.example.callcenter.domain.model.Callback
import com.example.callcenter.domain.model.CallbackStatus
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

@Singleton
class CallbacksRepository @Inject constructor() {

    private val _items = MutableStateFlow(MockData.callbacks)

    fun observeAll(): Flow<List<Callback>> =
        _items.map { list -> list.sortedBy { it.scheduledAt } }

    fun byId(id: Int): Callback? = _items.value.firstOrNull { it.id == id }

    suspend fun schedule(leadId: Int, leadName: String, phone: String, campaign: String,
                        at: LocalDateTime, note: String?): Callback {
        delay(180)
        val cb = Callback(
            id = (_items.value.maxOfOrNull { it.id } ?: 0) + 1,
            leadId = leadId,
            customerName = leadName,
            phone = phone,
            campaignName = campaign,
            scheduledAt = at,
            note = note,
            status = CallbackStatus.PENDING,
        )
        _items.update { it + cb }
        return cb
    }

    suspend fun update(id: Int, at: LocalDateTime, note: String?) {
        delay(120)
        _items.update { list ->
            list.map { if (it.id == id) it.copy(scheduledAt = at, note = note) else it }
        }
    }
}
