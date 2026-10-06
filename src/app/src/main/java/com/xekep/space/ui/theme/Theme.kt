package com.xekep.space.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SpaceColors = darkColorScheme(
    primary = Color(0xFF8BD3FF),
    secondary = Color(0xFFFFC857),
    tertiary = Color(0xFFFF8A5B),
    background = Color(0xFF02040B),
    surface = Color(0xFF0B1324),
    primaryContainer = Color(0xFF16384D),
    onPrimaryContainer = Color(0xFFA8DFFF),
    secondaryContainer = Color(0xFF41351C),
    onSecondaryContainer = Color(0xFFFFDA86),
    surfaceVariant = Color(0xFF17253B),
    onSurfaceVariant = Color(0xFF9CAFC7),
    outline = Color(0xFF53637A),
    outlineVariant = Color(0xFF27374D),
    onPrimary = Color(0xFF041018),
    onSecondary = Color(0xFF291B00),
    onTertiary = Color(0xFF2A0E00),
    onBackground = Color(0xFFE9F2FF),
    onSurface = Color(0xFFE9F2FF),
)

@Composable
fun SpaceTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SpaceColors,
        content = content,
    )
}
