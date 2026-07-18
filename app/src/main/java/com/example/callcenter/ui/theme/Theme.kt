package com.example.callcenter.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Brand600,
    onPrimary = Color.White,
    primaryContainer = Brand50,
    onPrimaryContainer = BrandOnSoft,
    secondary = AccentMint,
    onSecondary = Color.White,
    secondaryContainer = Emerald50,
    onSecondaryContainer = Emerald900,
    tertiary = AccentViolet,
    onTertiary = Color.White,
    background = AppBg,
    onBackground = Ink900,
    surface = SurfaceLight,
    onSurface = Ink900,
    surfaceVariant = Ink100,
    onSurfaceVariant = Ink600,
    outline = Ink200,
    outlineVariant = Ink100,
    error = Danger,
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Brand400,
    onPrimary = Ink900,
    primaryContainer = Brand800,
    onPrimaryContainer = Brand100,
    secondary = AccentMint,
    onSecondary = Ink900,
    secondaryContainer = Emerald900,
    onSecondaryContainer = Emerald100,
    tertiary = AccentViolet,
    onTertiary = Color.White,
    background = Ink900,
    onBackground = Color.White,
    surface = Ink800,
    onSurface = Color.White,
    surfaceVariant = Ink700,
    onSurfaceVariant = Ink300,
    outline = Ink700,
    outlineVariant = Ink800,
    error = Danger,
    onError = Color.White,
)

@Composable
fun CallCenterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalAppColors provides appColorsFor(darkTheme)) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

/**
 * Resolve the persisted theme preference ("system" | "light" | "dark") to a
 * dark/light boolean, falling back to the system setting for "system".
 */
@Composable
fun resolveDarkTheme(themeOverride: String): Boolean = when (themeOverride) {
    "dark" -> true
    "light" -> false
    else -> isSystemInDarkTheme()
}
