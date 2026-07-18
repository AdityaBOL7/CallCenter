package com.example.callcenter.data.remote.api

import com.example.callcenter.data.remote.dto.RefreshRequest
import com.example.callcenter.data.remote.dto.RefreshResponse
import com.example.callcenter.data.remote.dto.SendOtpRequest
import com.example.callcenter.data.remote.dto.SendOtpResponse
import com.example.callcenter.data.remote.dto.VerifyOtpRequest
import com.example.callcenter.data.remote.dto.VerifyOtpResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * AUTH_Services endpoints. Base URL: https://next.bol7.com/auth2/
 *
 * Bol7 uses a two-step OTP login (no username/password):
 *   1. send-otp-mobile  — triggers an OTP to the registered email/phone
 *   2. verify-otp-mobile — verifies the OTP and returns access/refresh tokens
 * The `-mobile` variants skip Cloudflare Turnstile captcha (right for native apps).
 * All three are unauthenticated.
 *
 * verify/refresh return retrofit2.Response because the ENCRYPTED tokens (the
 * only format the callcenter backend accepts as Bearer since 2026-07-16) are
 * delivered via Set-Cookie headers, not the JSON body — see [SetCookies].
 */
interface AuthApi {

    @POST("api/auth/send-otp-mobile/")
    suspend fun sendOtpMobile(@Body body: SendOtpRequest): SendOtpResponse

    @POST("api/auth/verify-otp-mobile/")
    suspend fun verifyOtpMobile(@Body body: VerifyOtpRequest): Response<VerifyOtpResponse>

    /**
     * Trade the refresh token for new tokens. Tokens ROTATE: every successful
     * refresh revokes the old refresh token, so the caller MUST persist the
     * replacement. The refresh is also sent as a Cookie header (the web
     * frontend's flow) to coax the server into answering with Set-Cookie
     * encrypted tokens rather than body JWTs.
     */
    @POST("api/refresh/")
    suspend fun refresh(
        @Body body: RefreshRequest,
        @Header("Cookie") cookie: String? = null,
    ): Response<RefreshResponse>
}
