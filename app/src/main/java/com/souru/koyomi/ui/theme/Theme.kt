package com.souru.koyomi.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
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

/** Clean, slightly deep red/blue — the only hues the monochrome UI keeps. */
private val SundayLight = Color(0xFFC2453A)
private val SaturdayLight = Color(0xFF3C64A6)
private val SundayDark = Color(0xFFF08A80)
private val SaturdayDark = Color(0xFF8FB0E6)

val LocalCalendarColors = staticCompositionLocalOf {
    CalendarColors(sunday = SundayLight, saturday = SaturdayLight)
}

// ---------- Monochrome base ----------
// こよみ is white paper and black ink. Every pack shares the same neutral
// surfaces (pure white / near-black, no tint); a pack only picks the ink —
// black/white for 墨, one restrained hue for the seasonal ones — which then
// drives "today", selections, switches and buttons.

private val Ink = Color(0xFF111111)
private val Paper = Color(0xFFFFFFFF)
private val NightPaper = Color(0xFF0B0B0B)
private val NightInk = Color(0xFFF2F2F2)

/**
 * Builds a full scheme (every Material role, so no stock lavender leaks into
 * sheets, dialogs or menus) from an optional accent. null = pure 墨.
 */
private fun monoScheme(dark: Boolean, accent: Color? = null): ColorScheme = if (!dark) {
    val ink = accent ?: Ink
    lightColorScheme(
        primary = ink,
        onPrimary = Paper,
        primaryContainer = accent?.let { lerp(Paper, it, 0.14f) } ?: Color(0xFFEDEDED),
        onPrimaryContainer = Ink,
        inversePrimary = accent?.let { lerp(it, Paper, 0.45f) } ?: Color(0xFFDADADA),
        secondary = Color(0xFF3D3D3D),
        onSecondary = Paper,
        secondaryContainer = Color(0xFFF0F0F0),
        onSecondaryContainer = Ink,
        tertiary = ink,
        onTertiary = Paper,
        tertiaryContainer = accent?.let { lerp(Paper, it, 0.14f) } ?: Color(0xFFEDEDED),
        onTertiaryContainer = Ink,
        background = Paper,
        onBackground = Ink,
        surface = Paper,
        onSurface = Ink,
        surfaceVariant = Color(0xFFF3F3F3),
        onSurfaceVariant = Color(0xFF6B6B6B),
        surfaceTint = Paper,
        inverseSurface = Color(0xFF1C1C1C),
        inverseOnSurface = Color(0xFFF2F2F2),
        error = SundayLight,
        onError = Paper,
        errorContainer = Color(0xFFFBE9E7),
        onErrorContainer = Color(0xFF5C1A14),
        outline = Color(0xFF9E9E9E),
        outlineVariant = Color(0xFFE6E6E6),
        scrim = Color.Black,
        surfaceBright = Paper,
        surfaceDim = Color(0xFFE9E9E9),
        surfaceContainerLowest = Paper,
        surfaceContainerLow = Color(0xFFFAFAFA),
        surfaceContainer = Color(0xFFF6F6F6),
        surfaceContainerHigh = Color(0xFFF2F2F2),
        surfaceContainerHighest = Color(0xFFEDEDED),
    )
} else {
    val ink = accent ?: NightInk
    darkColorScheme(
        primary = ink,
        onPrimary = Color.Black,
        primaryContainer = accent?.let { lerp(NightPaper, it, 0.22f) } ?: Color(0xFF262626),
        onPrimaryContainer = NightInk,
        inversePrimary = accent?.let { lerp(it, Color.Black, 0.45f) } ?: Color(0xFF3A3A3A),
        secondary = Color(0xFFC7C7C7),
        onSecondary = Color.Black,
        secondaryContainer = Color(0xFF222222),
        onSecondaryContainer = NightInk,
        tertiary = ink,
        onTertiary = Color.Black,
        tertiaryContainer = accent?.let { lerp(NightPaper, it, 0.22f) } ?: Color(0xFF262626),
        onTertiaryContainer = NightInk,
        background = NightPaper,
        onBackground = NightInk,
        surface = NightPaper,
        onSurface = NightInk,
        surfaceVariant = Color(0xFF1C1C1C),
        onSurfaceVariant = Color(0xFFA3A3A3),
        surfaceTint = NightPaper,
        inverseSurface = Color(0xFFEDEDED),
        inverseOnSurface = Color(0xFF161616),
        error = SundayDark,
        onError = Color.Black,
        errorContainer = Color(0xFF4A1A15),
        onErrorContainer = Color(0xFFFFDAD5),
        outline = Color(0xFF707070),
        outlineVariant = Color(0xFF2B2B2B),
        scrim = Color.Black,
        surfaceBright = Color(0xFF2A2A2A),
        surfaceDim = NightPaper,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color(0xFF131313),
        surfaceContainer = Color(0xFF171717),
        surfaceContainerHigh = Color(0xFF1E1E1E),
        surfaceContainerHighest = Color(0xFF262626),
    )
}

/** 墨 — pure black on white (white on black at night). The default. */
internal val KoyomiLightColors = monoScheme(dark = false)
internal val KoyomiDarkColors = monoScheme(dark = true)

// ---------- 季節のテーマパック ----------
// Same white/black paper; only the ink takes on the season's hue.

/** 桜 — 蘇芳 ink. */
internal val SakuraLightColors = monoScheme(false, Color(0xFFA34D66))
internal val SakuraDarkColors = monoScheme(true, Color(0xFFE6A6B8))

/** 若葉 — leaf green ink. */
internal val WakabaLightColors = monoScheme(false, Color(0xFF3F6E4C))
internal val WakabaDarkColors = monoScheme(true, Color(0xFF9DCBA8))

/** 藍 — indigo ink. */
internal val AiLightColors = monoScheme(false, Color(0xFF2F4C86))
internal val AiDarkColors = monoScheme(true, Color(0xFFA3B9E8))

/** 紅葉 — persimmon ink. */
internal val MomijiLightColors = monoScheme(false, Color(0xFFA9532F))
internal val MomijiDarkColors = monoScheme(true, Color(0xFFEFA27F))

/** The pack's color scheme; used by the app theme and the widgets alike. */
fun koyomiColorScheme(
    pack: ThemePack,
    darkTheme: Boolean,
    customColor: Int = com.souru.koyomi.data.SettingsRepository.DEFAULT_CUSTOM_THEME_COLOR,
) = when (pack) {
    ThemePack.SUMI -> if (darkTheme) KoyomiDarkColors else KoyomiLightColors
    ThemePack.SAKURA -> if (darkTheme) SakuraDarkColors else SakuraLightColors
    ThemePack.WAKABA -> if (darkTheme) WakabaDarkColors else WakabaLightColors
    ThemePack.AI -> if (darkTheme) AiDarkColors else AiLightColors
    ThemePack.MOMIJI -> if (darkTheme) MomijiDarkColors else MomijiLightColors
    ThemePack.CUSTOM -> customColorScheme(customColor, darkTheme)
}

/** HSV helper: build an opaque color from the seed's hue with fixed s/v. */
private fun hsv(hueDegrees: Float, saturation: Float, value: Float): Color =
    Color(android.graphics.Color.HSVToColor(floatArrayOf(hueDegrees, saturation, value)))

/**
 * カスタム: the user's seed becomes the ink on the shared white/black paper.
 * Saturation is clamped so any pick stays legible against both.
 */
internal fun customColorScheme(seedArgb: Int, darkTheme: Boolean): ColorScheme {
    val hsvArray = FloatArray(3)
    android.graphics.Color.colorToHSV(seedArgb, hsvArray)
    val hue = hsvArray[0]
    val strength = hsvArray[1].coerceIn(0.15f, 0.85f)
    val accent = if (!darkTheme) {
        hsv(hue, 0.30f + 0.40f * strength, 0.40f + 0.12f * strength)
    } else {
        hsv(hue, 0.15f + 0.25f * strength, 0.90f)
    }
    return monoScheme(darkTheme, accent)
}

/** Sharper corners than stock Material — crisp, editorial. */
val KoyomiShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

@Composable
fun KoyomiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    themePack: ThemePack = ThemePack.SUMI,
    customColor: Int = com.souru.koyomi.data.SettingsRepository.DEFAULT_CUSTOM_THEME_COLOR,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> koyomiColorScheme(themePack, darkTheme, customColor)
    }
    val calendarColors = if (darkTheme) {
        CalendarColors(sunday = SundayDark, saturday = SaturdayDark)
    } else {
        CalendarColors(sunday = SundayLight, saturday = SaturdayLight)
    }
    androidx.compose.runtime.CompositionLocalProvider(
        LocalCalendarColors provides calendarColors,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = KoyomiTypography,
            shapes = KoyomiShapes,
            content = content,
        )
    }
}
