package com.example.callcenter.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.callcenter.data.repository.AgentRepository
import com.example.callcenter.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which step of the OTP login flow the UI is showing. */
enum class LoginStep { IDENTIFIER, OTP }

/** How the agent identifies themselves for OTP. Both hit the same endpoint. */
enum class LoginMode { EMAIL, PHONE }

data class LoginUiState(
    val step: LoginStep = LoginStep.IDENTIFIER,
    val mode: LoginMode = LoginMode.EMAIL,
    val identifier: String = "",
    val otp: String = "",
    val submitting: Boolean = false,
    val error: String? = null,
    val info: String? = null,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepo: AuthRepository,
    private val agentRepo: AgentRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun setMode(mode: LoginMode) = _state.update {
        // Switching mode clears the identifier so an email isn't left in a phone field.
        if (it.mode == mode) it else it.copy(mode = mode, identifier = "", error = null)
    }

    fun setIdentifier(v: String) = _state.update { it.copy(identifier = v, error = null) }

    fun setOtp(v: String) = _state.update {
        // Accept alphanumeric codes (letters + digits); cap at 6 and drop whitespace
        // so a pasted code like "1 2 3 4 5 6" still works. Uppercase to match the
        // backend's case-sensitive codes AND the uppercased display in OtpBoxes —
        // otherwise the user sees "KSUNQ2" but the app sends "ksunq2" → Invalid OTP.
        it.copy(otp = v.filter { c -> c.isLetterOrDigit() }.take(6).uppercase(), error = null)
    }

    /** Step back to editing the email/phone. */
    fun changeIdentifier() = _state.update {
        it.copy(step = LoginStep.IDENTIFIER, otp = "", error = null, info = null)
    }

    /** Step 1 — send the OTP, then advance to the OTP entry step. */
    fun sendOtp() {
        val s = _state.value
        // Reentrancy guard: a rapid double-tap before `submitting` flips would
        // otherwise launch a second send (the button only disables on the next frame).
        if (s.submitting) return
        if (s.identifier.isBlank()) {
            _state.update { it.copy(error = "Enter your registered email or phone") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(submitting = true, error = null, info = null) }
            authRepo.sendOtp(s.identifier)
                .onSuccess {
                    _state.update {
                        it.copy(
                            submitting = false,
                            step = LoginStep.OTP,
                            info = "We sent a 6-digit code to ${s.identifier.trim()}",
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(submitting = false, error = e.message ?: "Couldn't send OTP") }
                }
        }
    }

    /** Resend the OTP without leaving the OTP step. */
    fun resendOtp() {
        val s = _state.value
        if (s.submitting) return
        viewModelScope.launch {
            _state.update { it.copy(submitting = true, error = null, info = null) }
            authRepo.sendOtp(s.identifier)
                .onSuccess { _state.update { it.copy(submitting = false, info = "A new code has been sent.") } }
                .onFailure { e -> _state.update { it.copy(submitting = false, error = e.message ?: "Couldn't resend OTP") } }
        }
    }

    /** Step 2 — verify the entered OTP and sign in. */
    fun verifyOtp(onSuccess: () -> Unit) {
        val s = _state.value
        // Reentrancy guard: stop a double-tap from launching two verifies (each of
        // which would call onSuccess() / navigate) before `submitting` disables the button.
        if (s.submitting) return
        if (s.otp.length != 6) {
            _state.update { it.copy(error = "Enter the 6-digit code") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(submitting = true, error = null) }
            authRepo.verifyOtp(s.identifier, s.otp)
                .onSuccess {
                    // Warm the agent profile while still on the login screen. The
                    // first me/ after login often races the token-refresh (raw JWT
                    // → Fernet-wrapped) and gets cancelled; retry until we get a
                    // real profile so call_mode/status are known before the
                    // dashboard needs them. Bounded so a genuine failure still ends.
                    var attempts = 0
                    while (attempts < 3 && !agentRepo.loadProfile(force = true).isSuccess) {
                        attempts++
                        delay(400)
                    }
                    _state.update { it.copy(submitting = false) }
                    onSuccess()
                }
                .onFailure { e ->
                    _state.update { it.copy(submitting = false, error = e.message ?: "Verification failed") }
                }
        }
    }
}
