package com.example.callcenter.navigation

import androidx.lifecycle.ViewModel
import com.example.callcenter.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class RootViewModel @Inject constructor(
    authRepository: AuthRepository,
) : ViewModel() {
    val authStatus: StateFlow<com.example.callcenter.domain.model.AuthStatus> = authRepository.authStatus
}
