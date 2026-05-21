package com.example.callcenter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Icon
import com.example.callcenter.ui.theme.AccentMint
import com.example.callcenter.ui.theme.AccentRose
import com.example.callcenter.ui.theme.AccentSky
import com.example.callcenter.ui.theme.AccentViolet
import com.example.callcenter.ui.theme.Brand500
import com.example.callcenter.ui.theme.Brand600
import com.example.callcenter.ui.theme.Danger
import com.example.callcenter.ui.theme.Ink500
import com.example.callcenter.ui.theme.Ink900
import com.example.callcenter.ui.theme.Success
import com.example.callcenter.ui.theme.Warn

enum class StatTone { Brand, Danger, Warn, Violet, Success, Sky, Rose }

private fun StatTone.color(): Color = when (this) {
    StatTone.Brand -> Brand600
    StatTone.Danger -> Danger
    StatTone.Warn -> Warn
    StatTone.Violet -> AccentViolet
    StatTone.Success -> Success
    StatTone.Sky -> AccentSky
    StatTone.Rose -> AccentRose
}

@Composable
fun StatCard(
    label: String,
    value: String,
    icon: ImageVector,
    tone: StatTone,
    modifier: Modifier = Modifier,
) {
    val tint = tone.color()
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Ink900)
            Spacer(Modifier.height(2.dp))
            Text(label, fontSize = 12.sp, color = Ink500, fontWeight = FontWeight.SemiBold)
        }
    }
}
