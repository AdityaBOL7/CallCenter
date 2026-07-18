package com.example.callcenter.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response of GET /api/dialer/auth/me/ — the authenticated agent's profile
 * plus the SIP credentials needed to register the softphone.
 *
 * The exact field set isn't documented with an example, so this is tolerant:
 * unknown keys are ignored (Json.ignoreUnknownKeys) and every field is nullable.
 * We'll tighten names once we see a real response in logcat.
 */
@Serializable
data class MeResponse(
    val id: Int? = null,
    @SerialName("display_name") val displayName: String? = null,
    val name: String? = null,
    val email: String? = null,
    val username: String? = null,
    val status: String? = null,
    @SerialName("caller_id_number") val callerIdNumber: String? = null,
    @SerialName("mobile_number") val mobileNumber: String? = null,
    // Backend-assigned dial mode: "sip" | "sim" | "voip". The agent does not
    // choose this; whoever provisions the agent sets it.
    @SerialName("call_mode") val callMode: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("extension") val extension: String? = null,

    // SIP block — may arrive flat or nested under "sip"; we accept both.
    @SerialName("sip_username") val sipUsername: String? = null,
    @SerialName("sip_password") val sipPassword: String? = null,
    @SerialName("sip_domain") val sipDomain: String? = null,
    val sip: SipBlock? = null,
) {
    @Serializable
    data class SipBlock(
        val username: String? = null,
        val password: String? = null,
        val domain: String? = null,
        val extension: String? = null,
    )

    val resolvedName: String?
        get() = displayName ?: name
    val resolvedSipUsername: String?
        get() = sipUsername ?: sip?.username
    val resolvedSipDomain: String?
        get() = sipDomain ?: sip?.domain
    val resolvedExtension: String?
        get() = extension ?: sip?.extension
}

/** Body for PATCH /api/dialer/auth/me/status/ — ready | busy | break | offline. */
@Serializable
data class ChangeStatusRequest(
    val status: String,
)

/** Response of POST /api/dialer/auth/me/avatar/ — the stored public image URL. */
@Serializable
data class AvatarResponse(
    @SerialName("avatar_url") val avatarUrl: String? = null,
)
