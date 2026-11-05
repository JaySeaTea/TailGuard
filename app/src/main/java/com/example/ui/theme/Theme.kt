package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val TailGuardDarkScheme = darkColorScheme(
    primary = MeshCyan,
    onPrimary = Color(0xFF04131F),
    primaryContainer = MeshCyanContainer,
    onPrimaryContainer = MeshCyanBright,
    secondary = SignalEmerald,
    onSecondary = Color(0xFF022017),
    secondaryContainer = SignalEmeraldContainer,
    onSecondaryContainer = SignalEmeraldLight,
    tertiary = LockdownAmber,
    onTertiary = Color(0xFF241300),
    tertiaryContainer = LockdownAmberContainer,
    onTertiaryContainer = Color(0xFFFDE68A),
    error = AlertCrimson,
    onError = Color.White,
    errorContainer = AlertCrimsonContainer,
    onErrorContainer = Color(0xFFFECACA),
    background = ObsidianBg,
    onBackground = SlateTextPrimary,
    surface = ObsidianSurface,
    onSurface = SlateTextPrimary,
    surfaceVariant = ObsidianCard,
    onSurfaceVariant = SlateTextSecondary,
    surfaceContainerHigh = ObsidianCardElevated,
    outline = ObsidianOutline,
    outlineVariant = Color(0xFF1E293B)
)

private val TailGuardLightScheme = lightColorScheme(
    primary = DaylightPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF075985),
    secondary = Color(0xFF059669),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD1FAE5),
    onSecondaryContainer = Color(0xFF065F46),
    tertiary = Color(0xFFD97706),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF92400E),
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF991B1B),
    background = DaylightBg,
    onBackground = DaylightOnSurface,
    surface = DaylightSurface,
    onSurface = DaylightOnSurface,
    surfaceVariant = DaylightCard,
    onSurfaceVariant = Color(0xFF475569),
    surfaceContainerHigh = Color(0xFFE2E8F0),
    outline = DaylightOutline,
    outlineVariant = Color(0xFFE2E8F0)
)

val TailGuardShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) TailGuardDarkScheme else TailGuardLightScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = TailGuardShapes,
        content = content
    )
}
