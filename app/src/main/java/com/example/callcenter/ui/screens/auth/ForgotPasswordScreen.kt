package com.example.callcenter.ui.screens.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.callcenter.data.repository.AuthRepository
import com.example.callcenter.ui.components.AppButton
import com.example.callcenter.ui.components.AppButtonSize
import com.example.callcenter.ui.components.AppInput
import com.example.callcenter.ui.components.PageHeader
import com.example.callcenter.ui.components.ScreenContainer
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ForgotPasswordViewModel @Inject constructor(
    private val authRepo: AuthRepository,
) : ViewModel() {
    data class State(val email: String = "", val submitting: Boolean = false,
                     val message: String? = null, val error: String? = null)

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()
    fun setEmail(v: String) = _state.update { it.copy(email = v, error = null) }
    fun submit() {
        viewModelScope.launch {
            _state.update { it.copy(submitting = true) }
            authRepo.forgotPassword(_state.value.email)
                .onSuccess { _state.update { it.copy(submitting = false, message = "Reset link sent to your email", error = null) } }
                .onFailure { e -> _state.update { it.copy(submitting = false, error = e.message) } }
        }
    }
}

@Composable
fun ForgotPasswordScreen(
    onBack: () -> Unit,
    viewModel: ForgotPasswordViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    ScreenContainer {
        Column(modifier = Modifier.fillMaxSize()) {
            PageHeader(title = "Forgot password", onBack = onBack)
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    "Enter your email and we'll send you a reset link.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                AppInput(
                    value = state.email,
                    onValueChange = viewModel::setEmail,
                    label = "Email",
                    placeholder = "agent@company.com",
                    leadingIcon = Icons.Outlined.Email,
                    keyboardType = KeyboardType.Email,
                    isError = state.error != null,
                )
                if (state.error != null) Text(state.error!!, color = MaterialTheme.colorScheme.error)
                if (state.message != null) Text(state.message!!, color = MaterialTheme.colorScheme.primary)
                AppButton(
                    text = "Send reset link",
                    onClick = viewModel::submit,
                    loading = state.submitting,
                    fullWidth = true,
                    size = AppButtonSize.Lg,
                )
            }
        }
    }
}
