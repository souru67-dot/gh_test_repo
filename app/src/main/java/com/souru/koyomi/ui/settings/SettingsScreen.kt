package com.souru.koyomi.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.souru.koyomi.R
import com.souru.koyomi.data.ThemeMode
import java.time.DayOfWeek
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = androidx.compose.runtime.remember {
        androidx.compose.material3.SnackbarHostState()
    }

    Scaffold(
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.close),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionLabel(stringResource(R.string.settings_week_start))
            RadioRow(
                label = stringResource(R.string.week_start_sunday),
                selected = state.weekStart == DayOfWeek.SUNDAY,
                onClick = { viewModel.setWeekStart(DayOfWeek.SUNDAY) },
            )
            RadioRow(
                label = stringResource(R.string.week_start_monday),
                selected = state.weekStart == DayOfWeek.MONDAY,
                onClick = { viewModel.setWeekStart(DayOfWeek.MONDAY) },
            )

            SectionLabel(stringResource(R.string.settings_month_scroll))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.settings_vertical_scroll),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = state.verticalScroll,
                    onCheckedChange = viewModel::setVerticalScroll,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.settings_multi_day_bars),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = state.multiDayBars,
                    onCheckedChange = viewModel::setMultiDayBars,
                )
            }
            SwitchRow(
                label = stringResource(R.string.settings_week_numbers),
                checked = state.showWeekNumbers,
                onChange = viewModel::setShowWeekNumbers,
            )
            SwitchRow(
                label = stringResource(R.string.settings_rokuyo),
                checked = state.showRokuyo,
                onChange = viewModel::setShowRokuyo,
            )
            SwitchRow(
                label = stringResource(R.string.settings_solar_terms),
                checked = state.showSolarTerms,
                onChange = viewModel::setShowSolarTerms,
            )
            SwitchRow(
                label = stringResource(R.string.settings_lunar_date),
                checked = state.showLunarDate,
                onChange = viewModel::setShowLunarDate,
            )
            SwitchRow(
                label = stringResource(R.string.settings_moon_age),
                checked = state.showMoonAge,
                onChange = viewModel::setShowMoonAge,
            )
            SwitchRow(
                label = stringResource(R.string.settings_japanese_era),
                checked = state.useJapaneseEra,
                onChange = viewModel::setUseJapaneseEra,
            )

            SectionLabel(stringResource(R.string.settings_theme_pack))
            for (pack in com.souru.koyomi.data.ThemePack.entries) {
                if (pack == com.souru.koyomi.data.ThemePack.CUSTOM) continue
                ThemePackRow(
                    pack = pack,
                    selected = state.themePack == pack && !state.dynamicColor,
                    onClick = { viewModel.setThemePack(pack) },
                )
            }
            CustomThemeSection(
                selected = state.themePack == com.souru.koyomi.data.ThemePack.CUSTOM &&
                    !state.dynamicColor,
                color = state.customThemeColor,
                onSelect = { viewModel.setThemePack(com.souru.koyomi.data.ThemePack.CUSTOM) },
                onColorChange = viewModel::setCustomThemeColor,
            )

            SectionLabel(stringResource(R.string.settings_theme))
            RadioRow(
                label = stringResource(R.string.theme_system),
                selected = state.themeMode == ThemeMode.SYSTEM,
                onClick = { viewModel.setThemeMode(ThemeMode.SYSTEM) },
            )
            RadioRow(
                label = stringResource(R.string.theme_light),
                selected = state.themeMode == ThemeMode.LIGHT,
                onClick = { viewModel.setThemeMode(ThemeMode.LIGHT) },
            )
            RadioRow(
                label = stringResource(R.string.theme_dark),
                selected = state.themeMode == ThemeMode.DARK,
                onClick = { viewModel.setThemeMode(ThemeMode.DARK) },
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.settings_dynamic_color),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = state.dynamicColor,
                    onCheckedChange = viewModel::setDynamicColor,
                )
            }

            SectionLabel(stringResource(R.string.settings_sync))
            for ((minutes, labelRes) in listOf(
                15 to R.string.sync_15min,
                30 to R.string.sync_30min,
                60 to R.string.sync_1hour,
                0 to R.string.sync_system_only,
            )) {
                RadioRow(
                    label = stringResource(labelRes),
                    selected = state.syncIntervalMinutes == minutes,
                    onClick = { viewModel.setSyncInterval(minutes) },
                )
            }

            NotificationPermissionSection()

            SectionLabel(stringResource(R.string.settings_widget_opacity))
            WidgetOpacitySlider(
                percent = state.widgetOpacityPercent,
                onChange = viewModel::setWidgetOpacity,
            )

            DataSection(viewModel = viewModel, state = state, snackbarHostState = snackbarHostState)

            SectionLabel(stringResource(R.string.settings_calendars))
            if (state.calendars.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_calendars_found),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
            for (calendar in state.calendars) {
                val shown = calendar.isVisible && calendar.id !in state.hiddenCalendarIds
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setCalendarShown(calendar, shown = !shown) }
                        .padding(horizontal = 20.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(
                                com.souru.koyomi.util.providerColor(calendar.color)
                                    ?: MaterialTheme.colorScheme.primary,
                                CircleShape,
                            ),
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp),
                    ) {
                        Text(
                            text = calendar.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = calendar.accountName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Checkbox(
                        checked = shown,
                        onCheckedChange = { checked ->
                            viewModel.setCalendarShown(calendar, shown = checked)
                        },
                    )
                }
            }
            Spacer(modifier = Modifier.padding(bottom = 24.dp))
        }
    }
}

@Composable
private fun WidgetOpacitySlider(percent: Int, onChange: (Int) -> Unit) {
    // Local value while dragging; persisted (and widgets redrawn) on release.
    var sliderValue by androidx.compose.runtime.remember(percent) {
        androidx.compose.runtime.mutableFloatStateOf(percent / 100f)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = {
                onChange((sliderValue * 100).toInt().coerceIn(20, 100))
            },
            valueRange = 0.2f..1f,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${(sliderValue * 100).toInt()}%",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

/** Reminder notifications need POST_NOTIFICATIONS on Android 13+. */
@Composable
private fun NotificationPermissionSection() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var granted by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(
            androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled(),
        )
    }
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { result -> granted = result }

    SectionLabel(stringResource(R.string.settings_notifications))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (granted) {
                stringResource(R.string.notifications_enabled)
            } else {
                stringResource(R.string.notifications_disabled)
            },
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        if (!granted) {
            androidx.compose.material3.TextButton(
                onClick = {
                    if (android.os.Build.VERSION.SDK_INT >= 33) {
                        launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        // Below 13 notifications are on by default; the state can
                        // only be changed from the system settings.
                        context.startActivity(
                            android.content.Intent(
                                android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS,
                            ).putExtra(
                                android.provider.Settings.EXTRA_APP_PACKAGE,
                                context.packageName,
                            ),
                        )
                    }
                },
            ) { Text(stringResource(R.string.notifications_allow)) }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** One theme pack: three swatch dots (surface, primary, accent) + name. */
@Composable
private fun ThemePackRow(
    pack: com.souru.koyomi.data.ThemePack,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val scheme = com.souru.koyomi.ui.theme.koyomiColorScheme(pack, dark)
    val nameRes = when (pack) {
        com.souru.koyomi.data.ThemePack.SUMI -> R.string.pack_sumi
        com.souru.koyomi.data.ThemePack.SAKURA -> R.string.pack_sakura
        com.souru.koyomi.data.ThemePack.WAKABA -> R.string.pack_wakaba
        com.souru.koyomi.data.ThemePack.AI -> R.string.pack_ai
        com.souru.koyomi.data.ThemePack.MOMIJI -> R.string.pack_momiji
        com.souru.koyomi.data.ThemePack.CUSTOM -> R.string.pack_custom
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Row(modifier = Modifier.padding(start = 4.dp, end = 10.dp)) {
            for (color in listOf(scheme.surface, scheme.primary, scheme.tertiary)) {
                Box(
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .size(16.dp)
                        .background(color, CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                )
            }
        }
        Text(
            text = stringResource(nameRes),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

// 和の伝統色 — preset seeds for the custom theme.
private val CustomPresetColors = listOf(
    0xFF5654A2.toInt(), // 桔梗
    0xFF2B5F9E.toInt(), // 瑠璃
    0xFF2A8A94.toInt(), // 浅葱
    0xFF33636B.toInt(), // 納戸
    0xFF4A6D48.toInt(), // 松葉
    0xFF6C6A2D.toInt(), // 鶯
    0xFFC7802D.toInt(), // 山吹
    0xFF8D5347.toInt(), // 小豆
    0xFFA94550.toInt(), // 茜
    0xFFC9767A.toInt(), // 珊瑚
    0xFF745399.toInt(), // 江戸紫
    0xFFA58F94.toInt(), // 桜鼠
)

/**
 * The CUSTOM pack row + its picker. Selecting the row switches to the
 * custom theme; the presets (traditional Japanese colors) and the hue
 * slider re-seed the derived palette.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun CustomThemeSection(
    selected: Boolean,
    color: Int,
    onSelect: () -> Unit,
    onColorChange: (Int) -> Unit,
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val scheme = com.souru.koyomi.ui.theme.koyomiColorScheme(
        com.souru.koyomi.data.ThemePack.CUSTOM,
        dark,
        customColor = color,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = 20.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Row(modifier = Modifier.padding(start = 4.dp, end = 10.dp)) {
            for (swatch in listOf(scheme.surface, scheme.primary, scheme.tertiary)) {
                Box(
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .size(16.dp)
                        .background(swatch, CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                )
            }
        }
        Text(
            text = stringResource(R.string.pack_custom),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
    if (!selected) return

    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 56.dp, end = 20.dp, top = 4.dp),
    ) {
        for (preset in CustomPresetColors) {
            val isCurrent = preset == color
            Box(
                modifier = Modifier
                    .padding(end = 10.dp, bottom = 10.dp)
                    .size(28.dp)
                    .background(Color(preset), CircleShape)
                    .border(
                        width = if (isCurrent) 2.dp else 1.dp,
                        color = if (isCurrent) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                        shape = CircleShape,
                    )
                    .clickable { onColorChange(preset) },
            )
        }
    }
    // Fine adjustment: sweep the hue, keeping a washi-friendly tone.
    val hsvArray = FloatArray(3)
    android.graphics.Color.colorToHSV(color, hsvArray)
    var hue by androidx.compose.runtime.remember(color) {
        androidx.compose.runtime.mutableFloatStateOf(hsvArray[0])
    }
    androidx.compose.material3.Slider(
        value = hue,
        onValueChange = { hue = it },
        onValueChangeFinished = {
            onColorChange(
                android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.55f, 0.60f)),
            )
        },
        valueRange = 0f..360f,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 56.dp, end = 20.dp),
    )
}

/** ICS export/import with the system document picker. */
@Composable
private fun DataSection(
    viewModel: SettingsViewModel,
    state: SettingsUiState,
    snackbarHostState: androidx.compose.material3.SnackbarHostState,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var pendingImportUri by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf<android.net.Uri?>(null)
    }

    fun report(count: Int, doneRes: Int, failRes: Int) {
        scope.launch {
            snackbarHostState.showSnackbar(
                if (count >= 0) {
                    context.getString(doneRes, count)
                } else {
                    context.getString(failRes)
                },
            )
        }
    }

    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/calendar"),
    ) { uri ->
        uri?.let {
            viewModel.exportIcs(it) { count ->
                report(count, R.string.export_done, R.string.export_failed)
            }
        }
    }
    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let {
            val writable = state.calendars.filter { c -> c.isWritable }
            when {
                writable.isEmpty() -> report(-1, R.string.import_done, R.string.import_failed)
                writable.size == 1 -> viewModel.importIcs(it, writable.first().id) { count ->
                    report(count, R.string.import_done, R.string.import_failed)
                }
                else -> pendingImportUri = it
            }
        }
    }

    SectionLabel(stringResource(R.string.settings_data))
    TextActionRow(stringResource(R.string.export_ics)) {
        val suggested = "koyomi-" +
            java.time.LocalDate.now()
                .format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE) + ".ics"
        exportLauncher.launch(suggested)
    }
    TextActionRow(stringResource(R.string.import_ics)) {
        importLauncher.launch(arrayOf("text/calendar", "text/plain", "application/octet-stream"))
    }

    pendingImportUri?.let { uri ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingImportUri = null },
            title = { Text(stringResource(R.string.import_choose_calendar)) },
            text = {
                Column {
                    for (calendar in state.calendars.filter { it.isWritable }) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    pendingImportUri = null
                                    viewModel.importIcs(uri, calendar.id) { count ->
                                        report(
                                            count,
                                            R.string.import_done,
                                            R.string.import_failed,
                                        )
                                    }
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(
                                        com.souru.koyomi.util.providerColor(calendar.color)
                                            ?: MaterialTheme.colorScheme.primary,
                                        CircleShape,
                                    ),
                            )
                            Text(
                                text = calendar.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { pendingImportUri = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun TextActionRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}
