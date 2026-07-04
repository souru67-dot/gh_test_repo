package com.souru.koyomi.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Calendar-specific colors that must stay legible regardless of the
 * dynamic Material You palette: Sunday/holiday red and Saturday blue.
 */
data class CalendarColors(
    val sunday: Color,
    val saturday: Color,
)

val LocalCalendarColors = staticCompositionLocalOf {
    CalendarColors(sunday = Color(0xFFC4574E), saturday = Color(0xFF4A6FA5))
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF3D4A3D),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDDE5DB),
    onPrimaryContainer = Color(0xFF1A211A),
    secondaryContainer = Color(0xFFE8E4DC),
    onSecondaryContainer = Color(0xFF2E2B25),
    surface = Color(0xFFFBF9F6),
    onSurface = Color(0xFF1C1B18),
    surfaceVariant = Color(0xFFEFECE6),
    onSurfaceVariant = Color(0xFF57544D),
    outlineVariant = Color(0xFFE0DDD6),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB6C4B3),
    onPrimary = Color(0xFF232B22),
    primaryContainer = Color(0xFF394639),
    onPrimaryContainer = Color(0xFFD6E0D3),
    secondaryContainer = Color(0xFF3A3731),
    onSecondaryContainer = Color(0xFFE3DFD6),
    surface = Color(0xFF15140F),
    onSurface = Color(0xFFE5E2DC),
    surfaceVariant = Color(0xFF2A2823),
    onSurfaceVariant = Color(0xFFB0ACA3),
    outlineVariant = Color(0xFF39362F),
)

@Composable
fun KoyomiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    val calendarColors = if (darkTheme) {
        CalendarColors(sunday = Color(0xFFE2867E), saturday = Color(0xFF8FAEDC))
    } else {
        CalendarColors(sunday = Color(0xFFC4574E), saturday = Color(0xFF4A6FA5))
    }
    androidx.compose.runtime.CompositionLocalProvider(
        LocalCalendarColors provides calendarColors,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = KoyomiTypography,
            content = content,
        )
    }
}
