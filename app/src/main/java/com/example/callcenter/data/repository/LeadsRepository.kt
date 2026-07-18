package com.example.callcenter.data.repository

import android.util.Log
import com.example.callcenter.data.remote.api.DialerApi
import com.example.callcenter.data.remote.dto.AssignLeadRequest
import com.example.callcenter.data.remote.dto.DeleteWithKeyRequest
import com.example.callcenter.data.remote.dto.LeadDto
import com.example.callcenter.data.remote.dto.LeadPage
import com.example.callcenter.data.remote.dto.UpdateLeadRequest
import com.example.callcenter.domain.model.Lead
import com.example.callcenter.domain.model.LeadFilters
import com.example.callcenter.domain.model.LeadPriority
import com.example.callcenter.domain.model.LeadSort
import com.example.callcenter.domain.model.LeadStatus
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
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

    // Own scope for fire-and-forget refreshes that must survive the CALLER
    // being torn down (e.g. the disposition screen navigating away right after
    // submit — a viewModelScope launch there gets cancelled mid-flight).
    private val repoScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO,
    )

    /**
     * Fire-and-forget [refresh]. Used right after a disposition: the backend
     * re-buckets the lead's status at hangup, so pulling the list now makes the
     * new bucket visible without a manual pull-to-refresh.
     */
    fun refreshSoon() {
        repoScope.launch { refresh() }
    }

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

    /**
     * Delete this agent's NEW (untouched) leads via POST /leads/delete-with-key/,
     * gated by a one-shot 6-digit action key the client generates from their
     * panel (Agent Action Keys). The server validates and burns the key.
     *
     * SCOPE: only status == NEW is deleted — leads the agent has already worked
     * (Contacted / Interested / Not-interested / Follow-up / Converted / Closed)
     * are kept, so a wrong-batch import can be wiped without losing progress.
     *
     * Failure mapping: HTTP 423 = the agent is LOCKED after 5 wrong keys (an
     * admin must unlock via /agents/<id>/unlock-action-key/); 400/403/404 =
     * invalid, already-used, or revoked key.
     */
    suspend fun deleteAllLeads(key: String): Result<Unit> {
        // Refresh first if the cache is cold so the NEW set reflects the server.
        if (_leads.value.isEmpty()) refresh()
        // Only fresh, un-worked leads are eligible for deletion.
        val ids = _leads.value.filter { it.status == LeadStatus.NEW }.map { it.id }
        if (ids.isEmpty()) {
            return Result.failure(IllegalStateException("There are no new leads to delete."))
        }
        return try {
            val resp = dialerApi.deleteLeadsWithKey(DeleteWithKeyRequest(key = key, leadIds = ids))
            Log.d(TAG, "delete-with-key ← deleted=${resp.deleted} skipped=${resp.skipped} key_state=${resp.keyState}")
            // Prune exactly what the server reports deleted (fall back to all
            // requested ids if it doesn't echo the list).
            val deletedIds = resp.deletedLeadIds.ifEmpty { ids }.toSet()
            _leads.value = _leads.value.filterNot { it.id in deletedIds }
            Result.success(Unit)
        } catch (e: retrofit2.HttpException) {
            Log.e(TAG, "delete-with-key ← HTTP ${e.code()}", e)
            val message = when (e.code()) {
                423 -> "Too many wrong keys — deletion is locked for your account. Ask your admin to unlock it."
                400, 403, 404 -> "That key is invalid, already used, or revoked. Ask your client for a fresh key."
                else -> "Couldn't delete leads (HTTP ${e.code()}). Try again."
            }
            Result.failure(Exception(message, e))
        } catch (e: kotlinx.serialization.SerializationException) {
            // The DELETE returned 2xx (server DID delete) but we couldn't parse the
            // body — do NOT report failure, or the agent thinks nothing happened
            // and retries a completed delete. Treat as success and re-sync truth.
            Log.w(TAG, "delete-with-key succeeded but response body didn't parse; refreshing", e)
            refresh()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "delete-with-key failed", e)
            Result.failure(Exception("Network error — leads were not deleted. Try again.", e))
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
        // Untried leads first, then no-answer retries — both are callable.
        return _leads.value.firstOrNull { it.status == LeadStatus.NEW }
            ?: _leads.value.firstOrNull { it.status == LeadStatus.NO_ANSWER }
            ?: _leads.value.firstOrNull()
    }

    /**
     * Ordered list of leads to auto-dial: untried (NEW) first by priority, then
     * NO_ANSWER retries — a lead that left NEW because nobody picked up must
     * stay in the dialing loop, else it silently becomes uncallable.
     * Refreshes from the backend first so the queue reflects the latest leads.
     */
    suspend fun queueForAutoDial(): List<Lead> {
        refresh()
        return _leads.value
            .filter { it.status == LeadStatus.NEW || it.status == LeadStatus.NO_ANSWER }
            .sortedWith(
                compareBy<Lead> { it.status != LeadStatus.NEW }
                    .thenByDescending { it.priority.weight },
            )
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
            createdAt = parseDate(createdAt),
            notes = notes,
            address = null,
            tags = tags.orEmpty(),
        )
    }

    private fun statusFromApi(s: String?): LeadStatus = when (s?.lowercase()?.trim()) {
        "new" -> LeadStatus.NEW
        "no_answer", "no-answer" -> LeadStatus.NO_ANSWER
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
        LeadStatus.NO_ANSWER -> "no_answer"
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
            // Convert the server's UTC/offset time to the DEVICE's local zone
            // BEFORE dropping the offset. (toLocalDateTime() alone keeps the raw
            // UTC wall-clock — e.g. showing 8:28 instead of 1:58 PM IST.)
            OffsetDateTime.parse(raw).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
        } catch (_: DateTimeParseException) {
            try {
                // Bare "…Z" instants → also anchor to local.
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
        const val TAG = "Bol7Leads"
    }
}
