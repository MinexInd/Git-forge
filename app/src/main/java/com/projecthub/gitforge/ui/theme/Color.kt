package com.projecthub.gitforge.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Blue40 = Color(0xFF2563EB)
private val Blue80 = Color(0xFF93C5FD)
private val Teal40 = Color(0xFF0F766E)
private val Teal80 = Color(0xFF5EEAD4)

private val LightScheme = lightColorScheme(
    primary = Blue40,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF0B1120),
    secondary = Teal40,
    onSecondary = Color.White,
    tertiary = Color(0xFF7C3AED),
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFF94A3B8),
    error = Color(0xFFB3261E),
)

private val DarkScheme = darkColorScheme(
    primary = Blue80,
    onPrimary = Color(0xFF0B1120),
    primaryContainer = Color(0xFF1E3A8A),
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondary = Teal80,
    onSecondary = Color(0xFF04201C),
    tertiary = Color(0xFFC4B5FD),
    background = Color(0xFF0B1120),
    onBackground = Color(0xFFE2E8F0),
    surface = Color(0xFF111827),
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFF1F2937),
    onSurfaceVariant = Color(0xFFCBD5E1),
    outline = Color(0xFF64748B),
    error = Color(0xFFF2B8B5),
)

/** Terminal palette. Kept separate because a terminal needs fixed ANSI colours. */
data class TerminalColors(
    val background: Color,
    val foreground: Color,
    val prompt: Color,
    val error: Color,
    val selection: Color,
)

val DarkTerminal = TerminalColors(
    background = Color(0xFF0A0F1A),
    foreground = Color(0xFFD1E0F5),
    prompt = Color(0xFF5EEAD4),
    error = Color(0xFFFCA5A5),
    selection = Color(0xFF1E3A8A),
)

val LightTerminal = TerminalColors(
    background = Color(0xFF0F172A),
    foreground = Color(0xFFE2E8F0),
    prompt = Color(0xFF5EEAD4),
    error = Color(0xFFFCA5A5),
    selection = Color(0xFF1E3A8A),
)

@Composable
fun GitForgeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    useDynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        darkTheme -> DarkScheme
        else -> LightScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = GitForgeTypography,
        shapes = GitForgeShapes,
        content = content,
    )
}
