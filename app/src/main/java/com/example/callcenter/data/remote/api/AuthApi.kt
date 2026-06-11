package com.example.callcenter.data.remote.api

import com.example.callcenter.data.remote.dto.RefreshRequest
import com.example.callcenter.data.remote.dto.RefreshResponse
import com.example.callcenter.data.remote.dto.SendOtpRequest
import com.example.callcenter.data.remote.dto.SendOtpResponse
import com.example.callcenter.data.remote.dto.VerifyOtpRequest
import com.example.callcenter.data.remote.dto.VerifyOtpResponse
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * AUTH_Services endpoints. Base URL: https://testing1.bol7.com/auth2/
 *
 * Bol7 uses a two-step OTP login (no username/password):
 *   1. send-otp-mobile  — triggers an OTP to the registered email/phone
 *   2. verify-otp-mobile — verifies the OTP and returns access/refresh tokens
 * The `-mobile` variants skip Cloudflare Turnstile captcha (right for native apps).
 * All three are unauthenticated.
 */
interface AuthApi {

    @POST("api/auth/send-otp-mobile/")
    suspend fun sendOtpMobile(@Body body: SendOtpRequest): SendOtpResponse

    @POST("api/auth/verify-otp-mobile/")
    suspend fun verifyOtpMobile(@Body body: VerifyOtpRequest): VerifyOtpResponse

    @POST("api/refresh/")
    suspend fun refresh(@Body body: RefreshRequest): RefreshResponse
}
