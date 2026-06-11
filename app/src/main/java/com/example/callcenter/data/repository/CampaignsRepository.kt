package com.example.callcenter.data.repository

import android.util.Log
import com.example.callcenter.data.remote.api.DialerApi
import com.example.callcenter.data.remote.dto.CampaignDto
import com.example.callcenter.domain.model.Campaign
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Singleton
class CampaignsRepository @Inject constructor(
    private val dialerApi: DialerApi,
    private val json: Json,
) {

    private val _items = MutableStateFlow<List<Campaign>>(emptyList())

    fun observeCampaigns(): Flow<List<Campaign>> = _items

    /** Fetch campaigns from the backend into the cache. */
    suspend fun refresh(): Result<Unit> = try {
        val element = dialerApi.listCampaigns()
        val dtos = parseList(element)
        Log.d(TAG, "campaigns/ ← ${dtos.size} campaigns")
        _items.value = dtos.mapNotNull { it.toCampaign() }
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "campaigns/ failed", e)
        Result.failure(e)
    }

    /** Cached campaign, or fetch by id if not present. */
    suspend fun byId(id: Int): Campaign? {
        _items.value.firstOrNull { it.id == id }?.let { return it }
        return try {
            dialerApi.getCampaign(id).toCampaign()?.also { fetched ->
                _items.value = _items.value.upsert(fetched)
            }
        } catch (e: Exception) {
            Log.e(TAG, "campaigns/$id failed", e)
            null
        }
    }

    private fun List<Campaign>.upsert(c: Campaign): List<Campaign> {
        val idx = indexOfFirst { it.id == c.id }
        return if (idx >= 0) toMutableList().also { it[idx] = c } else this + c
    }

    private fun parseList(element: JsonElement): List<CampaignDto> = when (element) {
        is JsonArray -> json.decodeFromJsonElement(ListSerializer(CampaignDto.serializer()), element)
        is JsonObject -> {
            val results = element["results"]
            if (results is JsonArray) {
                json.decodeFromJsonElement(ListSerializer(CampaignDto.serializer()), results)
            } else {
                emptyList()
            }
        }
        else -> emptyList()
    }

    private fun CampaignDto.toCampaign(): Campaign? {
        val cid = id ?: return null
        return Campaign(
            id = cid,
            name = name ?: "Untitled",
            description = description.orEmpty(),
            totalLeads = resolvedTotal,
            contactedLeads = resolvedContacted,
            conversions = resolvedConversions,
            active = isActive,
        )
    }

    private companion object {
        const val TAG = "Bol7Campaigns"
    }
}
