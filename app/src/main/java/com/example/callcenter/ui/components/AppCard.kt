package com.example.callcenter.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    cornerRadius: Int = 16,
    padding: PaddingValues = PaddingValues(16.dp),
    elevation: Int = 1,
    bordered: Boolean = true,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(cornerRadius.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = elevation.dp,
        shadowElevation = elevation.dp,
        border = if (bordered) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null,
    ) {
        androidx.compose.foundation.layout.Box(modifier = Modifier.padding(padding)) {
            content()
        }
    }
}
