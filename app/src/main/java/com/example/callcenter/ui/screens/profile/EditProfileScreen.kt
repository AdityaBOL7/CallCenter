package com.example.callcenter.ui.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.example.callcenter.data.repository.AgentRepository
import com.example.callcenter.data.repository.AuthRepository
import com.example.callcenter.ui.components.AppInput
import com.example.callcenter.ui.components.PageHeader
import com.example.callcenter.ui.components.ScreenContainer
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class EditProfileViewModel @Inject constructor(
    agentRepo: AgentRepository,
    private val authRepo: AuthRepository,
) : ViewModel() {
    val agent = agentRepo.agent

    // Extra identity bits from the login response that aren't on the Agent model.
    val phone: String? get() = authRepo.cachedUser?.phoneNumber
    val role: String? get() = authRepo.cachedUser?.role
}

/**
 * Read-only profile detail view. The dialer backend has no self-update endpoint
 * (GET me/ only; edits are an admin-side agent update), so fields are display-only.
 */
@Composable
fun EditProfileScreen(
    onBack: () -> Unit,
    viewModel: EditProfileViewModel = hiltViewModel(),
) {
    val agent by viewModel.agent.collectAsState()
    ScreenContainer {
        Column(Modifier.fillMaxSize()) {
            PageHeader(title = "Profile details", onBack = onBack)
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                ReadOnlyField(label = "Name", value = agent?.name, icon = Icons.Outlined.Person)
                ReadOnlyField(label = "Email", value = agent?.email, icon = Icons.Outlined.Email, keyboard = KeyboardType.Email)
                ReadOnlyField(label = "Phone", value = viewModel.phone, icon = Icons.Outlined.Phone, keyboard = KeyboardType.Phone)
                ReadOnlyField(label = "Role", value = viewModel.role, icon = Icons.Outlined.Badge)
                ReadOnlyField(label = "Extension", value = agent?.extension, icon = Icons.Outlined.Phone)
                ReadOnlyField(label = "SIP user", value = agent?.sipUsername, icon = Icons.Outlined.AlternateEmail)

                Text(
                    "These details are managed by your administrator. Contact support to update them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun ReadOnlyField(
    label: String,
    value: String?,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    keyboard: KeyboardType = KeyboardType.Text,
) {
    AppInput(
        value = value?.takeIf { it.isNotBlank() } ?: "—",
        onValueChange = {},
        label = label,
        leadingIcon = icon,
        keyboardType = keyboard,
        enabled = false,
    )
}
