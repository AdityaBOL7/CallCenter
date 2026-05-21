package com.example.callcenter.ui.screens.auth

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun FloatingOrb(
    size: Dp,
    color: Color,
    delayMs: Long = 0L,
    travelDp: Dp = 18.dp,
    durationMs: Int = 4500,
    modifier: Modifier = Modifier,
) {
    val t = remember { Animatable(0f) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val travelPx = with(density) { travelDp.toPx() }

    LaunchedEffect(Unit) {
        delay(delayMs)
        while (true) {
            t.animateTo(1f, tween(durationMs, easing = EaseInOutSine))
            t.animateTo(0f, tween(durationMs, easing = EaseInOutSine))
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { translationY = t.value * travelPx }
            .clip(CircleShape)
            .background(color.copy(alpha = 0.55f)),
    )
}
