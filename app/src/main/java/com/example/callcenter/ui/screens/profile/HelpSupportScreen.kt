package com.example.callcenter.ui.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.callcenter.ui.components.AppCard
import com.example.callcenter.ui.components.PageHeader
import com.example.callcenter.ui.components.ScreenContainer
import com.example.callcenter.ui.components.SectionHeader

@Composable
fun HelpSupportScreen(onBack: () -> Unit) {
    ScreenContainer {
        Column(Modifier.fillMaxSize()) {
            PageHeader(title = "Help & support", onBack = onBack)
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SectionHeader("Common questions")
                FaqCard("How do I dial a lead?",
                    "Pick a lead from the list, tap Call, and choose the route. We start the call once you confirm.")
                FaqCard("How are callbacks scheduled?",
                    "From a call's outcome screen, pick Follow-up and choose the date and time.")
                FaqCard("Why are some calls flagged Recording?",
                    "Recording follows your campaign policy. You can toggle it during an active call if your role permits.")
                Spacer(Modifier.size(8.dp))
                SectionHeader("Contact us")
                AppCard {
                    Text("support@bol7.com", fontWeight = FontWeight.SemiBold)
                    Text("Mon–Fri, 9am–7pm IST", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun FaqCard(q: String, a: String) {
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(q, fontWeight = FontWeight.Bold)
            Text(a, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
    }
}
