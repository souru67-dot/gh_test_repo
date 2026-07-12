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
    CalendarColors(sunday = Color(0xFFB4635A), saturday = Color(0xFF5C7490))
}

/**
 * こよみ brand palette — 墨 (ink), 和紙 (warm paper), 朱 (vermilion accent).
 * Dynamic Color is opt-in from settings; this palette IS the identity.
 */
internal val KoyomiLightColors = lightColorScheme(
    primary = Color(0xFF343B46),            // 藍墨 — ink with a blue undertone
    onPrimary = Color(0xFFF8F3EA),
    primaryContainer = Color(0xFFDFE3E8),
    onPrimaryContainer = Color(0xFF1D232B),
    secondary = Color(0xFF6D6152),
    secondaryContainer = Color(0xFFEDE4D3),  // 生成り
    onSecondaryContainer = Color(0xFF33291A),
    tertiary = Color(0xFFA8503C),            // 朱 — small accents only
    surface = Color(0xFFF8F3EA),             // 和紙
    onSurface = Color(0xFF26241F),
    surfaceVariant = Color(0xFFEFE8DA),
    onSurfaceVariant = Color(0xFF5D584D),
    outline = Color(0xFF8A8474),
    outlineVariant = Color(0xFFE2DAC8),
)

internal val KoyomiDarkColors = darkColorScheme(
    primary = Color(0xFFB6C2D0),
    onPrimary = Color(0xFF20272F),
    primaryContainer = Color(0xFF3A4450),
    onPrimaryContainer = Color(0xFFDBE2EA),
    secondary = Color(0xFFC9BCA5),
    secondaryContainer = Color(0xFF413A2E),
    onSecondaryContainer = Color(0xFFEAE0CC),
    tertiary = Color(0xFFD08A77),
    surface = Color(0xFF191817),             // 夜の墨
    onSurface = Color(0xFFEAE4D8),
    surfaceVariant = Color(0xFF2B2924),
    onSurfaceVariant = Color(0xFFB3AC9D),
    outline = Color(0xFF837D6E),
    outlineVariant = Color(0xFF3B3830),
)

@Composable
fun KoyomiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> KoyomiDarkColors
        else -> KoyomiLightColors
    }
    val calendarColors = if (darkTheme) {
        CalendarColors(sunday = Color(0xFFD08E85), saturday = Color(0xFF93A8C4))
    } else {
        CalendarColors(sunday = Color(0xFFB4635A), saturday = Color(0xFF5C7490))
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
