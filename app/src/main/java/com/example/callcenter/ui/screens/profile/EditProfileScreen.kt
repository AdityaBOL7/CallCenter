package com.example.callcenter.ui.screens.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.example.callcenter.data.repository.AgentRepository
import com.example.callcenter.data.repository.AuthRepository
import com.example.callcenter.ui.components.AppInput
import com.example.callcenter.ui.components.PageHeader
import com.example.callcenter.ui.components.ScreenContainer
import com.example.callcenter.ui.theme.AppColor
import com.example.callcenter.ui.theme.Brand500
import com.example.callcenter.ui.theme.Brand600
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class EditProfileViewModel @Inject constructor(
    private val agentRepo: AgentRepository,
    private val authRepo: AuthRepository,
) : ViewModel() {
    val agent = agentRepo.agent

    // Extra identity bits from the login response that aren't on the Agent model.
    val phone: String? get() = authRepo.cachedUser?.phoneNumber
    val role: String? get() = authRepo.cachedUser?.role

    private val _uploading = MutableStateFlow(false)
    val uploading: StateFlow<Boolean> = _uploading.asStateFlow()

    /** Upload the picked image as the agent's profile picture. */
    fun uploadAvatar(uri: Uri, onResult: (error: String?) -> Unit) {
        if (_uploading.value) return
        viewModelScope.launch {
            _uploading.value = true
            try {
                val result = agentRepo.uploadAvatar(uri)
                onResult(
                    if (result.isSuccess) null
                    else result.exceptionOrNull()?.message ?: "Upload failed. Try again.",
                )
            } finally {
                _uploading.value = false
            }
        }
    }
}

/**
 * Profile detail view. Identity fields are display-only (edits are admin-side),
 * but the agent can upload their own profile picture (POST /auth/me/avatar/).
 */
@Composable
fun EditProfileScreen(
    onBack: () -> Unit,
    viewModel: EditProfileViewModel = hiltViewModel(),
) {
    val agent by viewModel.agent.collectAsState()
    val uploading by viewModel.uploading.collectAsState()
    val context = LocalContext.current

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            viewModel.uploadAvatar(uri) { error ->
                android.widget.Toast.makeText(
                    context,
                    error ?: "Profile photo updated",
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    ScreenContainer {
        Column(Modifier.fillMaxSize()) {
            PageHeader(title = "Profile details", onBack = onBack)
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                AvatarPicker(
                    avatarUrl = agent?.avatarUrl,
                    name = agent?.name ?: "Agent",
                    uploading = uploading,
                    onPick = { pickImage.launch("image/*") },
                )

                ReadOnlyField(label = "Name", value = agent?.name, icon = Icons.Outlined.Person)
                ReadOnlyField(label = "Email", value = agent?.email, icon = Icons.Outlined.Email, keyboard = KeyboardType.Email)
                ReadOnlyField(label = "Phone", value = viewModel.phone, icon = Icons.Outlined.Phone, keyboard = KeyboardType.Phone)
                ReadOnlyField(label = "Role", value = viewModel.role, icon = Icons.Outlined.Badge)
                ReadOnlyField(label = "Extension", value = agent?.extension, icon = Icons.Outlined.Phone)
                ReadOnlyField(label = "SIP user", value = agent?.sipUsername, icon = Icons.Outlined.AlternateEmail)

                Text(
                    "These details are managed by your administrator — only the photo can be changed here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/**
 * Centered avatar (photo or initial fallback) with a camera badge. Tapping
 * either opens the system image picker; a spinner overlays while uploading.
 */
@Composable
private fun AvatarPicker(
    avatarUrl: String?,
    name: String,
    uploading: Boolean,
    onPick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .shadow(6.dp, CircleShape, ambientColor = Brand600.copy(alpha = 0.25f))
                    .clip(CircleShape)
                    .background(Brand500.copy(alpha = 0.14f))
                    .clickable(enabled = !uploading) { onPick() },
                contentAlignment = Alignment.Center,
            ) {
                if (!avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = "Profile photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        name.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                        color = Brand600,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 34.sp,
                    )
                }
                if (uploading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(26.dp),
                            color = Color.White,
                            strokeWidth = 2.5.dp,
                        )
                    }
                }
            }
            // Camera badge — the visual affordance that the photo is editable.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(30.dp)
                    .shadow(4.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Brand600)
                    .clickable(enabled = !uploading) { onPick() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.PhotoCamera,
                    contentDescription = "Change photo",
                    tint = Color.White,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (uploading) "Uploading…" else "Tap to change photo",
            color = AppColor.ink500,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
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
