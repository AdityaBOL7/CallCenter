package com.example.callcenter.data.repository

import com.example.callcenter.domain.model.AuthStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class AuthRepository @Inject constructor() {

    private val _authStatus = MutableStateFlow(AuthStatus.UNAUTHENTICATED)
    val authStatus: StateFlow<AuthStatus> = _authStatus.asStateFlow()

    suspend fun login(username: String, password: String): Result<Unit> {
        _authStatus.value = AuthStatus.LOADING
        delay(600)
        return if (username.isNotBlank() && password.isNotBlank()) {
            _authStatus.value = AuthStatus.AUTHENTICATED
            Result.success(Unit)
        } else {
            _authStatus.value = AuthStatus.UNAUTHENTICATED
            Result.failure(IllegalArgumentException("Username and password are required"))
        }
    }

    suspend fun forgotPassword(email: String): Result<Unit> {
        delay(500)
        return if (email.contains("@")) Result.success(Unit)
        else Result.failure(IllegalArgumentException("Enter a valid email"))
    }

    fun logout() {
        _authStatus.value = AuthStatus.UNAUTHENTICATED
    }
}
