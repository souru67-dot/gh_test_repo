package com.souru.koyomi.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarViewMonth
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material.icons.outlined.Brightness3
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.souru.koyomi.BuildConfig
import com.souru.koyomi.R
import com.souru.koyomi.data.ThemeMode
import com.souru.koyomi.data.ThemePack
import com.souru.koyomi.data.holiday.HolidayCountry
import com.souru.koyomi.util.ClockStyle
import java.time.DayOfWeek
import kotlinx.coroutines.launch

/**
 * Settings, as a short list of categories (each with a one-line summary of
 * its current values) that open their own page. Single choices are one
 * "title — value" row that opens a picker, so every page stays short.
 */
private enum class SettingsPage(val titleRes: Int, val icon: ImageVector) {
    DISPLAY(R.string.settings_cat_display, Icons.Outlined.CalendarViewMonth),
    KOYOMI(R.string.settings_cat_koyomi, Icons.Outlined.Brightness3),
    APPEARANCE(R.string.settings_cat_appearance, Icons.Outlined.Palette),
    CALENDARS(R.string.settings_cat_calendars, Icons.Outlined.Event),
    NOTIFICATIONS(R.string.settings_notifications, Icons.Outlined.NotificationsNone),
    WEATHER(R.string.settings_weather, Icons.Outlined.WbSunny),
    WIDGETS(R.string.settings_cat_widgets, Icons.Outlined.Widgets),
    DATA(R.string.settings_data, Icons.Outlined.SaveAlt),
    ABOUT(R.string.settings_cat_about, Icons.Outlined.Info),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenPremium: () -> Unit = {},
) {
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isPremium by viewModel.isPremium.collectAsStateWithLifecycle()
    val clockStyle by viewModel.clockStyle.collectAsStateWithLifecycle()
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    var pageName by rememberSaveable { mutableStateOf<String?>(null) }
    val page = pageName?.let { SettingsPage.valueOf(it) }

    BackHandler(enabled = page != null) { pageName = null }

    Scaffold(
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(page?.titleRes ?: R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = { if (page != null) pageName = null else onBack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.close),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                val forward = targetState != null
                (slideInHorizontally { w -> if (forward) w / 4 else -w / 4 } + fadeIn()) togetherWith
                    (slideOutHorizontally { w -> if (forward) -w / 4 else w / 4 } + fadeOut())
            },
            label = "settings-page",
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) { shown ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp),
            ) {
                val ctx = SettingsContext(viewModel, state, isPremium, clockStyle, onOpenPremium)
                when (shown) {
                    null -> SettingsHome(ctx, onOpen = { pageName = it.name })
                    SettingsPage.DISPLAY -> DisplayPage(ctx)
                    SettingsPage.KOYOMI -> KoyomiPage(ctx)
                    SettingsPage.APPEARANCE -> AppearancePage(ctx)
                    SettingsPage.CALENDARS -> CalendarsPage(ctx)
                    SettingsPage.NOTIFICATIONS -> NotificationPermissionSection()
                    SettingsPage.WEATHER -> WeatherPage(ctx)
                    SettingsPage.WIDGETS -> WidgetsPage(ctx)
                    SettingsPage.DATA -> DataSection(
                        viewModel = viewModel,
                        state = state,
                        snackbarHostState = snackbarHostState,
                        premiumUnlocked = isPremium,
                        onLockedAction = onOpenPremium,
                    )
                    SettingsPage.ABOUT -> AboutPage()
                }
            }
        }
    }
}

/** Everything a settings page needs, bundled to keep signatures short. */
private class SettingsContext(
    val viewModel: SettingsViewModel,
    val state: SettingsUiState,
    val isPremium: Boolean,
    val clockStyle: ClockStyle,
    val onOpenPremium: () -> Unit,
) {
    /** Premium-only switches: turning ON needs the unlock, OFF always works. */
    fun gateOn(setter: (Boolean) -> Unit): (Boolean) -> Unit = { value ->
        if (!value || isPremium) setter(value) else onOpenPremium()
    }
}

// ---------------------------------------------------------------- home

@Composable
private fun SettingsHome(ctx: SettingsContext, onOpen: (SettingsPage) -> Unit) {
    val s = ctx.state
    PremiumBannerRow(isPremium = ctx.isPremium, onClick = ctx.onOpenPremium)
    val notificationsOn = rememberNotificationsEnabled()
    val weatherPlace by ctx.viewModel.weatherPlace.collectAsStateWithLifecycle()
    for (page in SettingsPage.entries) {
        val summary = when (page) {
            SettingsPage.DISPLAY -> listOf(
                stringResource(weekStartLabel(s.weekStart)),
                stringResource(clockStyleLabel(ctx.clockStyle)),
                stringResource(
                    if (s.verticalScroll) R.string.settings_vertical_scroll else R.string.month_paging_swipe,
                ),
            ).joinToString("・")
            SettingsPage.KOYOMI -> stringResource(
                R.string.settings_koyomi_summary,
                stringResource(holidayLabel(s.holidayCountry)),
                listOf(
                    s.showRokuyo, s.showSolarTerms, s.showLuckyDays,
                    s.showLunarDate, s.showMoonAge, s.useJapaneseEra,
                ).count { it },
            )
            SettingsPage.APPEARANCE -> listOf(
                if (s.dynamicColor) "Material You" else stringResource(packLabel(s.themePack)),
                stringResource(themeModeLabel(s.themeMode)),
            ).joinToString("・")
            SettingsPage.CALENDARS -> stringResource(
                R.string.settings_calendars_summary,
                s.calendars.count { it.isVisible && it.id !in s.hiddenCalendarIds },
                stringResource(syncLabel(s.syncIntervalMinutes)),
            )
            SettingsPage.NOTIFICATIONS -> stringResource(
                if (notificationsOn) R.string.notifications_on else R.string.notifications_off,
            )
            SettingsPage.WEATHER -> weatherPlace?.name ?: stringResource(R.string.weather_place_none)
            SettingsPage.WIDGETS -> stringResource(R.string.settings_widget_summary, s.widgetOpacityPercent)
            SettingsPage.DATA -> stringResource(R.string.settings_data_summary)
            SettingsPage.ABOUT -> stringResource(R.string.settings_version, BuildConfig.VERSION_NAME)
        }
        CategoryRow(page, summary) { onOpen(page) }
    }
}

@Composable
private fun CategoryRow(page: SettingsPage, summary: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            page.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 18.dp),
        ) {
            Text(stringResource(page.titleRes), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------- pages

@Composable
private fun DisplayPage(ctx: SettingsContext) {
    val vm = ctx.viewModel
    val s = ctx.state
    ChoiceRow(
        title = stringResource(R.string.settings_week_start),
        options = listOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY),
        selected = s.weekStart,
        label = { stringResource(weekStartLabel(it)) },
        onSelect = vm::setWeekStart,
    )
    ChoiceRow(
        title = stringResource(R.string.settings_clock_style),
        options = ClockStyle.entries,
        selected = ctx.clockStyle,
        label = { stringResource(clockStyleLabel(it)) },
        onSelect = vm::setClockStyle,
    )
    ChoiceRow(
        title = stringResource(R.string.settings_month_paging),
        options = listOf(false, true),
        selected = s.verticalScroll,
        label = {
            stringResource(if (it) R.string.settings_vertical_scroll else R.string.month_paging_swipe)
        },
        onSelect = vm::setVerticalScroll,
    )
    SwitchRow(
        title = stringResource(R.string.settings_multi_day_bars),
        description = stringResource(R.string.settings_multi_day_bars_desc),
        checked = s.multiDayBars,
        onChange = vm::setMultiDayBars,
    )
    SwitchRow(
        title = stringResource(R.string.settings_week_numbers),
        checked = s.showWeekNumbers,
        onChange = vm::setShowWeekNumbers,
    )
}

@Composable
private fun KoyomiPage(ctx: SettingsContext) {
    val vm = ctx.viewModel
    val s = ctx.state
    ChoiceRow(
        title = stringResource(R.string.settings_holiday_country),
        options = listOf<HolidayCountry?>(null) + HolidayCountry.entries,
        selected = s.holidayCountry,
        label = { stringResource(holidayLabel(it)) },
        onSelect = vm::setHolidayCountry,
    )
    SectionLabel(stringResource(R.string.premium_feature_koyomi), premium = !ctx.isPremium)
    val premium = !ctx.isPremium
    SwitchRow(stringResource(R.string.settings_rokuyo), s.showRokuyo, ctx.gateOn(vm::setShowRokuyo), premium = premium)
    SwitchRow(stringResource(R.string.settings_solar_terms), s.showSolarTerms, ctx.gateOn(vm::setShowSolarTerms), premium = premium)
    SwitchRow(stringResource(R.string.settings_lucky_days), s.showLuckyDays, ctx.gateOn(vm::setShowLuckyDays), premium = premium)
    SwitchRow(stringResource(R.string.settings_lunar_date), s.showLunarDate, ctx.gateOn(vm::setShowLunarDate), premium = premium)
    SwitchRow(stringResource(R.string.settings_moon_age), s.showMoonAge, ctx.gateOn(vm::setShowMoonAge), premium = premium)
    SwitchRow(stringResource(R.string.settings_japanese_era), s.useJapaneseEra, ctx.gateOn(vm::setUseJapaneseEra), premium = premium)
}

@Composable
private fun AppearancePage(ctx: SettingsContext) {
    val vm = ctx.viewModel
    val s = ctx.state
    ChoiceRow(
        title = stringResource(R.string.settings_theme),
        options = ThemeMode.entries,
        selected = s.themeMode,
        label = { stringResource(themeModeLabel(it)) },
        onSelect = vm::setThemeMode,
    )
    SwitchRow(
        title = stringResource(R.string.settings_dynamic_color),
        description = stringResource(R.string.settings_dynamic_color_desc),
        checked = s.dynamicColor,
        onChange = vm::setDynamicColor,
    )
    SectionLabel(stringResource(R.string.settings_theme_pack), premium = !ctx.isPremium)
    for (pack in ThemePack.entries) {
        if (pack == ThemePack.CUSTOM) continue
        ThemePackRow(
            pack = pack,
            selected = s.themePack == pack && !s.dynamicColor,
            onClick = {
                if (pack == ThemePack.SUMI || ctx.isPremium) vm.setThemePack(pack) else ctx.onOpenPremium()
            },
        )
    }
    CustomThemeSection(
        selected = s.themePack == ThemePack.CUSTOM && !s.dynamicColor,
        color = s.customThemeColor,
        onSelect = { if (ctx.isPremium) vm.setThemePack(ThemePack.CUSTOM) else ctx.onOpenPremium() },
        onColorChange = { color ->
            if (ctx.isPremium) vm.setCustomThemeColor(color) else ctx.onOpenPremium()
        },
    )
}

@Composable
private fun CalendarsPage(ctx: SettingsContext) {
    val vm = ctx.viewModel
    val s = ctx.state
    val writable = s.calendars.filter { it.isWritable }
    val defaultId = s.defaultCalendarId?.takeIf { id -> writable.any { it.id == id } }
    ChoiceRow(
        title = stringResource(R.string.settings_default_calendar),
        options = listOf<Long?>(null) + writable.map { it.id },
        selected = defaultId,
        label = { id ->
            if (id == null) {
                stringResource(R.string.default_calendar_last_used)
            } else {
                writable.first { it.id == id }.displayName
            }
        },
        onSelect = vm::setDefaultCalendar,
    )
    ChoiceRow(
        title = stringResource(R.string.settings_sync),
        options = listOf(15, 30, 60, 0),
        selected = s.syncIntervalMinutes,
        label = { stringResource(syncLabel(it)) },
        onSelect = vm::setSyncInterval,
    )
    SectionLabel(stringResource(R.string.settings_calendars))
    if (s.calendars.isEmpty()) {
        Text(
            text = stringResource(R.string.no_calendars_found),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
    }
    for (calendar in s.calendars) {
        val shown = calendar.isVisible && calendar.id !in s.hiddenCalendarIds
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { vm.setCalendarShown(calendar, shown = !shown) }
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
                    .padding(start = 14.dp),
            ) {
                Text(text = calendar.displayName, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = calendar.accountName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Checkbox(
                checked = shown,
                onCheckedChange = { checked -> vm.setCalendarShown(calendar, shown = checked) },
            )
        }
    }
}

@Composable
private fun WeatherPage(ctx: SettingsContext) {
    WeatherPlaceRow(viewModel = ctx.viewModel)
    FootNote(stringResource(R.string.settings_weather_credit))
}

@Composable
private fun WidgetsPage(ctx: SettingsContext) {
    SectionLabel(stringResource(R.string.settings_widget_opacity))
    WidgetOpacitySlider(
        percent = ctx.state.widgetOpacityPercent,
        onChange = ctx.viewModel::setWidgetOpacity,
    )
    FootNote(stringResource(R.string.settings_widget_opacity_desc))
}

@Composable
private fun AboutPage() {
    var showLicenses by remember { mutableStateOf(false) }
    ValueRow(
        title = stringResource(R.string.app_name),
        value = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
        onClick = null,
    )
    ValueRow(
        title = stringResource(R.string.settings_licenses),
        value = null,
        onClick = { showLicenses = true },
    )
    FootNote(stringResource(R.string.settings_weather_credit))
    if (showLicenses) {
        val context = LocalContext.current
        val ofl = remember {
            runCatching {
                context.assets.open("licenses/Doto-OFL.txt").bufferedReader().use { it.readText() }
            }.getOrDefault("")
        }
        AlertDialog(
            onDismissRequest = { showLicenses = false },
            title = { Text(stringResource(R.string.settings_licenses)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(OSS_NOTICE, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(12.dp))
                    Text("Doto — SIL Open Font License 1.1", style = MaterialTheme.typography.titleSmall)
                    Text(ofl, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { showLicenses = false }) { Text(stringResource(R.string.close)) }
            },
        )
    }
}

private const val OSS_NOTICE =
    "AndroidX, Jetpack Compose, Glance, WorkManager, DataStore — Apache License 2.0\n" +
        "Kotlin, kotlinx.coroutines — Apache License 2.0\n" +
        "Google Play Billing Library — Android Software Development Kit License\n" +
        "Doto font — Copyright 2024 The Doto Project Authors, SIL Open Font License 1.1"

// ---------------------------------------------------------------- rows

/** "Title — current value" row that opens a single-choice picker. */
@Composable
private fun <T> ChoiceRow(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    ValueRow(title = title, value = label(selected), onClick = { open = true })
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    for (option in options) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelect(option)
                                    open = false
                                }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = option == selected,
                                onClick = {
                                    onSelect(option)
                                    open = false
                                },
                            )
                            Text(
                                text = label(option),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 6.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { open = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun ValueRow(
    title: String,
    value: String?,
    premium: Boolean = false,
    onClick: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (premium) PremiumMark()
            }
            if (value != null) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    description: String? = null,
    premium: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f, fill = false))
                if (premium) PremiumMark()
            }
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ActionRow(
    title: String,
    description: String,
    premium: Boolean = false,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
            if (premium) PremiumMark()
        }
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Small "◆ Premium" tag next to settings that need the unlock. */
@Composable
private fun PremiumMark() {
    Text(
        text = "◆ " + stringResource(R.string.settings_premium_only),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.tertiary,
        modifier = Modifier.padding(start = 8.dp),
    )
}

@Composable
private fun SectionLabel(text: String, premium: Boolean = false) {
    Column {
        HorizontalDivider(
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        Row(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (premium) PremiumMark()
        }
    }
}

@Composable
private fun FootNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
    )
}

@Composable
private fun WidgetOpacitySlider(percent: Int, onChange: (Int) -> Unit) {
    // Local value while dragging; persisted (and widgets redrawn) on release.
    var sliderValue by remember(percent) {
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
            onValueChangeFinished = { onChange((sliderValue * 100).toInt().coerceIn(0, 100)) },
            // 0% = fully transparent: text straight on the wallpaper.
            valueRange = 0f..1f,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${(sliderValue * 100).toInt()}%",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
private fun rememberNotificationsEnabled(): Boolean {
    val context = LocalContext.current
    return remember {
        androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
}

/** Reminder notifications need POST_NOTIFICATIONS on Android 13+. */
@Composable
private fun NotificationPermissionSection() {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { result -> granted = result }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(
                if (granted) R.string.notifications_enabled else R.string.notifications_disabled,
            ),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        if (!granted) {
            TextButton(
                onClick = {
                    if (android.os.Build.VERSION.SDK_INT >= 33) {
                        launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        // Below 13 notifications are on by default; the state can
                        // only be changed from the system settings.
                        context.startActivity(
                            android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName),
                        )
                    }
                },
            ) { Text(stringResource(R.string.notifications_allow)) }
        }
    }
}

// ---------------------------------------------------------------- labels

private fun weekStartLabel(day: DayOfWeek): Int =
    if (day == DayOfWeek.MONDAY) R.string.week_start_monday else R.string.week_start_sunday

private fun clockStyleLabel(style: ClockStyle): Int = when (style) {
    ClockStyle.SYSTEM -> R.string.clock_style_system
    ClockStyle.H24 -> R.string.clock_style_24h
    ClockStyle.H12 -> R.string.clock_style_12h
}

private fun themeModeLabel(mode: ThemeMode): Int = when (mode) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

private fun syncLabel(minutes: Int): Int = when (minutes) {
    15 -> R.string.sync_15min
    30 -> R.string.sync_30min
    60 -> R.string.sync_1hour
    else -> R.string.sync_system_only
}

private fun packLabel(pack: ThemePack): Int = when (pack) {
    ThemePack.SUMI -> R.string.pack_sumi
    ThemePack.SAKURA -> R.string.pack_sakura
    ThemePack.WAKABA -> R.string.pack_wakaba
    ThemePack.AI -> R.string.pack_ai
    ThemePack.MOMIJI -> R.string.pack_momiji
    ThemePack.CUSTOM -> R.string.pack_custom
}

private fun holidayLabel(country: HolidayCountry?): Int =
    if (country == null) R.string.holiday_country_auto else holidayCountryLabel(country)

private fun holidayCountryLabel(country: com.souru.koyomi.data.holiday.HolidayCountry): Int =
    when (country) {
        com.souru.koyomi.data.holiday.HolidayCountry.JAPAN -> R.string.holiday_country_jp
        com.souru.koyomi.data.holiday.HolidayCountry.UNITED_STATES -> R.string.holiday_country_us
        com.souru.koyomi.data.holiday.HolidayCountry.UNITED_KINGDOM -> R.string.holiday_country_gb
        com.souru.koyomi.data.holiday.HolidayCountry.SOUTH_KOREA -> R.string.holiday_country_kr
        com.souru.koyomi.data.holiday.HolidayCountry.NONE -> R.string.holiday_country_none
    }


/** こよみ プレミアム banner: opens the paywall (or shows the owned state). */
@Composable
private fun PremiumBannerRow(isPremium: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.premium_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(
                        if (isPremium) R.string.premium_owned else R.string.premium_banner,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "◆",
                color = MaterialTheme.colorScheme.tertiary,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

/**
 * Forecast location: shows the chosen place; tapping opens a name search
 * (Open-Meteo geocoding). Clearing the place turns weather display off.
 */
@Composable
private fun WeatherPlaceRow(viewModel: SettingsViewModel) {
    val place by viewModel.weatherPlace.collectAsStateWithLifecycle()
    val results by viewModel.weatherResults.collectAsStateWithLifecycle()
    var showDialog by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(false)
    }
    ValueRow(
        title = stringResource(R.string.weather_place),
        value = place?.name ?: stringResource(R.string.weather_place_none),
        onClick = { showDialog = true },
    )
    if (showDialog) {
        var query by androidx.compose.runtime.remember {
            androidx.compose.runtime.mutableStateOf("")
        }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.weather_place)) },
            text = {
                Column {
                    androidx.compose.material3.OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text(stringResource(R.string.weather_search_hint)) },
                        singleLine = true,
                        trailingIcon = {
                            androidx.compose.material3.TextButton(
                                onClick = { viewModel.searchWeatherPlaces(query) },
                            ) { Text(stringResource(R.string.search)) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    for (result in results) {
                        Text(
                            text = result.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setWeatherPlace(result)
                                    showDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                        )
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = { showDialog = false },
                ) { Text(stringResource(R.string.close)) }
            },
            dismissButton = {
                if (place != null) {
                    androidx.compose.material3.TextButton(
                        onClick = {
                            viewModel.setWeatherPlace(null)
                            showDialog = false
                        },
                    ) { Text(stringResource(R.string.weather_clear)) }
                }
            },
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
            for (swatch in listOf(scheme.surface, scheme.primaryContainer, scheme.primary)) {
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

/** One theme pack: three swatch dots (surface, primary, accent) + name. */
@Composable
private fun ThemePackRow(
    pack: com.souru.koyomi.data.ThemePack,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val scheme = com.souru.koyomi.ui.theme.koyomiColorScheme(pack, dark)
    val nameRes = packLabel(pack)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Row(modifier = Modifier.padding(start = 4.dp, end = 10.dp)) {
            for (color in listOf(scheme.surface, scheme.primaryContainer, scheme.primary)) {
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

/** ICS export/import + transfer backup with the system document picker. */
@Composable
private fun DataSection(
    viewModel: SettingsViewModel,
    state: SettingsUiState,
    snackbarHostState: androidx.compose.material3.SnackbarHostState,
    premiumUnlocked: Boolean,
    onLockedAction: () -> Unit,
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

    val backupExportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        uri?.let {
            viewModel.exportBackup(it) { ok ->
                report(if (ok) 0 else -1, R.string.backup_done, R.string.export_failed)
            }
        }
    }
    val backupImportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let {
            viewModel.importBackup(it) { count ->
                report(count, R.string.restore_done, R.string.import_failed)
            }
        }
    }

    ActionRow(stringResource(R.string.export_ics), stringResource(R.string.export_ics_desc)) {
        val suggested = "koyomi-" +
            java.time.LocalDate.now()
                .format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE) + ".ics"
        exportLauncher.launch(suggested)
    }
    ActionRow(stringResource(R.string.import_ics), stringResource(R.string.import_ics_desc)) {
        importLauncher.launch(arrayOf("text/calendar", "text/plain", "application/octet-stream"))
    }
    ActionRow(
        stringResource(R.string.backup_export),
        stringResource(R.string.backup_export_desc),
        premium = !premiumUnlocked,
    ) {
        if (!premiumUnlocked) {
            onLockedAction()
        } else {
            val suggested = "koyomi-backup-" +
                java.time.LocalDate.now()
                    .format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE) + ".json"
            backupExportLauncher.launch(suggested)
        }
    }
    ActionRow(
        stringResource(R.string.backup_import),
        stringResource(R.string.backup_import_desc),
        premium = !premiumUnlocked,
    ) {
        if (!premiumUnlocked) {
            onLockedAction()
        } else {
            backupImportLauncher.launch(
                arrayOf("application/json", "application/octet-stream", "text/plain"),
            )
        }
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

