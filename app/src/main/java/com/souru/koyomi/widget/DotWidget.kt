package com.souru.koyomi.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.TextPaint
import android.text.TextUtils
import android.widget.RemoteViews
import androidx.core.content.res.ResourcesCompat
import com.souru.koyomi.KoyomiApplication
import com.souru.koyomi.R
import com.souru.koyomi.data.ThemeMode
import com.souru.koyomi.data.holiday.Holidays
import com.souru.koyomi.data.model.EventInstance
import com.souru.koyomi.data.task.Task
import com.souru.koyomi.data.weather.DailyWeather
import com.souru.koyomi.data.weather.WeatherKind
import com.souru.koyomi.util.TimeStyle
import com.souru.koyomi.util.orderedWeekDays
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * ドット (4x2): an LED board — a 5x7 round-dot clock with its unlit dots
 * glowing faintly, the date with the day's weather, today's events and
 * to-dos (then tomorrow's), and a dot-matrix month.
 *
 * A classic RemoteViews widget rather than Glance: the clock is drawn here
 * as true 5x7 dot glyphs (launchers can't use bundled fonts), so it needs a
 * redraw every minute — done with a cheap partial update of just the clock
 * image, driven by a non-wakeup exact alarm (only fires while the device is
 * awake, i.e. when someone could be looking). Without the exact-alarm
 * permission it falls back to a self-ticking TextClock behind a dot mask.
 */
class DotWidgetReceiver : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        async { DotWidgetUpdater.updateAll(context) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        async { DotWidgetUpdater.update(context, appWidgetId) }
    }

    override fun onEnabled(context: Context) {
        WidgetUpdateWorker.schedule(context)
    }

    override fun onDisabled(context: Context) {
        DotClockTicker.cancel(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TICK -> async { DotWidgetUpdater.tick(context) }
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED ->
                async { DotWidgetUpdater.updateAll(context) }
            else -> super.onReceive(context, intent)
        }
    }

    private fun async(block: suspend () -> Unit) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                runCatching { block() }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_TICK = "com.souru.koyomi.action.DOT_TICK"
    }
}

/** Minute ticks for the dot clock (non-wakeup exact alarm, self re-arming). */
object DotClockTicker {

    /** Whether minute-exact redraws are allowed (else: TextClock fallback). */
    fun canTick(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = context.getSystemService(AlarmManager::class.java) ?: return false
        return am.canScheduleExactAlarms()
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        7,
        Intent(context, DotWidgetReceiver::class.java).setAction(DotWidgetReceiver.ACTION_TICK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun schedule(context: Context) {
        if (DotWidgetUpdater.ids(context).isEmpty() || !canTick(context)) {
            cancel(context)
            return
        }
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        // Just past the next minute boundary. RTC (not RTC_WAKEUP): while the
        // screen is off nothing fires; on wake the pending tick lands at once.
        val next = (System.currentTimeMillis() / 60_000L + 1) * 60_000L + 150L
        runCatching { am.setExact(AlarmManager.RTC, next, pendingIntent(context)) }
    }

    fun cancel(context: Context) {
        runCatching {
            context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent(context))
        }
    }
}

/**
 * LED colors for the 「ドット」 widget. Each has a variant for light
 * backgrounds, where the bright LED tones would wash out.
 */
enum class DotColor(
    val dark: Int,
    val light: Int,
    /** Tiled unlit-LED grids for the clock (dark / light backgrounds). */
    val ghostDark: Int,
    val ghostLight: Int,
) {
    ORANGE(0xFFF2711C.toInt(), 0xFFF2711C.toInt(), R.drawable.dot_ghost_orange_dark, R.drawable.dot_ghost_orange_light),
    GREEN(0xFF39E27A.toInt(), 0xFF1E9E52.toInt(), R.drawable.dot_ghost_green_dark, R.drawable.dot_ghost_green_light),
    WHITE(0xFFF2F2F2.toInt(), 0xFF111111.toInt(), R.drawable.dot_ghost_white_dark, R.drawable.dot_ghost_white_light),
    RED(0xFFFF4D3D.toInt(), 0xFFE03A2E.toInt(), R.drawable.dot_ghost_red_dark, R.drawable.dot_ghost_red_light),
    BLUE(0xFF4DA3FF.toInt(), 0xFF2F6FD6.toInt(), R.drawable.dot_ghost_blue_dark, R.drawable.dot_ghost_blue_light),
    ;

    companion object {
        fun fromName(name: String?): DotColor = entries.find { it.name == name } ?: ORANGE
    }
}

/** Loads, lays out and pushes the 「ドット」 widgets. */
object DotWidgetUpdater {

    private class Snapshot(
        val month: MonthGridData,
        val events: List<EventInstance>,
        val tasks: List<Task>,
        val tomorrow: List<EventInstance>,
        val weather: DailyWeather?,
        val color: DotColor,
        val look: WidgetLook,
        val premium: Boolean,
    )

    /** Layout metrics in dp, derived from the widget's real size. */
    private class Metrics(val width: Float, val height: Float) {
        val pad = 14f
        val padTop = 12f
        val innerWidth = width - 2 * pad
        val innerHeight = (height - 2 * padTop).coerceAtLeast(80f)
        val leftWidth = (innerWidth * 0.5f).coerceIn(130f, 240f)
        val calendarWidth = (innerWidth - 10f - leftWidth).coerceAtLeast(100f)
        /** Dot pitch of the clock: 5+5 | 5+5 digit columns plus gaps/colon ≈ 23.4 pitches wide. */
        val pitch = minOf(leftWidth / 23.4f, innerHeight * 0.45f / 7f)
        val clockHeight = pitch * 7f
    }

    fun ids(context: Context): IntArray = AppWidgetManager.getInstance(context)
        .getAppWidgetIds(ComponentName(context, DotWidgetReceiver::class.java))

    suspend fun updateAll(context: Context) {
        for (id in ids(context)) runCatching { update(context, id) }
        DotClockTicker.schedule(context)
    }

    private fun metrics(context: Context, appWidgetId: Int): Metrics {
        val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId)
        // Portrait size: min width x max height.
        val w = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it > 0 } ?: 300
        val h = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).takeIf { it > 0 } ?: 150
        return Metrics(w.toFloat(), h.toFloat())
    }

    private fun isDark(context: Context, look: WidgetLook): Boolean = look.forcedDark
        ?: ((context.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES)

    private suspend fun load(context: Context, appWidgetId: Int): Snapshot {
        val app = context.applicationContext as KoyomiApplication
        val today = LocalDate.now()
        val now = LocalTime.now()
        val tasks = runCatching {
            app.container.taskRepository
                .loadTasksByDay(today, today.plusDays(1))[today]
                .orEmpty()
                .filter { !it.done && (it.time?.isAfter(now) ?: true) }
        }.getOrDefault(emptyList())
        val weather = runCatching {
            app.container.weatherRepository.forecastByDay.first()[today]
        }.getOrNull()
        val sections = loadDaySections(context, days = 2)
        return Snapshot(
            month = loadMonthGridData(context),
            events = sections[0].events,
            tasks = tasks,
            tomorrow = sections[1].events.take(3),
            weather = weather,
            color = DotColor.fromName(
                app.container.settingsRepository.widgetDotColor(appWidgetId).first(),
            ),
            look = resolveWidgetLook(context, appWidgetId, defaultTheme = ThemeMode.DARK),
            premium = isPremiumUnlocked(context),
        )
    }

    suspend fun update(context: Context, appWidgetId: Int) {
        val s = load(context, appWidgetId)
        val m = metrics(context, appWidgetId)
        val dark = isDark(context, s.look)
        val lit = if (dark) s.color.dark else s.color.light
        val views = RemoteViews(context.packageName, R.layout.widget_dot)
        views.setImageViewBitmap(
            R.id.dot_canvas,
            DotRenderer.canvas(context, m.width, m.height, s.look, dark) { canvas, d ->
                if (!s.premium) {
                    DotRenderer.lockText(context, canvas, m.width * d, m.height * d, dark)
                } else {
                    drawBody(context, canvas, d, m, s, lit, dark)
                }
            },
        )
        val ticking = s.premium && DotClockTicker.canTick(context)
        views.setViewVisibility(R.id.dot_clock_bitmap, if (ticking) android.view.View.VISIBLE else android.view.View.GONE)
        views.setViewVisibility(
            R.id.dot_clock_live,
            if (s.premium && !ticking) android.view.View.VISIBLE else android.view.View.GONE,
        )
        if (ticking) {
            views.setImageViewBitmap(R.id.dot_clock_bitmap, clockBitmap(context, m, s.color, dark))
        } else if (s.premium) {
            applyLiveClock(views, s.look, s.color, lit, dark)
        }
        val open = PendingIntent.getActivity(
            context,
            appWidgetId,
            openDayIntent(context, LocalDate.now()),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(R.id.dot_root, open)
        AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, views)
    }

    /** Minute tick: redraw only the clock image of every dot widget. */
    suspend fun tick(context: Context) {
        val app = context.applicationContext as KoyomiApplication
        val manager = AppWidgetManager.getInstance(context)
        if (DotClockTicker.canTick(context)) {
            for (id in ids(context)) {
                runCatching {
                    val look = resolveWidgetLook(context, id, defaultTheme = ThemeMode.DARK)
                    val color = DotColor.fromName(
                        app.container.settingsRepository.widgetDotColor(id).first(),
                    )
                    val views = RemoteViews(context.packageName, R.layout.widget_dot)
                    views.setImageViewBitmap(
                        R.id.dot_clock_bitmap,
                        clockBitmap(context, metrics(context, id), color, isDark(context, look)),
                    )
                    manager.partiallyUpdateAppWidget(id, views)
                }
            }
        }
        DotClockTicker.schedule(context)
    }

    private fun clockBitmap(context: Context, m: Metrics, color: DotColor, dark: Boolean): Bitmap {
        val now = LocalTime.now()
        val hour = if (TimeStyle.is24Hour(context)) {
            now.hour
        } else {
            (now.hour % 12).let { if (it == 0) 12 else it }
        }
        return DotRenderer.clock(
            context = context,
            hour = hour,
            minute = now.minute,
            pitchDp = m.pitch,
            lit = if (dark) color.dark else color.light,
        )
    }

    /** Fallback (no exact alarms): self-ticking TextClock behind a dot mask. */
    private fun applyLiveClock(views: RemoteViews, look: WidgetLook, color: DotColor, lit: Int, dark: Boolean) {
        views.setTextColor(R.id.dot_clock_time, lit)
        applyClockStyle(views, R.id.dot_clock_time, h24 = "HH:mm", h12 = "hh:mm")
        if (look.opacityPercent >= 60) {
            views.setInt(R.id.dot_clock_ghost, "setBackgroundResource", if (dark) color.ghostDark else color.ghostLight)
        } else {
            views.setViewVisibility(R.id.dot_clock_ghost, android.view.View.GONE)
        }
        val step = ((look.opacityPercent + 10) / 20).coerceIn(0, 5)
        if (step == 0) {
            views.setViewVisibility(R.id.dot_clock_mask, android.view.View.GONE)
        } else {
            views.setInt(R.id.dot_clock_mask, "setBackgroundResource", maskTile(dark, step))
        }
    }

    private fun maskTile(dark: Boolean, step: Int): Int = if (dark) {
        when (step) {
            1 -> R.drawable.dot_mask_dark_20
            2 -> R.drawable.dot_mask_dark_40
            3 -> R.drawable.dot_mask_dark_60
            4 -> R.drawable.dot_mask_dark_80
            else -> R.drawable.dot_mask_dark_100
        }
    } else {
        when (step) {
            1 -> R.drawable.dot_mask_light_20
            2 -> R.drawable.dot_mask_light_40
            3 -> R.drawable.dot_mask_light_60
            4 -> R.drawable.dot_mask_light_80
            else -> R.drawable.dot_mask_light_100
        }
    }

    /** Everything but the clock: date line, agenda, month. */
    private fun drawBody(
        context: Context,
        canvas: Canvas,
        d: Float,
        m: Metrics,
        s: Snapshot,
        lit: Int,
        dark: Boolean,
    ) {
        val today = LocalDate.now()
        val is24 = TimeStyle.is24Hour(context)
        val left = m.pad * d
        var y = (m.padTop + m.clockHeight + 6f) * d
        val columnWidth = m.leftWidth * d

        // Date + weather, shrunk (never stretched) to the column.
        val line = DotRenderer.dateLine(context, today, s.weather, lit)
        val scale = minOf(1f, columnWidth / line.width)
        canvas.drawBitmap(
            line,
            null,
            RectF(left, y, left + line.width * scale, y + line.height * scale),
            null,
        )
        y += line.height * scale + 5 * d

        val bottom = (m.height - m.padTop) * d
        val rowH = 14f * d
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.DEFAULT
            textSize = sp(context, 10f)
            color = if (dark) 0xFFF2F2F2.toInt() else 0xFF111111.toInt()
        }
        val dimTitle = TextPaint(titlePaint).apply {
            color = if (dark) 0xFF8C8C8C.toInt() else 0xFF7A7A7A.toInt()
        }
        fun row(mark: Bitmap, title: String, paint: TextPaint): Boolean {
            if (y + rowH > bottom) return false
            val cy = y + rowH / 2f
            canvas.drawBitmap(mark, left, cy - mark.height / 2f, null)
            val tx = left + mark.width + 6 * d
            val avail = columnWidth - (tx - left)
            val text = TextUtils.ellipsize(title, paint, avail, TextUtils.TruncateAt.END).toString()
            canvas.drawText(text, tx, cy - (paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2f, paint)
            y += rowH
            return true
        }

        if (s.events.isEmpty() && s.tasks.isEmpty()) {
            if (y + rowH <= bottom) {
                canvas.drawText(
                    context.getString(R.string.no_events),
                    left,
                    y + rowH / 2f - (dimTitle.fontMetrics.ascent + dimTitle.fontMetrics.descent) / 2f,
                    dimTitle,
                )
                y += rowH
            }
        }
        var room = true
        for (event in s.events) {
            if (!room) break
            room = row(
                DotRenderer.mark(context, eventMark(event, is24), square = false, lit = lit, is24 = is24),
                event.title.ifBlank { context.getString(R.string.untitled) },
                titlePaint,
            )
        }
        for (task in s.tasks) {
            if (!room) break
            room = row(
                DotRenderer.mark(
                    context,
                    task.time?.let { clockText(it.hour, it.minute, is24) } ?: "",
                    square = true,
                    lit = lit,
                    is24 = is24,
                ),
                task.title,
                titlePaint,
            )
        }
        if (room && s.tomorrow.isNotEmpty() && y + rowH * 2 <= bottom) {
            val label = DotRenderer.label(context, "TOMORROW", lit)
            y += 3 * d
            canvas.drawBitmap(label, left, y, null)
            y += label.height + 1 * d
            for (event in s.tomorrow) {
                if (!row(
                        DotRenderer.mark(context, eventMark(event, is24), square = false, lit = lit, is24 = is24, dim = true),
                        event.title.ifBlank { context.getString(R.string.untitled) },
                        dimTitle,
                    )
                ) break
            }
        }

        // Month on the right.
        val month = DotRenderer.month(
            context = context,
            data = s.month,
            today = today,
            widthDp = m.calendarWidth,
            heightDp = m.innerHeight,
            lit = lit,
            holiday = if (s.color == DotColor.RED) {
                if (dark) 0xFFF2F2F2.toInt() else 0xFF111111.toInt()
            } else {
                context.getColor(R.color.dot_holiday)
            },
            onDisc = if (s.color == DotColor.WHITE && dark) 0xFF111111.toInt() else 0xFFFFFFFF.toInt(),
        )
        canvas.drawBitmap(month, (m.pad + m.leftWidth + 10f) * d, m.padTop * d, null)
    }

    private fun eventMark(event: EventInstance, is24: Boolean): String {
        if (event.allDay) return "ALL"
        val t = java.time.Instant.ofEpochMilli(event.begin)
            .atZone(java.time.ZoneId.systemDefault()).toLocalTime()
        return clockText(t.hour, t.minute, is24)
    }

    /** "21:30" or "9:30P" — compact, Latin-only for the dot face. */
    private fun clockText(hour: Int, minute: Int, is24: Boolean): String = if (is24) {
        "%02d:%02d".format(Locale.ENGLISH, hour, minute)
    } else {
        val h = if (hour % 12 == 0) 12 else hour % 12
        "%d:%02d%s".format(Locale.ENGLISH, h, minute, if (hour < 12) "A" else "P")
    }

    private fun sp(context: Context, value: Float): Float =
        android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_SP,
            value,
            context.resources.displayMetrics,
        )
}

/** Draws the Doto-face parts of the 「ドット」 widget onto bitmaps. */
private object DotRenderer {

    @Volatile private var cachedFace: Typeface? = null

    private fun face(context: Context): Typeface = cachedFace
        ?: (runCatching { ResourcesCompat.getFont(context, R.font.doto_dot) }.getOrNull()
            ?: Typeface.MONOSPACE).also { cachedFace = it }

    private fun newBitmap(context: Context, w: Int, h: Int): Bitmap =
        Bitmap.createBitmap(w.coerceAtLeast(1), h.coerceAtLeast(1), Bitmap.Config.ARGB_8888).apply {
            density = context.resources.displayMetrics.densityDpi
        }

    private fun sp(context: Context, value: Float): Float =
        android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_SP,
            value,
            context.resources.displayMetrics,
        )

    /** Classic 5x7 LED digits ('#' = lit). */
    private val DIGITS = mapOf(
        0 to listOf(".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."),
        1 to listOf("..#..", ".##..", "..#..", "..#..", "..#..", "..#..", ".###."),
        2 to listOf(".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####"),
        3 to listOf(".###.", "#...#", "....#", "..##.", "....#", "#...#", ".###."),
        4 to listOf("...#.", "..##.", ".#.#.", "#..#.", "#####", "...#.", "...#."),
        5 to listOf("#####", "#....", "####.", "....#", "....#", "#...#", ".###."),
        6 to listOf("..##.", ".#...", "#....", "####.", "#...#", "#...#", ".###."),
        7 to listOf("#####", "....#", "...#.", "..#..", ".#...", ".#...", ".#..."),
        8 to listOf(".###.", "#...#", "#...#", ".###.", "#...#", "#...#", ".###."),
        9 to listOf(".###.", "#...#", "#...#", ".####", "....#", "...#.", ".##.."),
    )

    /**
     * The LED clock: four 5x7 digits of big round dots, every unlit dot
     * faintly visible, and a colon column (two lit dots) — like a real dot panel.
     * In 12-hour mode a leading zero is left unlit.
     */
    fun clock(context: Context, hour: Int, minute: Int, pitchDp: Float, lit: Int): Bitmap {
        val d = context.resources.displayMetrics.density
        val p = pitchDp * d
        val gap = 0.6f * p
        val bitmap = newBitmap(context, kotlin.math.ceil(23.4f * p).toInt(), kotlin.math.ceil(7f * p).toInt())
        val canvas = Canvas(bitmap)
        val on = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = lit }
        val off = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = (lit and 0x00FFFFFF) or (0x2A shl 24)
        }
        val r = 0.42f * p
        var x = 0f
        fun digit(value: Int?) {
            val rows = value?.let { DIGITS[it] }
            for (row in 0 until 7) for (col in 0 until 5) {
                val isOn = rows?.get(row)?.get(col) == '#'
                canvas.drawCircle(x + (col + 0.5f) * p, (row + 0.5f) * p, r, if (isOn) on else off)
            }
            x += 5 * p
        }
        val leadingBlank = hour < 10 && !TimeStyle.is24Hour(context)
        digit(if (leadingBlank) null else hour / 10)
        x += gap
        digit(hour % 10)
        x += gap
        // Separator column: the colon is two lit dots (rows 3 and 5 of 7),
        // the rest of the column stays as unlit LEDs. Kept steady — a blink
        // would need per-second redraws.
        for (row in 0 until 7) {
            canvas.drawCircle(x + 0.5f * p, (row + 0.5f) * p, r, if (row == 2 || row == 4) on else off)
        }
        x += p + gap
        digit(minute / 10)
        x += gap
        digit(minute % 10)
        return bitmap
    }

    /** The widget's backdrop: its surface color at the chosen opacity, rounded. */
    fun canvas(
        context: Context,
        widthDp: Float,
        heightDp: Float,
        look: WidgetLook,
        dark: Boolean,
        draw: (Canvas, Float) -> Unit,
    ): Bitmap {
        val d = context.resources.displayMetrics.density
        val bitmap = newBitmap(context, (widthDp * d).toInt(), (heightDp * d).toInt())
        val canvas = Canvas(bitmap)
        val surface = if (dark) 0x0B0B0B else 0xFFFFFF
        val alpha = (look.opacityPercent * 255 / 100).coerceIn(0, 255)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = (alpha shl 24) or surface }
        canvas.drawRoundRect(
            RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat()),
            24 * d,
            24 * d,
            bg,
        )
        draw(canvas, d)
        return bitmap
    }

    /** Shown instead of the content until こよみ プレミアム is unlocked. */
    fun lockText(context: Context, canvas: Canvas, w: Float, h: Float, dark: Boolean) {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.DEFAULT
            textSize = sp(context, 12f)
            color = if (dark) 0xFFA3A3A3.toInt() else 0xFF6B6B6B.toInt()
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            context.getString(R.string.widget_premium_lock),
            w / 2f,
            h / 2f - (paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2f,
            paint,
        )
    }

    /** "11/23 MON ☀ 18° 70%" — rain only on umbrella-worthy days. */
    fun dateLine(context: Context, date: LocalDate, weather: DailyWeather?, lit: Int): Bitmap {
        val d = context.resources.displayMetrics.density
        val big = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = face(context)
            textSize = sp(context, 15f)
            color = lit
        }
        val small = Paint(big).apply { textSize = sp(context, 11f) }
        val weekday = date.dayOfWeek.getDisplayName(JavaTextStyle.SHORT, Locale.ENGLISH)
            .uppercase(Locale.ENGLISH)
        val dateText = "%02d/%02d %s".format(Locale.ENGLISH, date.monthValue, date.dayOfMonth, weekday)
        val icon = weather?.let {
            androidx.core.content.ContextCompat.getDrawable(context, weatherIcon(it.kind))?.mutate()
        }
        val iconSize = (13 * d).toInt()
        val temp = weather?.let { "${it.maxC.toInt()}°" }
        val rain = weather?.rainChance?.takeIf { it >= 50 }?.let { "$it%" }

        var width = big.measureText(dateText)
        if (icon != null) width += 8 * d + iconSize
        if (temp != null) width += 4 * d + small.measureText(temp)
        if (rain != null) width += 4 * d + small.measureText(rain)
        val height = (big.fontMetrics.let { it.descent - it.ascent } + 2 * d)
        val bitmap = newBitmap(context, kotlin.math.ceil(width).toInt() + 2, kotlin.math.ceil(height).toInt())
        val canvas = Canvas(bitmap)
        val mid = bitmap.height / 2f
        fun baseline(p: Paint) = mid - (p.fontMetrics.ascent + p.fontMetrics.descent) / 2f

        var x = 0f
        canvas.drawText(dateText, x, baseline(big), big)
        x += big.measureText(dateText)
        if (icon != null) {
            x += 8 * d
            val top = (mid - iconSize / 2f).toInt()
            icon.setBounds(x.toInt(), top, x.toInt() + iconSize, top + iconSize)
            icon.setTint(lit)
            icon.draw(canvas)
            x += iconSize
        }
        if (temp != null) {
            x += 4 * d
            canvas.drawText(temp, x, baseline(small), small)
            x += small.measureText(temp)
        }
        if (rain != null) {
            x += 4 * d
            val dim = Paint(small).apply { alpha = 190 }
            canvas.drawText(rain, x, baseline(dim), dim)
        }
        return bitmap
    }

    /**
     * The agenda's time column in the dot face, fixed width so titles align:
     * "21:30" / "9:30P" / "ALL", or a to-do box (with its time if any).
     */
    fun mark(
        context: Context,
        text: String,
        square: Boolean,
        lit: Int,
        is24: Boolean,
        dim: Boolean = false,
    ): Bitmap {
        val d = context.resources.displayMetrics.density
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = face(context)
            textSize = sp(context, 10f)
            color = lit
            if (dim) alpha = 150
        }
        val box = 7 * d
        val columnText = if (is24) "00:00" else "00:00P"
        val width = paint.measureText(columnText) + 2 * d
        val height = paint.fontMetrics.let { it.descent - it.ascent } + 2 * d
        val bitmap = newBitmap(context, kotlin.math.ceil(width).toInt(), kotlin.math.ceil(height).toInt())
        val canvas = Canvas(bitmap)
        val mid = bitmap.height / 2f
        var x = 0f
        if (square) {
            val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.2f * d
                color = lit
                if (dim) alpha = 150
            }
            canvas.drawRoundRect(x + 0.6f * d, mid - box / 2, x + box, mid + box / 2, 1.5f * d, 1.5f * d, stroke)
            x += box + 4 * d
        }
        if (text.isNotEmpty()) {
            canvas.drawText(text, x, mid - (paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2f, paint)
        }
        return bitmap
    }

    /** A small dim section label in the dot face ("TOMORROW"). */
    fun label(context: Context, text: String, lit: Int): Bitmap {
        val d = context.resources.displayMetrics.density
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = face(context)
            textSize = sp(context, 8.5f)
            color = lit
            alpha = 140
        }
        val bitmap = newBitmap(
            context,
            kotlin.math.ceil(paint.measureText(text) + 2 * d).toInt(),
            kotlin.math.ceil(paint.fontMetrics.let { it.descent - it.ascent } + 2 * d).toInt(),
        )
        Canvas(bitmap).drawText(
            text,
            0f,
            bitmap.height / 2f - (paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2f,
            paint,
        )
        return bitmap
    }

    private fun weatherIcon(kind: WeatherKind): Int = when (kind) {
        WeatherKind.SUN -> R.drawable.dot_wx_sun
        WeatherKind.PARTLY -> R.drawable.dot_wx_partly
        WeatherKind.CLOUD -> R.drawable.dot_wx_cloud
        WeatherKind.FOG -> R.drawable.dot_wx_fog
        WeatherKind.RAIN -> R.drawable.dot_wx_rain
        WeatherKind.SNOW -> R.drawable.dot_wx_snow
        WeatherKind.THUNDER -> R.drawable.dot_wx_thunder
    }

    /** "NOV 2026", weekday letters and the month's weeks, today on a disc. */
    fun month(
        context: Context,
        data: MonthGridData,
        today: LocalDate,
        widthDp: Float,
        heightDp: Float,
        lit: Int,
        holiday: Int,
        onDisc: Int,
    ): Bitmap {
        val d = context.resources.displayMetrics.density
        val w = (widthDp * d).toInt()
        val h = (heightDp * d).toInt()
        val bitmap = newBitmap(context, w, h)
        val canvas = Canvas(bitmap)
        val header = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = face(context)
            textSize = sp(context, 14f)
            color = lit
            textAlign = Paint.Align.CENTER
        }
        val cell = Paint(header).apply { textSize = sp(context, 11f) }
        val weekdayPaint = Paint(cell).apply {
            textSize = sp(context, 9f)
            alpha = 120
        }
        fun baseline(p: Paint, centerY: Float) =
            centerY - (p.fontMetrics.ascent + p.fontMetrics.descent) / 2f

        val month: YearMonth = data.month
        val headerH = header.fontMetrics.let { it.descent - it.ascent } + 2 * d
        canvas.drawText(
            month.month.getDisplayName(JavaTextStyle.SHORT, Locale.ENGLISH).uppercase(Locale.ENGLISH) +
                " " + month.year,
            w / 2f,
            baseline(header, headerH / 2f),
            header,
        )
        val colW = w / 7f
        val weekdayH = cell.fontMetrics.let { it.descent - it.ascent } + 5 * d
        orderedWeekDays(data.weekStart).forEachIndexed { i, day ->
            canvas.drawText(
                day.getDisplayName(JavaTextStyle.NARROW, Locale.ENGLISH),
                colW * (i + 0.5f),
                baseline(weekdayPaint, headerH + weekdayH / 2f),
                weekdayPaint,
            )
        }
        val weeks = (0 until 6).filter { week ->
            (0 until 7).any { YearMonth.from(data.days[week * 7 + it]) == month }
        }
        val gridTop = headerH + weekdayH
        val rowH = (h - gridTop) / weeks.size.coerceAtLeast(1)
        val disc = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = lit }
        weeks.forEachIndexed { row, week ->
            val cy = gridTop + rowH * (row + 0.5f)
            for (i in 0 until 7) {
                val date = data.days[week * 7 + i]
                if (YearMonth.from(date) != month) continue
                val cx = colW * (i + 0.5f)
                val paint = Paint(cell)
                when {
                    date == today -> {
                        canvas.drawCircle(cx, cy, minOf(colW, rowH) * 0.44f, disc)
                        paint.color = onDisc
                    }
                    Holidays.isRedDay(date) || date.dayOfWeek == DayOfWeek.SUNDAY ->
                        paint.color = holiday
                }
                canvas.drawText(date.dayOfMonth.toString(), cx, baseline(paint, cy), paint)
            }
        }
        return bitmap
    }
}
