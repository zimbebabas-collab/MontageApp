package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val ReelsCutDarkColorScheme = darkColorScheme(
    primary = SpeakerBlue,
    onPrimary = Color(0xFF061621),
    primaryContainer = SpeakerBlueContainer,
    onPrimaryContainer = SpeakerBlue,
    secondary = SpeakerPink,
    onSecondary = Color(0xFF26091A),
    secondaryContainer = SpeakerPinkContainer,
    onSecondaryContainer = SpeakerPink,
    tertiary = AccentGreen,
    onTertiary = Color(0xFF052010),
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
    outlineVariant = Color(0xFF232333),
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
