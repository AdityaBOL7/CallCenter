package com.example.callcenter.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.callcenter.ui.theme.Brand600
import com.example.callcenter.ui.theme.Brand700
import com.example.callcenter.ui.theme.Danger
import com.example.callcenter.ui.theme.Ink100
import com.example.callcenter.ui.theme.Ink50
import com.example.callcenter.ui.theme.Ink800
import com.example.callcenter.ui.theme.Ink900
import com.example.callcenter.ui.theme.Success

enum class AppButtonVariant { Primary, Secondary, Danger, Success, Ghost }
enum class AppButtonSize { Sm, Md, Lg }

@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: AppButtonVariant = AppButtonVariant.Primary,
    size: AppButtonSize = AppButtonSize.Md,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    fullWidth: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.97f else 1f,
        label = "appButtonScale",
    )

    val (bg, fg) = when (variant) {
        AppButtonVariant.Primary -> (if (pressed) Brand700 else Brand600) to Color.White
        AppButtonVariant.Secondary -> (if (pressed) Ink900 else Ink800) to Color.White
        AppButtonVariant.Danger -> Danger to Color.White
        AppButtonVariant.Success -> Success to Color.White
        AppButtonVariant.Ghost -> (if (pressed) Ink100 else Ink50) to Ink900
    }

    val padding: PaddingValues = when (size) {
        AppButtonSize.Sm -> PaddingValues(horizontal = 12.dp, vertical = 8.dp)
        AppButtonSize.Md -> PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        AppButtonSize.Lg -> PaddingValues(horizontal = 20.dp, vertical = 16.dp)
    }
    val iconSize = if (size == AppButtonSize.Lg) 20.dp else 18.dp

    Row(
        modifier = modifier
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .alpha(if (enabled) 1f else 0.6f)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled && !loading,
                onClick = onClick,
            )
            .padding(padding),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(iconSize),
                color = fg,
                strokeWidth = 2.dp,
            )
        } else if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, tint = fg, modifier = Modifier.size(iconSize))
        }
        Text(
            text = text,
            color = fg,
            fontWeight = FontWeight.SemiBold,
            fontSize = if (size == AppButtonSize.Sm) 14.sp else 16.sp,
        )
    }
}
