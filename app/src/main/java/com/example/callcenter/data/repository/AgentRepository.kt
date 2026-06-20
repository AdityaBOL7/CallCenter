package com.example.callcenter.data.repository

import android.util.Log
import com.example.callcenter.data.remote.api.DialerApi
import com.example.callcenter.data.remote.dto.ChangeStatusRequest
import com.example.callcenter.data.remote.dto.MeResponse
import com.example.callcenter.domain.model.Agent
import com.example.callcenter.domain.model.AgentStats
import com.example.callcenter.domain.model.AgentStatus
import com.example.callcenter.domain.model.CallRouteType
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import retrofit2.HttpException

@Singleton
class AgentRepository @Inject constructor(
    private val dialerApi: DialerApi,
    private val authRepo: AuthRepository,
) {

    // Null until the first successful me/ load; Profile shows "—" placeholders.
    private val _agent = MutableStateFlow<Agent?>(null)
    val agent: StateFlow<Agent?> = _agent.asStateFlow()

    // Stats are not part of me/; left at zero until calls/reporting feed them.
    private val _stats = MutableStateFlow(AgentStats())
    val stats: StateFlow<AgentStats> = _stats.asStateFlow()

    // Surfaces *why* the profile is empty (e.g. "not provisioned as a dialer agent").
    private val _profileError = MutableStateFlow<String?>(null)
    val profileError: StateFlow<String?> = _profileError.asStateFlow()

    // Dedupe: Dashboard + Profile both ask for me/ on init.
    private val loadMutex = Mutex()

    // Long-lived scope for best-effort background syncs (e.g. pushing the
    // forced-Available status to the backend) that must outlive the caller.
    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // On the first profile load after login the agent is forced Available
    // regardless of the backend's stored status (and any earlier session's
    // Break/Busy). Cleared after that first load and reset by logout(), so a
    // later me/ refresh never overrides the agent's own explicit choice.
    private var forceAvailableOnLoad = true

    /**
     * Fetch GET /api/dialer/auth/me/ once and publish the profile.
     * Skips the network call if we already have a profile, unless force=true.
     */
    // True once we have a REAL me/ profile (not just the login-seeded placeholder).
    // A seeded placeholder must not block a later real me/ load — otherwise a me/
    // that was cancelled at login (by the token refresh racing it) would leave us
    // permanently on the SIP default with no real call_mode.
    @Volatile private var hasRealProfile = false

    suspend fun loadProfile(force: Boolean = false): Result<Unit> = loadMutex.withLock {
        // Skip only if we already have a *real* profile (not a seeded placeholder).
        if (!force && hasRealProfile) return Result.success(Unit)
        return try {
            val me = dialerApi.getMe()
            Log.d(TAG, "me/ ← $me")
            _agent.value = me.toAgent().applyLoginStatusOverride()
            hasRealProfile = true
            _profileError.value = null
            Result.success(Unit)
        } catch (e: CancellationException) {
            // me/ was interrupted. If OUR caller's coroutine is the one being
            // cancelled, honour it and rethrow. Otherwise the cancellation came
            // from the underlying call being aborted (e.g. the token-refresh at
            // login racing it) — that's NOT a real failure: don't seed a SIP
            // placeholder, just report failure so the caller retries.
            if (!coroutineContext.isActive) {
                Log.w(TAG, "me/ cancelled by caller", e)
                throw e
            }
            Log.w(TAG, "me/ call aborted (token race); will retry", e)
            _profileError.value = null
            Result.failure(e)
        } catch (e: HttpException) {
            val detail = serverDetail(e)
            Log.e(TAG, "me/ ← HTTP ${e.code()} $detail", e)
            seedFromLoginUser()
            _profileError.value = detail ?: "Couldn't load profile (HTTP ${e.code()})."
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "me/ failed", e)
            seedFromLoginUser()
            _profileError.value = if (e is IOException) {
                "Network error loading profile."
            } else {
                e.message ?: "Couldn't load profile."
            }
            Result.failure(e)
        }
    }

    /**
     * When the dialer profile (me/) is unavailable — e.g. the user isn't yet
     * provisioned as a dialer agent — fall back to the identity captured from
     * the login response so the UI shows the real name instead of "Agent".
     * SIP/extension stay blank because those only exist once provisioned.
     */
    private fun seedFromLoginUser() {
        if (_agent.value != null) return
        val u = authRepo.cachedUser ?: return
        _agent.value = Agent(
            id = 0,
            name = u.fullName ?: u.email ?: "Agent",
            email = u.email.orEmpty(),
            username = u.email.orEmpty(),
            extension = "",
            sipUsername = "",
            avatarUrl = u.profileImage,
            // Fresh login defaults to Available, not Offline (see applyLoginStatusOverride).
            status = if (forceAvailableOnLoad) AgentStatus.AVAILABLE else AgentStatus.OFFLINE,
        ).also { forceAvailableOnLoad = false }
    }

    /**
     * On the first load after login, force the agent Available regardless of the
     * backend's stored status, and best-effort push that to the server so the two
     * stay in sync. After this one-shot, the agent's own status choices win.
     */
    private fun Agent.applyLoginStatusOverride(): Agent {
        if (!forceAvailableOnLoad) return this
        forceAvailableOnLoad = false
        if (status != AgentStatus.AVAILABLE) {
            // Best-effort; don't block profile load or fail it if this errors.
            repoScope.launch {
                runCatching { dialerApi.changeStatus(ChangeStatusRequest(status = "ready")) }
            }
        }
        return copy(status = AgentStatus.AVAILABLE)
    }

    private fun serverDetail(e: HttpException): String? = try {
        e.response()?.errorBody()?.string()
            ?.let { Regex("\"detail\"\\s*:\\s*\"([^\"]+)\"").find(it)?.groupValues?.get(1) }
    } catch (_: Exception) {
        null
    }

    /** PATCH the presence status; optimistically update local state on success. */
    suspend fun setStatus(status: AgentStatus): Result<Unit> {
        return try {
            val me = dialerApi.changeStatus(ChangeStatusRequest(status = status.toApi()))
            // Prefer the server's echo; fall back to the requested status.
            val applied = me.status?.let(::statusFromApi) ?: status
            _agent.value = _agent.value?.copy(status = applied)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "changeStatus failed", e)
            Result.failure(e)
        }
    }

    /**
     * Fetch the dashboard KPIs (GET /api/dialer/dashboard/) and publish them as
     * agent stats so the Home screen shows real numbers. Best-effort: on failure
     * the existing stats are kept (no exception thrown to the caller).
     */
    suspend fun loadDashboard(): Result<Unit> = try {
        val element = dialerApi.getDashboard()
        val totals = (element as? JsonObject)?.get("totals") as? JsonObject
        if (totals != null) {
            fun int(vararg keys: String): Int {
                for (k in keys) {
                    val v = (totals[k] as? JsonPrimitive)?.intOrNull
                    if (v != null) return v
                }
                return 0
            }
            fun str(vararg keys: String): String? {
                for (k in keys) {
                    val v = (totals[k] as? JsonPrimitive)?.contentOrNull
                    if (!v.isNullOrBlank()) return v
                }
                return null
            }
            _stats.value = AgentStats(
                totalCalls = int("calls_today", "total_calls", "calls"),
                connectedCalls = int("connected_calls", "connected"),
                missedCalls = int("missed_calls", "pending_calls", "missed"),
                callbacksDue = int("followups_tomorrow", "callbacks_due", "followups"),
                conversions = int("sales", "conversions", "converted"),
                // newLeads is derived from the live leads list in the dashboard VM.
                newLeads = _stats.value.newLeads,
                totalLeads = int("leads", "total_leads"),
                recordings = int("recordings"),
                talkTime = str("total_talk_time_str", "total_talk_time") ?: "0m",
            )
            Log.d(TAG, "dashboard/ ← mapped stats=${_stats.value}")
        } else {
            Log.w(TAG, "dashboard/ ← no 'totals' object in response")
        }
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "dashboard/ failed", e)
        Result.failure(e)
    }

    /** Best-effort server-side logout; never throws to the caller. */
    suspend fun serverLogout() {
        try {
            dialerApi.logout()
        } catch (e: Exception) {
            Log.w(TAG, "server logout failed (ignored)", e)
        }
    }

    fun clear() {
        _agent.value = null
        _stats.value = AgentStats()
        hasRealProfile = false
        // Next login should again force Available on first profile load.
        forceAvailableOnLoad = true
    }

    private fun MeResponse.toAgent(): Agent = Agent(
        id = id ?: 0,
        name = cleanName(resolvedName ?: username ?: email ?: "Agent"),
        email = email.orEmpty(),
        username = username ?: email.orEmpty(),
        extension = resolvedExtension.orEmpty(),
        sipUsername = resolvedSipUsername.orEmpty(),
        avatarUrl = avatarUrl,
        status = status?.let(::statusFromApi) ?: AgentStatus.OFFLINE,
        callMode = callModeFromApi(callMode),
        mobileNumber = mobileNumber,
    )

    /**
     * The backend tags demo/seed agents with a "DEMO::" prefix on the display
     * name (e.g. "DEMO::Aditya Dev"). Strip it for display so agents see a clean
     * name. Harmless for non-demo agents (no prefix → unchanged).
     */
    private fun cleanName(raw: String): String =
        raw.removePrefix("DEMO::").trim().ifBlank { raw }

    /** Map the backend call_mode ("sip" | "sim" | "voip") to a route type. */
    private fun callModeFromApi(mode: String?): CallRouteType = when (mode?.lowercase()?.trim()) {
        "sim" -> CallRouteType.MOBILE
        // "sip" and "voip" both run the in-app call screen for now.
        else -> CallRouteType.SIP
    }

    private fun statusFromApi(api: String): AgentStatus = when (api.lowercase()) {
        "ready", "available" -> AgentStatus.AVAILABLE
        "busy" -> AgentStatus.BUSY
        "break" -> AgentStatus.BREAK
        else -> AgentStatus.OFFLINE
    }

    private fun AgentStatus.toApi(): String = when (this) {
        AgentStatus.AVAILABLE -> "ready"
        AgentStatus.BUSY -> "busy"
        AgentStatus.BREAK -> "break"
        AgentStatus.OFFLINE -> "offline"
    }

    private companion object {
        const val TAG = "Bol7Agent"
    }
}
