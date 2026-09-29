package com.example.tiebasearch.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF2B6CB0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E4FF),
    onPrimaryContainer = Color(0xFF0B3A6F),
    secondaryContainer = Color(0xFFE8E8E8),
    errorContainer = Color(0xFFFFDAD6)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9CC5FF),
    onPrimary = Color(0xFF0B3A6F),
    primaryContainer = Color(0xFF1B4B7F),
    onPrimaryContainer = Color(0xFFD6E4FF),
    secondaryContainer = Color(0xFF33373B),
    errorContainer = Color(0xFF6B2A25)
)

@Composable
fun TiebaSearchTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
