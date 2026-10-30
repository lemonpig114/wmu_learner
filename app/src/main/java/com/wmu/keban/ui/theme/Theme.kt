package com.wmu.keban.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1F6FB2),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E4FF),
    onPrimaryContainer = Color(0xFF001C38),
    secondary = Color(0xFF00696D),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF9CF1F5),
    onSecondaryContainer = Color(0xFF002022),
    tertiary = Color(0xFF7C5800),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDEA7),
    onTertiaryContainer = Color(0xFF261A00),
    background = Color(0xFFF8F9FC),
    onBackground = Color(0xFF191C20),
    surface = Color(0xFFF8F9FC),
    onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFDFE2EB),
    onSurfaceVariant = Color(0xFF43474E)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9ECAFF),
    onPrimary = Color(0xFF00325B),
    primaryContainer = Color(0xFF00497F),
    onPrimaryContainer = Color(0xFFD3E4FF),
    secondary = Color(0xFF4DD9DE),
    onSecondary = Color(0xFF003739),
    secondaryContainer = Color(0xFF004F53),
    onSecondaryContainer = Color(0xFF9CF1F5),
    tertiary = Color(0xFFFFB951),
    onTertiary = Color(0xFF422C00),
    tertiaryContainer = Color(0xFF5F4100),
    onTertiaryContainer = Color(0xFFFFDEA7),
    background = Color(0xFF101418),
    onBackground = Color(0xFFE0E2E8),
    surface = Color(0xFF101418),
    onSurface = Color(0xFFE0E2E8),
    surfaceVariant = Color(0xFF43474E),
    onSurfaceVariant = Color(0xFFC4C6D0)
)

@Composable
fun KeBanTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
