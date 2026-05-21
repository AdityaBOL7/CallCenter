package com.example.callcenter.ui.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.callcenter.data.repository.AgentRepository
import com.example.callcenter.ui.components.AppButton
import com.example.callcenter.ui.components.AppButtonSize
import com.example.callcenter.ui.components.AppInput
import com.example.callcenter.ui.components.PageHeader
import com.example.callcenter.ui.components.ScreenContainer
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@HiltViewModel
class EditProfileViewModel @Inject constructor(
    private val agentRepo: AgentRepository,
) : ViewModel() {
    val agent = agentRepo.agent
    fun save(name: String, email: String, onDone: () -> Unit) {
        viewModelScope.launch {
            agentRepo.updateProfile(name, email)
            onDone()
        }
    }
}

@Composable
fun EditProfileScreen(
    onBack: () -> Unit,
    viewModel: EditProfileViewModel = hiltViewModel(),
) {
    val agent by viewModel.agent.collectAsState()
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    LaunchedEffect(agent) {
        name = agent.name
        email = agent.email
    }
    ScreenContainer {
        Column(Modifier.fillMaxSize()) {
            PageHeader(title = "Edit profile", onBack = onBack)
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                AppInput(value = name, onValueChange = { name = it }, label = "Name", leadingIcon = Icons.Outlined.Person)
                AppInput(value = email, onValueChange = { email = it }, label = "Email",
                    leadingIcon = Icons.Outlined.Email, keyboardType = KeyboardType.Email)
                AppButton(
                    text = "Save changes",
                    onClick = { viewModel.save(name, email, onBack) },
                    fullWidth = true, size = AppButtonSize.Lg,
                )
            }
        }
    }
}
