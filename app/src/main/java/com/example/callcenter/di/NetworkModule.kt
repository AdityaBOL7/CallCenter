package com.example.callcenter.di

import com.example.callcenter.BuildConfig
import com.example.callcenter.data.remote.AuthInterceptor
import com.example.callcenter.data.remote.InMemoryCookieJar
import com.example.callcenter.data.remote.TokenAuthenticator
import com.example.callcenter.data.remote.api.AuthApi
import com.example.callcenter.data.remote.api.DialerApi
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    // AUTH_Services — login / refresh. Trailing slash is required by Retrofit.
    private const val AUTH_BASE_URL = "https://testing1.bol7.com/auth2/"

    // Dialer backend — profile, leads, calls, callbacks, etc.
    private const val DIALER_BASE_URL = "https://callcenter.bol7.com/"

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
        // Without this, kotlinx-serialization omits any property equal to its
        // default value — which dropped `purpose`/`role` from request bodies and
        // made the server reject the call with "Invalid purpose."
        encodeDefaults = true
    }

    private fun loggingInterceptor() = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    // ---- AUTH client: no Bearer, carries the OTP session cookie ----
    @Provides
    @Singleton
    @Named("auth")
    fun provideAuthOkHttpClient(cookieJar: InMemoryCookieJar): OkHttpClient =
        OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .addInterceptor(loggingInterceptor())
            .build()

    @Provides
    @Singleton
    @Named("auth")
    fun provideAuthRetrofit(@Named("auth") client: OkHttpClient, json: Json): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(AUTH_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    fun provideAuthApi(@Named("auth") retrofit: Retrofit): AuthApi =
        retrofit.create(AuthApi::class.java)

    // ---- DIALER client: Bearer token + 401→refresh→retry ----
    @Provides
    @Singleton
    @Named("dialer")
    fun provideDialerOkHttpClient(
        authInterceptor: AuthInterceptor,
        tokenAuthenticator: TokenAuthenticator,
    ): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .authenticator(tokenAuthenticator)
            .addInterceptor(loggingInterceptor())
            .build()

    @Provides
    @Singleton
    @Named("dialer")
    fun provideDialerRetrofit(@Named("dialer") client: OkHttpClient, json: Json): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(DIALER_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    fun provideDialerApi(@Named("dialer") retrofit: Retrofit): DialerApi =
        retrofit.create(DialerApi::class.java)
}
