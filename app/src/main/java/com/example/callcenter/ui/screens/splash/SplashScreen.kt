package com.example.callcenter.ui.screens.splash

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.callcenter.BuildConfig
import com.example.callcenter.R
import com.example.callcenter.ui.theme.PlusJakarta

// ---- Palette (screen-local; splash uses fixed brand colors, not the theme) ----
private val G1 = Color(0xFF6E74FF)
private val G2 = Color(0xFF4B44D6)
private val G3 = Color(0xFF3A32BC)
private val G4 = Color(0xFF2C2596)
private val GlowLavender = Color(0x8C818CF8)
private val GlowIndigo = Color(0x805A50DC)
private val LogoBgTop = Color(0xFFEEF0FE)
private val LogoBgBottom = Color(0xFFDDE0FF)
private val SecurityAccent = Color(0xFF9BE7C6)

/**
 * Animated brand splash shown on cold start: indigo radial gradient with ambient
 * glows, an app-icon badge that floats and pulses, expanding rings, the BOL7
 * wordmark, tagline, an indeterminate shimmer bar, and a security footer.
 *
 * Keeps the [onFinished] callback (the nav graph routes auth-aware from here);
 * it fires after ~2s.
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(2000)
        onFinished()
    }

    val transition = rememberInfiniteTransition(label = "splash")

    // Two expanding pulse rings, the 2nd staggered by half the cycle.
    val ring1Scale by transition.animateFloat(
        initialValue = 0.85f, targetValue = 1.4f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart),
        label = "ring1Scale",
    )
    val ring1Alpha by transition.animateFloat(
        initialValue = 0.5f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart),
        label = "ring1Alpha",
    )
    val ring2Scale by transition.animateFloat(
        initialValue = 0.85f, targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            tween(3000, easing = LinearEasing), RepeatMode.Restart, StartOffset(1500),
        ),
        label = "ring2Scale",
    )
    val ring2Alpha by transition.animateFloat(
        initialValue = 0.5f, targetValue = 0f,
        animationSpec = infiniteRepeatable(
            tween(3000, easing = LinearEasing), RepeatMode.Restart, StartOffset(1500),
        ),
        label = "ring2Alpha",
    )

    // Badge glow pulse + gentle vertical float.
    val glowScale by transition.animateFloat(
        initialValue = 1f, targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(3000), RepeatMode.Reverse),
        label = "glowScale",
    )
    val glowAlpha by transition.animateFloat(
        initialValue = 0.6f, targetValue = 0.15f,
        animationSpec = infiniteRepeatable(tween(3000), RepeatMode.Reverse),
        label = "glowAlpha",
    )
    val floatY by transition.animateFloat(
        initialValue = 0f, targetValue = -6f,
        animationSpec = infiniteRepeatable(tween(4000), RepeatMode.Reverse),
        label = "floatY",
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // --- Radial gradient background, centered high (y ≈ 12% of height) ---
        var sizePx by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { sizePx = androidx.compose.ui.geometry.Size(it.width.toFloat(), it.height.toFloat()) }
                .background(
                    if (sizePx == androidx.compose.ui.geometry.Size.Zero) {
                        Brush.verticalGradient(listOf(G1, G2, G3, G4))
                    } else {
                        Brush.radialGradient(
                            colorStops = arrayOf(0.0f to G1, 0.44f to G2, 0.78f to G3, 1.0f to G4),
                            center = Offset(sizePx.width * 0.5f, sizePx.height * 0.12f),
                            radius = maxOf(sizePx.width, sizePx.height) * 0.95f,
                        )
                    },
                ),
        )

        // --- Ambient glow blobs (radial-gradient boxes; blur() is API 31+ only) ---
        GlowBlob(
            color = GlowLavender,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 40.dp)
                .size(320.dp),
        )
        GlowBlob(
            color = GlowIndigo,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .size(360.dp),
        )
        // Subtle dark vignette at the bottom.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(220.dp)
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color(0x33000000))),
                ),
        )

        // --- Center column ---
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Logo cluster (rings + glow + floating badge).
            Box(
                modifier = Modifier.size(168.dp),
                contentAlignment = Alignment.Center,
            ) {
                PulseRing(scale = ring1Scale, alpha = ring1Alpha)
                PulseRing(scale = ring2Scale, alpha = ring2Alpha)

                // Soft white glow behind the badge.
                Box(
                    modifier = Modifier
                        .size(128.dp)
                        .scale(glowScale)
                        .alpha(glowAlpha)
                        .clip(RoundedCornerShape(40.dp))
                        .background(
                            Brush.radialGradient(listOf(Color.White, Color.Transparent)),
                        ),
                )

                // Glass badge holding our app icon; floats gently.
                Box(
                    modifier = Modifier
                        .graphicsLayer { translationY = floatY }
                        .size(112.dp)
                        .clip(RoundedCornerShape(34.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xF2FFFFFF), Color(0xD1FFFFFF)),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(84.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Brush.verticalGradient(listOf(LogoBgTop, LogoBgBottom))),
                        contentAlignment = Alignment.Center,
                    ) {
                        // ic_launcher_foreground is a transparent-bg raster the
                        // painter can load (the adaptive-icon XML cannot).
                        Image(
                            painter = painterResource(R.mipmap.ic_launcher_foreground),
                            contentDescription = "BOL7",
                            modifier = Modifier.size(76.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            // Wordmark.
            Text(
                text = "BOL7",
                fontFamily = PlusJakarta,
                color = Color.White,
                fontSize = 46.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.14.em,
            )

            Spacer(Modifier.height(16.dp))

            // Short divider.
            Box(
                modifier = Modifier
                    .width(44.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.5f)),
            )

            Spacer(Modifier.height(16.dp))

            // Tagline.
            Text(
                text = "Smarter calls. Faster connections.",
                fontFamily = PlusJakarta,
                color = Color.White.copy(alpha = 0.82f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(40.dp))

            // Indeterminate shimmer loading bar.
            ShimmerBar(transition)
        }

        // --- Footer ---
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.12f))
                    .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.GppGood,
                    contentDescription = null,
                    tint = SecurityAccent,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Enterprise-grade & secure",
                    fontFamily = PlusJakarta,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.05.em,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Version ${BuildConfig.VERSION_NAME} · Powered by BOL7",
                fontFamily = PlusJakarta,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/** A thin white circular border that expands + fades (one pulse ring). */
@Composable
private fun PulseRing(scale: Float, alpha: Float) {
    Box(
        modifier = Modifier
            .size(120.dp)
            .scale(scale)
            .alpha(alpha)
            .border(1.dp, Color.White, RoundedCornerShape(999.dp)),
    )
}

/** A soft radial-gradient blob used as an ambient glow (blur-free, all APIs). */
@Composable
private fun GlowBlob(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(
            Brush.radialGradient(listOf(color, Color.Transparent)),
            shape = RoundedCornerShape(999.dp),
        ),
    )
}

/** 132×4 translucent track with a 42dp white highlight sweeping left→right. */
@Composable
private fun ShimmerBar(transition: androidx.compose.animation.core.InfiniteTransition) {
    val trackWidth = 132.dp
    val highlightWidth = 42.dp
    val density = androidx.compose.ui.platform.LocalDensity.current
    val trackPx = with(density) { trackWidth.toPx() }
    val highlightPx = with(density) { highlightWidth.toPx() }

    // 0f → 1f drives the highlight from fully off the left to fully off the right.
    val progress by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(1400, easing = FastOutSlowInEasing), RepeatMode.Restart,
        ),
        label = "shimmer",
    )

    Box(
        modifier = Modifier
            .width(trackWidth)
            .height(4.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Color.White.copy(alpha = 0.22f)),
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    // Travel from -highlightWidth to trackWidth (off both edges).
                    translationX = -highlightPx + progress * (trackPx + highlightPx)
                }
                .width(highlightWidth)
                .height(4.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, Color.White, Color.Transparent),
                    ),
                ),
        )
    }
}
