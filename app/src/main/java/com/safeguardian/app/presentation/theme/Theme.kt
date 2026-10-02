package com.safeguardian.app.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = GuardianBlue,
    onPrimary = GuardianDark,
    primaryContainer = GuardianBlueDim,
    secondary = GuardianGreen,
    onSecondary = GuardianDark,
    secondaryContainer = GuardianGreenDim,
    tertiary = GuardianPurple,
    error = GuardianRed,
    errorContainer = GuardianRedDim,
    background = GuardianDark,
    onBackground = GuardianTextPrimary,
    surface = GuardianSurface,
    onSurface = GuardianTextPrimary,
    surfaceVariant = GuardianSurfaceVariant,
    onSurfaceVariant = GuardianTextSecondary,
    outline = GuardianBorder,
    outlineVariant = GuardianBorder
)

@Composable
fun SafeGuardianTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = SafeGuardianTypography,
        content = content
    )
}
