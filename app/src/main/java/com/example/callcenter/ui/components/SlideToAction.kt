package com.example.callcenter.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.callcenter.ui.theme.AppGradients
import com.example.callcenter.ui.theme.Emerald50
import com.example.callcenter.ui.theme.Emerald500
import com.example.callcenter.ui.theme.Emerald600
import com.example.callcenter.ui.theme.Emerald900
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun SlideToAction(
    label: String,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Rounded.ChevronRight,
    hintText: String = "SLIDE TO START",
    enabled: Boolean = true,
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val thumbDp = 60.dp
    val paddingDp = 5.dp
    val trackHeight = 70.dp

    val thumbPx = with(density) { thumbDp.toPx() }
    val paddingPx = with(density) { paddingDp.toPx() }

    var trackWidthPx by remember { mutableFloatStateOf(0f) }
    val offsetX = remember { Animatable(0f) }
    var dragging by remember { mutableStateOf(false) }
    var committed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val maxX by remember {
        derivedStateOf { (trackWidthPx - thumbPx - paddingPx * 2).coerceAtLeast(0f) }
    }
    val progress by remember {
        derivedStateOf { if (maxX == 0f) 0f else (offsetX.value / maxX).coerceIn(0f, 1f) }
    }

    val infinite = rememberInfiniteTransition(label = "slideShimmer")
    val shimmer by infinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing)),
        label = "shimmerOffset",
    )
    val chevPulse by infinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), repeatMode = RepeatMode.Reverse),
        label = "chevPulse",
    )

    val thumbScale by animateFloatAsState(
        targetValue = if (dragging) 1.05f else 1f,
        label = "thumbScale",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(trackHeight)
            .clip(RoundedCornerShape(16.dp))
            .onSizeChanged { trackWidthPx = it.width.toFloat() }
            .background(AppGradients.slideTrack()),
    ) {
        // Fill progress
        if (offsetX.value > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .width(with(density) { (offsetX.value + thumbPx + paddingPx * 2).toDp() })
                    .background(AppGradients.slideFill()),
            )
        }

        // Shimmer band (idle only)
        if (!dragging && !committed && trackWidthPx > 0f) {
            val shimmerOffset = (shimmer * (trackWidthPx + 200f) - 100f).roundToInt()
            Box(
                modifier = Modifier
                    .offset { IntOffset(shimmerOffset, 0) }
                    .rotate(-20f)
                    .fillMaxSize()
                    .width(80.dp)
                    .background(Color.White.copy(alpha = 0.12f)),
            )
        }

        // Label
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = hintText,
                color = Emerald900.copy(alpha = 0.5f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    color = Emerald900,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.size(8.dp))
                Box {
                    Icon(
                        Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = Emerald900.copy(alpha = 0.5f),
                        modifier = Modifier
                            .size(16.dp)
                            .offset(x = (-6).dp)
                            .scale(0.3f + 0.7f * chevPulse),
                    )
                    Icon(
                        Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = Emerald900,
                        modifier = Modifier
                            .size(16.dp)
                            .offset(x = (chevPulse * 6).dp),
                    )
                }
            }
        }

        // Thumb
        Box(
            modifier = Modifier
                .offset { IntOffset((offsetX.value + paddingPx).roundToInt(), paddingPx.roundToInt()) }
                .size(thumbDp)
                .scale(thumbScale)
                .clip(CircleShape)
                .background(Color.White)
                .pointerInput(enabled, maxX) {
                    if (!enabled || maxX <= 0f) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { dragging = true },
                        onDragEnd = {
                            dragging = false
                            scope.launch {
                                if (offsetX.value > maxX * 0.72f) {
                                    committed = true
                                    offsetX.animateTo(maxX, tween(180))
                                    onComplete()
                                    kotlinx.coroutines.delay(400)
                                    offsetX.animateTo(0f, tween(300))
                                    committed = false
                                } else {
                                    offsetX.animateTo(0f, androidx.compose.animation.core.spring(
                                        dampingRatio = 0.6f, stiffness = 380f,
                                    ))
                                }
                            }
                        },
                        onDragCancel = {
                            dragging = false
                            scope.launch {
                                offsetX.animateTo(0f, androidx.compose.animation.core.spring(
                                    dampingRatio = 0.6f, stiffness = 380f,
                                ))
                            }
                        },
                        onHorizontalDrag = { _, delta ->
                            scope.launch {
                                offsetX.snapTo((offsetX.value + delta).coerceIn(0f, maxX))
                            }
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (committed) Emerald500 else Emerald50),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (committed) Icons.Rounded.Check else icon,
                    contentDescription = null,
                    tint = if (committed) Color.White else Emerald600,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }

    LaunchedEffect(enabled) {
        if (!enabled) {
            offsetX.snapTo(0f)
            committed = false
        }
    }
}
