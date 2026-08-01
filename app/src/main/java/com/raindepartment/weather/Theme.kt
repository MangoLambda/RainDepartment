package com.raindepartment.weather

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF168CE4),
    onPrimary = Color.White,
    background = Color(0xFFF0F7FD),
    surface = Color.White,
    onBackground = Color(0xFF12346D),
    onSurface = Color(0xFF12346D),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9ACBFF),
    onPrimary = Color(0xFF003258),
    background = Color(0xFF0D1C2B),
    surface = Color(0xFF172A3E),
    onBackground = Color(0xFFE4F0FF),
    onSurface = Color(0xFFE4F0FF),
)

@Composable
internal fun RainDepartmentTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
