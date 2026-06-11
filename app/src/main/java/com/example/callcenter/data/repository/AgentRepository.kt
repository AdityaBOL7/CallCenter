package com.example.callcenter.data.repository

import android.util.Log
import com.example.callcenter.data.remote.api.DialerApi
import com.example.callcenter.data.remote.dto.ChangeStatusRequest
import com.example.callcenter.data.remote.dto.MeResponse
import com.example.callcenter.domain.model.Agent
import com.example.callcenter.domain.model.AgentStats
import com.example.callcenter.domain.model.AgentStatus
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    /**
     * Fetch GET /api/dialer/auth/me/ once and publish the profile.
     * Skips the network call if we already have a profile, unless force=true.
     */
    suspend fun loadProfile(force: Boolean = false): Result<Unit> = loadMutex.withLock {
        if (!force && _agent.value != null) return Result.success(Unit)
        return try {
            val me = dialerApi.getMe()
            Log.d(TAG, "me/ ← $me")
            _agent.value = me.toAgent()
            _profileError.value = null
            Result.success(Unit)
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
            status = AgentStatus.OFFLINE,
        )
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
    }

    private fun MeResponse.toAgent(): Agent = Agent(
        id = id ?: 0,
        name = resolvedName ?: username ?: email ?: "Agent",
        email = email.orEmpty(),
        username = username ?: email.orEmpty(),
        extension = resolvedExtension.orEmpty(),
        sipUsername = resolvedSipUsername.orEmpty(),
        avatarUrl = avatarUrl,
        status = status?.let(::statusFromApi) ?: AgentStatus.OFFLINE,
    )

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
