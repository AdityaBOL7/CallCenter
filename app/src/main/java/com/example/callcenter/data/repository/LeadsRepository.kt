package com.example.callcenter.data.repository

import android.util.Log
import com.example.callcenter.data.remote.api.DialerApi
import com.example.callcenter.data.remote.dto.AssignLeadRequest
import com.example.callcenter.data.remote.dto.LeadDto
import com.example.callcenter.data.remote.dto.LeadPage
import com.example.callcenter.data.remote.dto.UpdateLeadRequest
import com.example.callcenter.domain.model.Lead
import com.example.callcenter.domain.model.LeadFilters
import com.example.callcenter.domain.model.LeadPriority
import com.example.callcenter.domain.model.LeadSort
import com.example.callcenter.domain.model.LeadStatus
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Singleton
class LeadsRepository @Inject constructor(
    private val dialerApi: DialerApi,
    private val json: Json,
) {

    private val _leads = MutableStateFlow<List<Lead>>(emptyList())
    val leads: StateFlow<List<Lead>> = _leads.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Fetch all leads from the backend into the cache. */
    suspend fun refresh(): Result<Unit> {
        return try {
            val element = dialerApi.listLeads()
            val dtos = parseLeadList(element)
            Log.d(TAG, "leads/ ← ${dtos.size} leads")
            _leads.value = dtos.mapNotNull { it.toLeadOrNull() }
            _error.value = null
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "leads/ failed", e)
            _error.value = e.message ?: "Couldn't load leads."
            Result.failure(e)
        }
    }

    fun observeLeads(filters: Flow<LeadFilters>): Flow<List<Lead>> =
        combine(_leads, filters) { source, f -> applyFilters(source, f) }

    fun observeLeads(filters: LeadFilters): Flow<List<Lead>> =
        _leads.map { applyFilters(it, filters) }

    /** Return a cached lead, or fetch it from the backend if not present. */
    suspend fun byId(id: Int): Lead? {
        _leads.value.firstOrNull { it.id == id }?.let { return it }
        return try {
            dialerApi.getLead(id).toLeadOrNull()?.also { fetched ->
                _leads.value = _leads.value.upsert(fetched)
            }
        } catch (e: Exception) {
            Log.e(TAG, "leads/$id failed", e)
            null
        }
    }

    suspend fun updateLead(
        id: Int,
        status: LeadStatus? = null,
        priority: LeadPriority? = null,
        notes: String? = null,
        tags: List<String>? = null,
    ): Result<Lead> = runCatchingLead {
        dialerApi.updateLead(
            id,
            UpdateLeadRequest(
                status = status?.toApi(),
                priority = priority?.toApi(),
                notes = notes,
                tags = tags,
            ),
        )
    }

    suspend fun assignToAgent(id: Int, agentId: Int?): Result<Lead> = runCatchingLead {
        dialerApi.assignLead(id, AssignLeadRequest(agentId = agentId))
    }

    suspend fun nextLead(): Lead? {
        if (_leads.value.isEmpty()) refresh()
        return _leads.value.firstOrNull { it.status == LeadStatus.NEW }
            ?: _leads.value.firstOrNull()
    }

    // --- helpers ---

    private suspend fun runCatchingLead(block: suspend () -> LeadDto): Result<Lead> = try {
        val lead = block().toLeadOrNull()
            ?: return Result.failure(IllegalStateException("Malformed lead in response"))
        _leads.value = _leads.value.upsert(lead)
        Result.success(lead)
    } catch (e: Exception) {
        Log.e(TAG, "lead mutation failed", e)
        Result.failure(e)
    }

    private fun List<Lead>.upsert(lead: Lead): List<Lead> {
        val idx = indexOfFirst { it.id == lead.id }
        return if (idx >= 0) toMutableList().also { it[idx] = lead } else this + lead
    }

    /** Accept both a bare array and a DRF {results:[...]} envelope. */
    private fun parseLeadList(element: JsonElement): List<LeadDto> = when (element) {
        is JsonArray -> json.decodeFromJsonElement(
            kotlinx.serialization.builtins.ListSerializer(LeadDto.serializer()), element,
        )
        is JsonObject -> json.decodeFromJsonElement(LeadPage.serializer(), element).results
        else -> emptyList()
    }

    private fun applyFilters(source: List<Lead>, filters: LeadFilters): List<Lead> {
        val text = filters.search.trim().lowercase()
        return source.asSequence()
            .filter { filters.status == null || it.status == filters.status }
            .filter { filters.campaignId == null || it.campaignId == filters.campaignId }
            .filter {
                text.isEmpty() ||
                    it.name.lowercase().contains(text) ||
                    it.phone.contains(text) ||
                    (it.email?.lowercase()?.contains(text) ?: false)
            }
            .toList()
            .let { list ->
                when (filters.sort) {
                    LeadSort.PRIORITY -> list.sortedByDescending { it.priority.weight }
                    LeadSort.RECENT -> list.sortedByDescending { it.lastContactedAt }
                    LeadSort.FOLLOW_UP -> list.sortedBy { it.nextCallbackAt ?: LocalDateTime.MAX }
                }
            }
    }

    private fun LeadDto.toLeadOrNull(): Lead? {
        val leadId = id ?: return null
        return Lead(
            id = leadId,
            name = name?.takeIf { it.isNotBlank() } ?: "Unknown",
            phone = phone.orEmpty(),
            email = email,
            campaignId = resolvedCampaignId ?: 0,
            campaignName = campaignName.orEmpty(),
            status = statusFromApi(status),
            priority = priorityFromApi(priority),
            nextCallbackAt = parseDate(nextCallbackAt),
            lastContactedAt = parseDate(lastContactedAt),
            notes = notes,
            address = null,
            tags = tags.orEmpty(),
        )
    }

    private fun statusFromApi(s: String?): LeadStatus = when (s?.lowercase()?.trim()) {
        "new" -> LeadStatus.NEW
        "contacted" -> LeadStatus.CONTACTED
        "interested" -> LeadStatus.INTERESTED
        "not_interested", "not-interested" -> LeadStatus.NOT_INTERESTED
        "follow_up", "follow-up", "followup" -> LeadStatus.FOLLOW_UP
        "converted" -> LeadStatus.CONVERTED
        "closed" -> LeadStatus.CLOSED
        // A brand-new lead the backend hasn't stamped yet has no status → treat as NEW.
        // An *unrecognized* non-empty status must NOT inflate the "new leads" count,
        // so it falls into CONTACTED rather than NEW.
        null, "" -> LeadStatus.NEW
        else -> LeadStatus.CONTACTED
    }

    private fun LeadStatus.toApi(): String = when (this) {
        LeadStatus.NEW -> "new"
        LeadStatus.CONTACTED -> "contacted"
        LeadStatus.INTERESTED -> "interested"
        LeadStatus.NOT_INTERESTED -> "not_interested"
        LeadStatus.FOLLOW_UP -> "follow_up"
        LeadStatus.CONVERTED -> "converted"
        LeadStatus.CLOSED -> "closed"
    }

    private fun priorityFromApi(p: String?): LeadPriority = when (p?.lowercase()) {
        "low" -> LeadPriority.LOW
        "medium" -> LeadPriority.MEDIUM
        "high" -> LeadPriority.HIGH
        "urgent" -> LeadPriority.URGENT
        else -> LeadPriority.MEDIUM
    }

    private fun LeadPriority.toApi(): String = when (this) {
        LeadPriority.LOW -> "low"
        LeadPriority.MEDIUM -> "medium"
        LeadPriority.HIGH -> "high"
        LeadPriority.URGENT -> "urgent"
    }

    /** Parse an ISO-8601 timestamp (with or without offset) to local date-time. */
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
        const val TAG = "Bol7Leads"
    }
}
