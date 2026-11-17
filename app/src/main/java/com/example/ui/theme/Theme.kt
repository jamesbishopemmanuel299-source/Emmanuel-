package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark")
}

private val LightColorScheme = lightColorScheme(
    primary = EmeraldPrimary,
    onPrimary = CrispWhite,
    primaryContainer = EmeraldLightContainer,
    onPrimaryContainer = EmeraldOnContainer,
    secondary = NavyDeep,
    onSecondary = CrispWhite,
    secondaryContainer = Color(0xFFDCE4F0),
    onSecondaryContainer = NavyDeep,
    tertiary = GoldAccent,
    onTertiary = CrispWhite,
    tertiaryContainer = GoldContainer,
    onTertiaryContainer = Color(0xFF451A03),
    background = MintBackground,
    onBackground = NavyDeep,
    surface = CrispWhite,
    onSurface = NavyDeep,
    surfaceVariant = SoftMintSurface,
    onSurfaceVariant = Color(0xFF3F5147),
    outline = OutlineMint,
    error = CrimsonError,
    onError = CrispWhite,
    errorContainer = CrimsonContainer,
    onErrorContainer = Color(0xFF7F1D1D)
)

private val DarkColorScheme = darkColorScheme(
    primary = EmeraldVibrant,
    onPrimary = EmeraldOnContainer,
    primaryContainer = Color(0xFF064E3B),
    onPrimaryContainer = EmeraldLightContainer,
    secondary = Color(0xFF38BDF8),
    onSecondary = NavyDeep,
    secondaryContainer = NavyElevated,
    onSecondaryContainer = Color(0xFFE2E8F0),
    tertiary = Color(0xFFFBBF24),
    onTertiary = Color(0xFF451A03),
    tertiaryContainer = Color(0xFF78350F),
    onTertiaryContainer = GoldContainer,
    background = NavyDeep,
    onBackground = Color(0xFFF1F5F9),
    surface = NavySurface,
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = NavyElevated,
    onSurfaceVariant = NavyMutedText,
    outline = Color(0xFF2B4162),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = CrimsonContainer
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun GLACBTTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    MaterialTheme(
        colorScheme = if (isDark) DarkColorScheme else LightColorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
