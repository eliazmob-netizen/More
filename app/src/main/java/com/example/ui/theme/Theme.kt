package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SeaDarkColorScheme = darkColorScheme(
    primary = OceanBlue,
    onPrimary = Color.White,
    primaryContainer = OceanBluePressed,
    onPrimaryContainer = Color.White,
    secondary = SkyCyan,
    onSecondary = OceanAbyss,
    tertiary = SunAmber,
    onTertiary = OceanAbyss,
    background = OceanAbyss,
    onBackground = SeaFoamInk,
    surface = GlassSurfaceElevated,
    onSurface = SeaFoamInk,
    surfaceVariant = ChipBackground,
    onSurfaceVariant = MutedSeaInk,
    outline = GlassBorder,
    error = ErrorCoral,
)

@Composable
fun SeaAndSkyTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = SeaDarkColorScheme,
        typography = SeaTypography,
        content = content,
    )
}
