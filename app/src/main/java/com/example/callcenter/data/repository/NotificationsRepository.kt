package com.example.callcenter.data.repository

import com.example.callcenter.data.mock.MockData
import com.example.callcenter.domain.model.AppNotification
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

@Singleton
class NotificationsRepository @Inject constructor() {

    private val _items = MutableStateFlow(MockData.notifications)

    fun observe(): Flow<List<AppNotification>> =
        _items.map { list -> list.sortedByDescending { it.createdAt } }

    suspend fun markRead(id: Int) {
        delay(80)
        _items.update { list ->
            list.map { if (it.id == id) it.copy(read = true) else it }
        }
    }

    suspend fun markAllRead() {
        delay(120)
        _items.update { list -> list.map { it.copy(read = true) } }
    }
}
