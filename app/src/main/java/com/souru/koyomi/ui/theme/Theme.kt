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
import com.souru.koyomi.data.ThemePack

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

// ---------- 季節のテーマパック ----------
// Each pack keeps the こよみ recipe — washi-toned surface, ink-dark text,
// one restrained accent — and only shifts the hue toward its season.

/** 桜 — cherry-tinted paper with a deep 蘇芳 primary. */
internal val SakuraLightColors = lightColorScheme(
    primary = Color(0xFF95566A),
    onPrimary = Color(0xFFFBF2F1),
    primaryContainer = Color(0xFFF1DBDF),
    onPrimaryContainer = Color(0xFF3E212B),
    secondary = Color(0xFF786369),
    secondaryContainer = Color(0xFFF0E2DE),
    onSecondaryContainer = Color(0xFF362A29),
    tertiary = Color(0xFFA85D48),
    surface = Color(0xFFFAF3F1),
    onSurface = Color(0xFF292325),
    surfaceVariant = Color(0xFFF2E7E4),
    onSurfaceVariant = Color(0xFF605354),
    outline = Color(0xFF8F7F80),
    outlineVariant = Color(0xFFE5D6D3),
)

internal val SakuraDarkColors = darkColorScheme(
    primary = Color(0xFFE0B4C0),
    onPrimary = Color(0xFF3C232C),
    primaryContainer = Color(0xFF55353F),
    onPrimaryContainer = Color(0xFFF5DDE3),
    secondary = Color(0xFFCDB7B4),
    secondaryContainer = Color(0xFF453736),
    onSecondaryContainer = Color(0xFFEEDEDA),
    tertiary = Color(0xFFDB9481),
    surface = Color(0xFF1B1718),
    onSurface = Color(0xFFEBE0DE),
    surfaceVariant = Color(0xFF2E2728),
    onSurfaceVariant = Color(0xFFB5A8A6),
    outline = Color(0xFF857877),
    outlineVariant = Color(0xFF3D3536),
)

/** 若葉 — fresh leaf green on pale straw paper. */
internal val WakabaLightColors = lightColorScheme(
    primary = Color(0xFF4A6151),
    onPrimary = Color(0xFFF4F7F0),
    primaryContainer = Color(0xFFDCE6DA),
    onPrimaryContainer = Color(0xFF24322A),
    secondary = Color(0xFF66705C),
    secondaryContainer = Color(0xFFE6EBD9),
    onSecondaryContainer = Color(0xFF2B3122),
    tertiary = Color(0xFF8A6A34),
    surface = Color(0xFFF5F6EC),
    onSurface = Color(0xFF24261F),
    surfaceVariant = Color(0xFFE9ECDC),
    onSurfaceVariant = Color(0xFF55594B),
    outline = Color(0xFF7F8471),
    outlineVariant = Color(0xFFD9DEC8),
)

internal val WakabaDarkColors = darkColorScheme(
    primary = Color(0xFFAFC6B2),
    onPrimary = Color(0xFF253226),
    primaryContainer = Color(0xFF3A4A3E),
    onPrimaryContainer = Color(0xFFD8E6D9),
    secondary = Color(0xFFC0C9AC),
    secondaryContainer = Color(0xFF3A4030),
    onSecondaryContainer = Color(0xFFE2E8D0),
    tertiary = Color(0xFFD2B077),
    surface = Color(0xFF171916),
    onSurface = Color(0xFFE3E6DB),
    surfaceVariant = Color(0xFF282B24),
    onSurfaceVariant = Color(0xFFACB2A0),
    outline = Color(0xFF7B8171),
    outlineVariant = Color(0xFF363B31),
)

/** 藍 — indigo ink; 朱 stays as the accent, the classic pairing. */
internal val AiLightColors = lightColorScheme(
    primary = Color(0xFF3A4E75),
    onPrimary = Color(0xFFF2F5FA),
    primaryContainer = Color(0xFFDCE3F0),
    onPrimaryContainer = Color(0xFF1C2841),
    secondary = Color(0xFF5C6474),
    secondaryContainer = Color(0xFFE2E7F0),
    onSecondaryContainer = Color(0xFF262C38),
    tertiary = Color(0xFFA8503C),
    surface = Color(0xFFF4F5F8),
    onSurface = Color(0xFF222429),
    surfaceVariant = Color(0xFFE8EAF0),
    onSurfaceVariant = Color(0xFF52555E),
    outline = Color(0xFF7B7F8A),
    outlineVariant = Color(0xFFD8DCE5),
)

internal val AiDarkColors = darkColorScheme(
    primary = Color(0xFFA9BEE4),
    onPrimary = Color(0xFF1F2A40),
    primaryContainer = Color(0xFF35435F),
    onPrimaryContainer = Color(0xFFD9E2F5),
    secondary = Color(0xFFB9C1D2),
    secondaryContainer = Color(0xFF363D4B),
    onSecondaryContainer = Color(0xFFDFE4F0),
    tertiary = Color(0xFFD08A77),
    surface = Color(0xFF15171B),
    onSurface = Color(0xFFDFE2E9),
    surfaceVariant = Color(0xFF262931),
    onSurfaceVariant = Color(0xFFA6AAB6),
    outline = Color(0xFF767A86),
    outlineVariant = Color(0xFF343842),
)

/** 紅葉 — autumn amber and persimmon on warm paper. */
internal val MomijiLightColors = lightColorScheme(
    primary = Color(0xFF8C4F33),
    onPrimary = Color(0xFFFBF3EC),
    primaryContainer = Color(0xFFF1DFD1),
    onPrimaryContainer = Color(0xFF3B2214),
    secondary = Color(0xFF79624F),
    secondaryContainer = Color(0xFFF0E3D2),
    onSecondaryContainer = Color(0xFF342A1D),
    tertiary = Color(0xFF9C3E32),
    surface = Color(0xFFFAF3EA),
    onSurface = Color(0xFF29241E),
    surfaceVariant = Color(0xFFF1E7D8),
    onSurfaceVariant = Color(0xFF5F574B),
    outline = Color(0xFF8C8271),
    outlineVariant = Color(0xFFE4D8C4),
)

internal val MomijiDarkColors = darkColorScheme(
    primary = Color(0xFFE3B694),
    onPrimary = Color(0xFF3B2415),
    primaryContainer = Color(0xFF563926),
    onPrimaryContainer = Color(0xFFF6E0CD),
    secondary = Color(0xFFCFBBA2),
    secondaryContainer = Color(0xFF463A2B),
    onSecondaryContainer = Color(0xFFEEDFC9),
    tertiary = Color(0xFFDD8C74),
    surface = Color(0xFF1A1815),
    onSurface = Color(0xFFEBE1D5),
    surfaceVariant = Color(0xFF2D2922),
    onSurfaceVariant = Color(0xFFB4AB9C),
    outline = Color(0xFF837B6B),
    outlineVariant = Color(0xFF3C372E),
)

/** The pack's color scheme; used by the app theme and the widgets alike. */
fun koyomiColorScheme(pack: ThemePack, darkTheme: Boolean) = when (pack) {
    ThemePack.SUMI -> if (darkTheme) KoyomiDarkColors else KoyomiLightColors
    ThemePack.SAKURA -> if (darkTheme) SakuraDarkColors else SakuraLightColors
    ThemePack.WAKABA -> if (darkTheme) WakabaDarkColors else WakabaLightColors
    ThemePack.AI -> if (darkTheme) AiDarkColors else AiLightColors
    ThemePack.MOMIJI -> if (darkTheme) MomijiDarkColors else MomijiLightColors
}

@Composable
fun KoyomiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    themePack: ThemePack = ThemePack.SUMI,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> koyomiColorScheme(themePack, darkTheme)
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
