package com.example.callcenter.data.repository

import android.util.Log
import com.example.callcenter.data.remote.api.DialerApi
import com.example.callcenter.data.remote.dto.CampaignDto
import com.example.callcenter.data.remote.dto.CreateCampaignRequest
import com.example.callcenter.data.remote.dto.UpdateCampaignRequest
import com.example.callcenter.domain.model.Campaign
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
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

    /**
     * Fetch campaigns from the backend into the cache. Optional filters mirror
     * GET /campaigns/?status=&search=.
     */
    suspend fun refresh(status: String? = null, search: String? = null): Result<Unit> = try {
        val element = dialerApi.listCampaigns(
            status = status?.takeIf { it.isNotBlank() },
            search = search?.takeIf { it.isNotBlank() },
        )
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

    /** Create a campaign (admin / company-owner). On success it's cached. */
    suspend fun create(
        name: String,
        description: String = "",
        status: String = "draft",
        startDate: String? = null,
        endDate: String? = null,
    ): Result<Campaign> = runCatchingCampaign {
        dialerApi.createCampaign(
            CreateCampaignRequest(
                name = name,
                description = description,
                status = status,
                startDate = startDate,
                endDate = endDate,
            ),
        )
    }

    /** Partial update (admin / company-owner). Null fields are omitted. */
    suspend fun update(
        id: Int,
        name: String? = null,
        description: String? = null,
        status: String? = null,
        startDate: String? = null,
        endDate: String? = null,
    ): Result<Campaign> = runCatchingCampaign {
        dialerApi.updateCampaign(
            id,
            UpdateCampaignRequest(
                name = name,
                description = description,
                status = status,
                startDate = startDate,
                endDate = endDate,
            ),
        )
    }

    /** Delete a campaign (admin / company-owner); drops it from the cache. */
    suspend fun delete(id: Int): Result<Unit> = try {
        dialerApi.deleteCampaign(id)
        _items.update { list -> list.filterNot { it.id == id } }
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "campaigns/$id delete failed", e)
        Result.failure(e)
    }

    /** Recompute target/contacted/conversion counts from the Lead table. */
    suspend fun refreshStats(id: Int): Result<Campaign> = runCatchingCampaign {
        dialerApi.refreshCampaignStats(id)
    }

    // --- helpers ---

    private suspend fun runCatchingCampaign(block: suspend () -> CampaignDto): Result<Campaign> = try {
        val campaign = block().toCampaign()
            ?: return Result.failure(IllegalStateException("Malformed campaign in response"))
        _items.update { it.upsert(campaign) }
        Result.success(campaign)
    } catch (e: Exception) {
        Log.e(TAG, "campaign mutation failed", e)
        Result.failure(e)
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
