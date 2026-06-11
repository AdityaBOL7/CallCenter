package com.example.callcenter.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Step 1 — request an OTP to the registered email/phone. */
@Serializable
data class SendOtpRequest(
    val email: String,
    val purpose: String = "login",
)

/** Step 1 response is informational only; we tolerate any shape. */
@Serializable
data class SendOtpResponse(
    val detail: String? = null,
    val message: String? = null,
)

/** Step 2 — verify the OTP and receive tokens. */
@Serializable
data class VerifyOtpRequest(
    val email: String,
    val otp: String,
    val purpose: String = "login",
    // AUTH_Services rejects with 403 if this doesn't match the user's role.
    val role: String = "agent",
)

/**
 * AUTH_Services verify-otp response. The backend nests the payload under
 * `data` on success and uses `access_token`/`refresh_token`, but the Postman
 * test script also tolerates top-level + `access`/`refresh`, so we do too.
 */
@Serializable
data class VerifyOtpResponse(
    val access: String? = null,
    val refresh: String? = null,
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    val data: TokenData? = null,
    val detail: String? = null,
) {
    @Serializable
    data class TokenData(
        val access: String? = null,
        val refresh: String? = null,
        @SerialName("access_token") val accessToken: String? = null,
        @SerialName("refresh_token") val refreshToken: String? = null,
        val user: AuthUser? = null,
    )

    /** The authenticated user as returned by AUTH_Services (not the dialer Agent). */
    @Serializable
    data class AuthUser(
        val id: String? = null,
        val email: String? = null,
        @SerialName("phone_number") val phoneNumber: String? = null,
        @SerialName("first_name") val firstName: String? = null,
        @SerialName("last_name") val lastName: String? = null,
        @SerialName("profile_image") val profileImage: String? = null,
        val role: String? = null,
    ) {
        val fullName: String?
            get() = listOfNotNull(firstName?.takeIf { it.isNotBlank() }, lastName?.takeIf { it.isNotBlank() })
                .joinToString(" ").takeIf { it.isNotBlank() }
    }

    val resolvedAccess: String?
        get() = access ?: accessToken ?: data?.access ?: data?.accessToken

    val resolvedRefresh: String?
        get() = refresh ?: refreshToken ?: data?.refresh ?: data?.refreshToken

    val user: AuthUser?
        get() = data?.user
}

@Serializable
data class RefreshRequest(
    val refresh: String,
)

@Serializable
data class RefreshResponse(
    val access: String? = null,
    @SerialName("access_token") val accessToken: String? = null,
    val data: TokenData? = null,
) {
    @Serializable
    data class TokenData(
        val access: String? = null,
        @SerialName("access_token") val accessToken: String? = null,
    )

    val resolvedAccess: String?
        get() = access ?: accessToken ?: data?.access ?: data?.accessToken
}
