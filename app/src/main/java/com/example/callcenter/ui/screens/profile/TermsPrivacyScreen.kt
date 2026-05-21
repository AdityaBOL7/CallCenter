package com.example.callcenter.ui.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.callcenter.ui.components.PageHeader
import com.example.callcenter.ui.components.ScreenContainer
import com.example.callcenter.ui.components.SectionHeader

@Composable
fun TermsPrivacyScreen(onBack: () -> Unit) {
    ScreenContainer {
        Column(Modifier.fillMaxSize()) {
            PageHeader(title = "Terms & privacy", onBack = onBack)
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SectionHeader("Terms of service")
                Text(
                    """
                    By using CallCenter you agree to use it only for authorized outreach within your organization. Calls may be recorded according to your campaign settings. Do not contact numbers on do-not-call lists.
                    """.trimIndent(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SectionHeader("Privacy")
                Text(
                    """
                    We store agent identity, call metadata, and lead details to operate the service. Recordings, when enabled, are retained per your organization's policy. Contact your admin for export or deletion requests.
                    """.trimIndent(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
