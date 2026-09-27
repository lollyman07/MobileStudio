package com.mobilestudio.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.mobilestudio.app.model.AppThemeMode

private val DarkColors = darkColorScheme(
    primary = StudioAccent,
    secondary = StudioAccentVariant,
    background = StudioBackground,
    surface = StudioSurface,
    surfaceVariant = StudioSurfaceVariant,
    onBackground = StudioTextPrimary,
    onSurface = StudioTextPrimary,
    error = StudioRec
)

private val LightColors = lightColorScheme(
    primary = StudioAccent,
    secondary = StudioAccentVariant,
    error = StudioRec
)

@Composable
fun MobileStudioTheme(themeMode: AppThemeMode = AppThemeMode.DARK, content: @Composable () -> Unit) {
    val useDark = when (themeMode) {
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (useDark) DarkColors else LightColors,
        typography = MaterialTheme.typography,
        content = content
    )
}
