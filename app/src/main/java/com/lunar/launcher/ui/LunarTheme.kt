package com.lunar.launcher.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object LunarColors {
    val background = Color(0xFF050313)
    val surface = Color(0xFF0D0A24)
    val surface2 = Color(0xFF151035)
    val purple = Color(0xFF9A5CFF)
    val purpleBright = Color(0xFFB87CFF)
}

private val DarkColors = darkColorScheme(
    primary = LunarColors.purple,
    secondary = LunarColors.purpleBright,
    background = LunarColors.background,
    surface = LunarColors.surface,
    onPrimary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White
)

@Composable
fun LunarTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}
