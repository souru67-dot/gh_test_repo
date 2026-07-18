package com.souru.koyomi.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

                androidx.compose.runtime.LaunchedEffect(Unit) {
                    theme = settings.widgetTheme(appWidgetId).first()
                    opacity = (
                        settings.widgetOpacity(appWidgetId).first()
                            ?: settings.widgetOpacityPercent.first()
                        ) / 100f
                    loaded = true
                }

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

                        Button(
                            onClick = { save(appWidgetId, theme, (opacity * 100).toInt()) },
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

    private fun save(appWidgetId: Int, theme: ThemeMode, opacityPercent: Int) {
        val settings = (application as KoyomiApplication).container.settingsRepository
        lifecycleScope.launch {
            settings.setWidgetTheme(appWidgetId, theme)
            settings.setWidgetOpacity(appWidgetId, opacityPercent)
            WidgetUpdateWorker.updateAllWidgets(applicationContext)
            setResult(
                RESULT_OK,
                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
            )
            finish()
        }
    }
}
