package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val ReelsCutDarkColorScheme = darkColorScheme(
    primary = NeonPurple,
    onPrimary = Color(0xFF0D0D12),
    primaryContainer = NeonPurpleContainer,
    onPrimaryContainer = NeonPurpleBright,
    secondary = ElectricCyan,
    onSecondary = Color(0xFF06161C),
    secondaryContainer = ElectricCyanContainer,
    onSecondaryContainer = ElectricCyan,
    tertiary = NeonGreen,
    onTertiary = Color(0xFF061A04),
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = ObsidianBg,
    surfaceContainerLow = SurfaceDark,
    surfaceContainer = SurfaceElevated,
    surfaceContainerHigh = SurfaceCard,
    outline = BorderSubtle,
    outlineVariant = Color(0xFF25253A),
    error = HotCoral,
    onError = Color.White
)

private val ReelsCutShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun ReelsCutTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ReelsCutDarkColorScheme,
        typography = Typography,
        shapes = ReelsCutShapes,
        content = content
    )
}
