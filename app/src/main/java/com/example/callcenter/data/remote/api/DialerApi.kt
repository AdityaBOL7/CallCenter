package com.example.callcenter.data.remote.api

import com.example.callcenter.data.remote.dto.AssignLeadRequest
import com.example.callcenter.data.remote.dto.CallDto
import com.example.callcenter.data.remote.dto.CallbackDto
import com.example.callcenter.data.remote.dto.CampaignDto
import com.example.callcenter.data.remote.dto.ChangeStatusRequest
import com.example.callcenter.data.remote.dto.CompleteCallbackRequest
import com.example.callcenter.data.remote.dto.HangupRequest
import com.example.callcenter.data.remote.dto.LeadDto
import com.example.callcenter.data.remote.dto.MeResponse
import com.example.callcenter.data.remote.dto.UnreadCountDto
import com.example.callcenter.data.remote.dto.ScheduleCallbackRequest
import com.example.callcenter.data.remote.dto.StartCallRequest
import com.example.callcenter.data.remote.dto.UpdateCallRequest
import com.example.callcenter.data.remote.dto.UpdateCallbackRequest
import com.example.callcenter.data.remote.dto.UpdateLeadRequest
import kotlinx.serialization.json.JsonElement
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Bol7 Dialer backend. Base URL: https://callcenter.bol7.com/
 * All endpoints require a Bearer JWT (added by AuthInterceptor).
 */
interface DialerApi {

    /** Authenticated agent's profile + SIP block. Stamps last_seen_at. */
    @GET("api/dialer/auth/me/")
    suspend fun getMe(): MeResponse

    /** Change presence: ready | busy | break | offline. */
    @PATCH("api/dialer/auth/me/status/")
    suspend fun changeStatus(@Body body: ChangeStatusRequest): MeResponse

    /** Server-side logout: clears FCM token, closes status interval, sets offline. */
    @POST("api/dialer/auth/logout/")
    suspend fun logout()

    // ---- Leads ----

    /**
     * List leads. Returns either a bare JSON array or a DRF page envelope
     * ({count,next,previous,results}); the repo normalises both. Filters are
     * optional; blank values are omitted by passing null.
     */
    @GET("api/dialer/leads/")
    suspend fun listLeads(
        @Query("status") status: String? = null,
        @Query("priority") priority: String? = null,
        @Query("search") search: String? = null,
        @Query("campaign_id") campaignId: Int? = null,
        @Query("assigned_agent_id") assignedAgentId: String? = null,
    ): JsonElement

    @GET("api/dialer/leads/{id}/")
    suspend fun getLead(@Path("id") id: Int): LeadDto

    @PATCH("api/dialer/leads/{id}/")
    suspend fun updateLead(@Path("id") id: Int, @Body body: UpdateLeadRequest): LeadDto

    @POST("api/dialer/leads/{id}/assign/")
    suspend fun assignLead(@Path("id") id: Int, @Body body: AssignLeadRequest): LeadDto

    // ---- Dispositions ----

    /**
     * Active disposition codes for the agent's tenant. Returns a bare array or a
     * DRF page envelope; the repo normalises both.
     */
    @GET("api/dialer/dispositions/")
    suspend fun listDispositions(): JsonElement

    // ---- Calls ----

    /** Start a call (records metadata in `initiating` status). Returns id + rpdp_uid. */
    @POST("api/dialer/calls/")
    suspend fun startCall(@Body body: StartCallRequest): CallDto

    /** In-call update: status transitions + mute/hold/recording/note. */
    @PATCH("api/dialer/calls/{id}/")
    suspend fun updateCall(@Path("id") id: Int, @Body body: UpdateCallRequest): CallDto

    /** Final hangup with disposition + note. Stamps ended_at, computes duration. */
    @POST("api/dialer/calls/{id}/hangup/")
    suspend fun hangupCall(@Path("id") id: Int, @Body body: HangupRequest): CallDto

    @GET("api/dialer/calls/{id}/")
    suspend fun getCall(@Path("id") id: Int): CallDto

    /** Call history (bare array or DRF page envelope; repo normalises). */
    @GET("api/dialer/calls/")
    suspend fun listCalls(
        @Query("direction") direction: String? = null,
        @Query("status") status: String? = null,
        @Query("search") search: String? = null,
    ): JsonElement

    /** Calls currently initiating | ringing | connected for this agent. */
    @GET("api/dialer/calls/live/")
    suspend fun listLiveCalls(): JsonElement

    // ---- Callbacks ----

    /** List callbacks (bare array or DRF page envelope; repo normalises). */
    @GET("api/dialer/callbacks/")
    suspend fun listCallbacks(
        @Query("status") status: String? = null,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
    ): JsonElement

    @POST("api/dialer/callbacks/")
    suspend fun scheduleCallback(@Body body: ScheduleCallbackRequest): CallbackDto

    @PATCH("api/dialer/callbacks/{id}/")
    suspend fun updateCallback(@Path("id") id: Int, @Body body: UpdateCallbackRequest): CallbackDto

    /** Soft-cancel: sets status=cancelled (row preserved). */
    @DELETE("api/dialer/callbacks/{id}/")
    suspend fun cancelCallback(@Path("id") id: Int)

    @POST("api/dialer/callbacks/{id}/complete/")
    suspend fun completeCallback(@Path("id") id: Int, @Body body: CompleteCallbackRequest): CallbackDto

    /** Pending callbacks due within the next N minutes (default 30). */
    @GET("api/dialer/callbacks/due/")
    suspend fun callbacksDue(@Query("minutes") minutes: Int = 30): JsonElement

    // ---- Campaigns ----

    /** List campaigns (bare array or DRF page envelope; repo normalises). */
    @GET("api/dialer/campaigns/")
    suspend fun listCampaigns(
        @Query("status") status: String? = null,
        @Query("search") search: String? = null,
    ): JsonElement

    @GET("api/dialer/campaigns/{id}/")
    suspend fun getCampaign(@Path("id") id: Int): CampaignDto

    // ---- Notifications (in-app inbox; no FCM) ----

    /** List notifications (bare array or DRF page envelope; repo normalises). */
    @GET("api/dialer/notifications/")
    suspend fun listNotifications(
        @Query("kind") kind: String? = null,
        @Query("unread") unread: String? = null,
    ): JsonElement

    @GET("api/dialer/notifications/unread-count/")
    suspend fun unreadCount(): UnreadCountDto

    @POST("api/dialer/notifications/{id}/read/")
    suspend fun markNotificationRead(@Path("id") id: Int)

    @POST("api/dialer/notifications/mark-all-read/")
    suspend fun markAllNotificationsRead()

    @DELETE("api/dialer/notifications/{id}/")
    suspend fun dismissNotification(@Path("id") id: Int)
}
