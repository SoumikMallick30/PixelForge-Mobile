package dev.pixelforge.mobile.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Colors = lightColorScheme(
    primary = Color(0xFF5B4BDB), onPrimary = Color.White,
    background = Color(0xFFFAF9FF), surface = Color.White,
    onBackground = Color(0xFF191821), onSurface = Color(0xFF191821),
    surfaceVariant = Color(0xFFF0EEF8), onSurfaceVariant = Color(0xFF62606D)
)

@Composable fun PixelForgeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
