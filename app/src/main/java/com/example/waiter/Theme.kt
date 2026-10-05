package com.example.waiter

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F766E), onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEEE9), onPrimaryContainer = Color(0xFF00201C),
    secondary = Color(0xFFB45309), onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE6CC), onSecondaryContainer = Color(0xFF331800),
    background = Color(0xFFF6F4F0), onBackground = Color(0xFF1C1B19),
    surface = Color.White, onSurface = Color(0xFF1C1B19),
    surfaceVariant = Color(0xFFEAE6E0), onSurfaceVariant = Color(0xFF5A554E),
    outline = Color(0xFF8A847B), error = Color(0xFFB3261E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4FD1C5), onPrimary = Color(0xFF00201C),
    primaryContainer = Color(0xFF0B4F49), onPrimaryContainer = Color(0xFFCDEEE9),
    secondary = Color(0xFFFFB366), onSecondary = Color(0xFF331800),
    secondaryContainer = Color(0xFF5A3200), onSecondaryContainer = Color(0xFFFFE6CC),
    background = Color(0xFF141311), onBackground = Color(0xFFE8E4DE),
    surface = Color(0xFF1E1D1A), onSurface = Color(0xFFE8E4DE),
    surfaceVariant = Color(0xFF2E2C28), onSurfaceVariant = Color(0xFFCFC9C0),
    outline = Color(0xFF8A847B), error = Color(0xFFF2B8B5),
)

@Composable
fun WaiterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        shapes = Shapes(
            small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(24.dp),
        ),
        content = content,
    )
}
