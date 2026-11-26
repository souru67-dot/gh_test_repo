package com.souru.koyomi.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.souru.koyomi.KoyomiApplication
import com.souru.koyomi.R
import com.souru.koyomi.data.ThemeMode
import com.souru.koyomi.ui.theme.KoyomiTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Per-widget configuration (theme / background opacity), shared by every
 * widget type. Declared with configuration_optional so adding a widget is
 * instant; reachable later via the launcher's reconfigure affordance.
 */
class WidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Cancelling from a config activity must not add the widget.
        setResult(
            RESULT_CANCELED,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
        )
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val settings = (application as KoyomiApplication).container.settingsRepository

        setContent {
            KoyomiTheme {
                var loaded by remember { mutableStateOf(false) }
                var theme by remember { mutableStateOf(ThemeMode.SYSTEM) }
                var opacity by remember { mutableFloatStateOf(1f) }
                var pack by remember { mutableStateOf(com.souru.koyomi.data.ThemePack.SUMI) }
                var isDot by remember { mutableStateOf(false) }
                var dotColor by remember { mutableStateOf(DotColor.ORANGE) }
                var customColor by remember {
                    androidx.compose.runtime.mutableIntStateOf(
                        com.souru.koyomi.data.SettingsRepository.DEFAULT_CUSTOM_THEME_COLOR,
                    )
                }

                androidx.compose.runtime.LaunchedEffect(Unit) {
                    // The dot-matrix widget is designed dark-first.
                    isDot = runCatching {
                        AppWidgetManager.getInstance(this@WidgetConfigActivity)
                            .getAppWidgetInfo(appWidgetId)?.provider?.className
                    }.getOrNull()?.endsWith("DotWidgetReceiver") == true
                    theme = settings.widgetThemeOverride(appWidgetId).first()
                        ?: if (isDot) ThemeMode.DARK else ThemeMode.SYSTEM
                    opacity = (
                        settings.widgetOpacity(appWidgetId).first()
                            ?: settings.widgetOpacityPercent.first()
                        ) / 100f
                    pack = settings.themePack.first()
                    customColor = settings.customThemeColor.first()
                    dotColor = DotColor.fromName(settings.widgetDotColor(appWidgetId).first())
                    loaded = true
                }
                val systemDark = androidx.compose.foundation.isSystemInDarkTheme()

                Surface(modifier = Modifier.fillMaxSize()) {
                    if (!loaded) return@Surface
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.widget_config_title),
                            style = MaterialTheme.typography.headlineSmall,
                        )

                        // Live preview over a light and a dark "wallpaper", so
                        // the right text tone for a see-through widget is obvious.
                        val previewDark = when (theme) {
                            ThemeMode.LIGHT -> false
                            ThemeMode.DARK -> true
                            ThemeMode.SYSTEM -> systemDark
                        }
                        val baseScheme = com.souru.koyomi.ui.theme.koyomiColorScheme(
                            pack,
                            previewDark,
                            customColor,
                        )
                        // The dot widget's accent is its LED color.
                        val previewScheme = if (isDot) {
                            baseScheme.copy(
                                primary = androidx.compose.ui.graphics.Color(
                                    if (previewDark) dotColor.dark else dotColor.light,
                                ),
                            )
                        } else {
                            baseScheme
                        }
                        Row(
                            modifier = Modifier.padding(top = 20.dp),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement
                                .spacedBy(12.dp),
                        ) {
                            for (wallpaperDark in listOf(false, true)) {
                                WidgetLookPreview(
                                    scheme = previewScheme,
                                    opacity = opacity,
                                    wallpaperDark = wallpaperDark,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        Text(
                            text = stringResource(R.string.widget_config_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )

                        Text(
                            text = stringResource(R.string.settings_theme),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 24.dp, bottom = 4.dp),
                        )
                        for ((mode, labelRes) in listOf(
                            ThemeMode.SYSTEM to R.string.theme_system,
                            ThemeMode.LIGHT to R.string.theme_light,
                            ThemeMode.DARK to R.string.theme_dark,
                        )) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { theme = mode }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = theme == mode,
                                    onClick = { theme = mode },
                                )
                                Text(
                                    text = stringResource(labelRes),
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(start = 4.dp),
                                )
                            }
                        }

                        Text(
                            text = stringResource(R.string.settings_widget_opacity),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 24.dp, bottom = 4.dp),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Slider(
                                value = opacity,
                                onValueChange = { opacity = it },
                                valueRange = 0f..1f,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "${(opacity * 100).toInt()}%",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }

                        if (isDot && !DotClockTicker.canTick(this@WidgetConfigActivity)) {
                            // Exact alarms are off (default on Android 14+): the
                            // round-dot clock needs them to redraw each minute.
                            androidx.compose.material3.Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 20.dp),
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = stringResource(R.string.widget_dot_exact_alarm),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    androidx.compose.material3.TextButton(
                                        onClick = {
                                            runCatching {
                                                startActivity(
                                                    Intent(
                                                        android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                                        android.net.Uri.parse("package:$packageName"),
                                                    ),
                                                )
                                            }
                                        },
                                    ) { Text(stringResource(R.string.widget_dot_exact_alarm_allow)) }
                                }
                            }
                        }

                        if (isDot && isAggressiveBatteryOem()) {
                            // HyperOS / MIUI stop background apps at lock unless
                            // auto-start is on and battery saving is lifted.
                            androidx.compose.material3.Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 20.dp),
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = stringResource(R.string.widget_dot_battery_oem),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Row {
                                        androidx.compose.material3.TextButton(
                                            onClick = { openAutoStartSettings() },
                                        ) { Text(stringResource(R.string.widget_dot_autostart)) }
                                        androidx.compose.material3.TextButton(
                                            onClick = { openAppDetails() },
                                        ) { Text(stringResource(R.string.widget_dot_app_info)) }
                                    }
                                }
                            }
                        }

                        if (isDot) {
                            Text(
                                text = stringResource(R.string.widget_dot_color),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
                            )
                            Row(
                                horizontalArrangement = androidx.compose.foundation.layout.Arrangement
                                    .spacedBy(14.dp),
                            ) {
                                for (option in DotColor.entries) {
                                    val selected = option == dotColor
                                    androidx.compose.foundation.layout.Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(
                                                androidx.compose.ui.graphics.Color(
                                                    if (previewDark) option.dark else option.light,
                                                ),
                                                androidx.compose.foundation.shape.CircleShape,
                                            )
                                            .then(
                                                if (selected) {
                                                    Modifier.border(
                                                        3.dp,
                                                        MaterialTheme.colorScheme.onSurface,
                                                        androidx.compose.foundation.shape.CircleShape,
                                                    )
                                                } else {
                                                    Modifier.border(
                                                        1.dp,
                                                        MaterialTheme.colorScheme.outlineVariant,
                                                        androidx.compose.foundation.shape.CircleShape,
                                                    )
                                                },
                                            )
                                            .clickable { dotColor = option },
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = {
                                save(
                                    appWidgetId,
                                    theme,
                                    (opacity * 100).toInt(),
                                    dotColor.takeIf { isDot },
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 32.dp)
                                .height(52.dp),
                        ) {
                            Text(stringResource(R.string.save))
                        }
                    }
                }
            }
        }
    }

    /** A tiny mock widget on a sample wallpaper, drawn with the real colors. */
    @Composable
    private fun WidgetLookPreview(
        scheme: androidx.compose.material3.ColorScheme,
        opacity: Float,
        wallpaperDark: Boolean,
        modifier: Modifier = Modifier,
    ) {
        val wallpaper = if (wallpaperDark) {
            androidx.compose.ui.graphics.Brush.linearGradient(
                listOf(
                    androidx.compose.ui.graphics.Color(0xFF1B2330),
                    androidx.compose.ui.graphics.Color(0xFF3A2F45),
                ),
            )
        } else {
            androidx.compose.ui.graphics.Brush.linearGradient(
                listOf(
                    androidx.compose.ui.graphics.Color(0xFFE9E4DA),
                    androidx.compose.ui.graphics.Color(0xFFBFD0DA),
                ),
            )
        }
        androidx.compose.foundation.layout.Box(
            modifier = modifier
                .height(116.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                .background(wallpaper)
                .padding(12.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        scheme.surface.copy(alpha = opacity),
                        androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                    )
                    .padding(12.dp),
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = java.time.LocalDate.now().dayOfMonth.toString(),
                        style = MaterialTheme.typography.headlineSmall,
                        color = scheme.onSurface,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    )
                    Text(
                        text = java.time.LocalDate.now().dayOfWeek.getDisplayName(
                            java.time.format.TextStyle.SHORT,
                            java.util.Locale.getDefault(),
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 6.dp, bottom = 4.dp),
                    )
                }
                for (width in listOf(0.8f, 0.55f)) {
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .size(width = 3.dp, height = 12.dp)
                                .background(scheme.primary),
                        )
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .padding(start = 6.dp)
                                .fillMaxWidth(width)
                                .height(6.dp)
                                .background(
                                    scheme.onSurface.copy(alpha = 0.75f),
                                    androidx.compose.foundation.shape.RoundedCornerShape(3.dp),
                                ),
                        )
                    }
                }
            }
        }
    }

    private fun save(
        appWidgetId: Int,
        theme: ThemeMode,
        opacityPercent: Int,
        dotColor: DotColor?,
    ) {
        val settings = (application as KoyomiApplication).container.settingsRepository
        lifecycleScope.launch {
            settings.setWidgetTheme(appWidgetId, theme)
            settings.setWidgetOpacity(appWidgetId, opacityPercent)
            dotColor?.let { settings.setWidgetDotColor(appWidgetId, it.name) }
            WidgetUpdateWorker.updateAllWidgets(applicationContext)
            setResult(
                RESULT_OK,
                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
            )
            finish()
        }
    }

    private fun isAggressiveBatteryOem(): Boolean =
        android.os.Build.MANUFACTURER.lowercase() in setOf("xiaomi", "redmi", "poco")

    /** MIUI / HyperOS auto-start list; falls back to the app's info page. */
    private fun openAutoStartSettings() {
        val autoStart = Intent().setComponent(
            android.content.ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity",
            ),
        )
        if (runCatching { startActivity(autoStart) }.isFailure) openAppDetails()
    }

    private fun openAppDetails() {
        runCatching {
            startActivity(
                Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:$packageName"),
                ),
            )
        }
    }
}
