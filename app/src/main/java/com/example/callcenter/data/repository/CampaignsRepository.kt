package com.example.callcenter.data.repository

import com.example.callcenter.data.mock.MockData
import com.example.callcenter.domain.model.Campaign
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

@Singleton
class CampaignsRepository @Inject constructor() {

    private val _items = MutableStateFlow(MockData.campaigns)

    fun observeCampaigns(): Flow<List<Campaign>> = _items

    fun byId(id: Int): Campaign? = _items.value.firstOrNull { it.id == id }
}
