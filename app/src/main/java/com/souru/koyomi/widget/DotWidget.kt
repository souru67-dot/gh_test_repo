package com.souru.koyomi.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.souru.koyomi.KoyomiApplication
import com.souru.koyomi.R
import com.souru.koyomi.data.ThemeMode
import com.souru.koyomi.data.holiday.Holidays
import com.souru.koyomi.data.model.EventInstance
import com.souru.koyomi.data.task.Task
import com.souru.koyomi.data.weather.DailyWeather
import com.souru.koyomi.data.weather.WeatherKind
import com.souru.koyomi.util.orderedWeekDays
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale
import kotlinx.coroutines.flow.first

class DotWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DotWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetUpdateWorker.schedule(context)
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

/**
 * ドット (4x2): an LED dot-matrix board — a big dot clock with its unlit
 * dots glowing faintly, the date with the day's weather, today's events
 * and to-dos, and a dot-matrix month. Orange on black by default; the LED
 * color is per widget.
 *
 * Widgets can't use bundled fonts (the launcher inflates the views), so:
 * - the clock is a live TextClock seen through a dot mask (see
 *   widget_dot_clock.xml) — it keeps ticking without widget redraws;
 * - the date/weather line and the month are drawn here, in the Doto dot
 *   face, onto bitmaps (refreshed at midnight / on changes like the rest);
 * - event and to-do titles stay in the system font (Doto is Latin-only).
 */
class DotWidget : GlanceAppWidget() {

    /** Exact size: the month bitmap is drawn to the widget's real height. */
    override val sizeMode = SizeMode.Exact

    private class Snapshot(
        val month: MonthGridData,
        val events: List<EventInstance>,
        val tasks: List<Task>,
        val weather: DailyWeather?,
        val color: DotColor,
        val look: WidgetLook,
        val premium: Boolean,
    )

    private suspend fun load(context: Context, id: GlanceId): Snapshot {
        val app = context.applicationContext as KoyomiApplication
        val today = LocalDate.now()
        val now = LocalTime.now()
        val tasks = runCatching {
            app.container.taskRepository
                .loadTasksByDay(today, today.plusDays(1))[today]
                .orEmpty()
                // Open to-dos; timed ones drop off once their time has passed.
                .filter { !it.done && (it.time?.isAfter(now) ?: true) }
        }.getOrDefault(emptyList())
        val weather = runCatching {
            app.container.weatherRepository.forecastByDay.first()[today]
        }.getOrNull()
        val appWidgetId = id.toString().filter { it.isDigit() }.toIntOrNull() ?: -1
        return Snapshot(
            month = loadMonthGridData(context),
            events = loadDaySections(context, days = 1).first().events,
            tasks = tasks,
            weather = weather,
            color = DotColor.fromName(
                app.container.settingsRepository.widgetDotColor(appWidgetId).first(),
            ),
            look = resolveWidgetLook(context, id, defaultTheme = ThemeMode.DARK),
            premium = isPremiumUnlocked(context),
        )
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideFreshContent(load = { load(context, id) }) { s ->
            GlanceTheme(colors = widgetColors(s.look)) {
                if (!s.premium) {
                    WidgetPremiumLock(context, s.look)
                } else {
                    Content(context, s)
                }
            }
        }
    }

    /** Whether this widget currently draws on a dark background. */
    private fun isDark(context: Context, look: WidgetLook): Boolean = look.forcedDark
        ?: ((context.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES)

    @Composable
    private fun Content(context: Context, s: Snapshot) {
        val today = LocalDate.now()
        val dark = isDark(context, s.look)
        val lit = if (dark) s.color.dark else s.color.light
        val size: DpSize = LocalSize.current
        // Split the width from the real widget size: the left column gets
        // about half (the clock auto-sizes to fill it), the month the rest —
        // no dead gap between them on wide launchers.
        val innerWidth = size.width.value - 28f
        val innerHeight = (size.height.value - 24f).coerceAtLeast(80f)
        val leftWidth = (innerWidth * 0.5f).coerceIn(130f, 230f)
        val calendarWidth = (innerWidth - 10f - leftWidth).coerceAtLeast(100f)
        val clockHeight = (innerHeight * 0.44f).coerceIn(40f, 78f)
        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(widgetBackground(s.look))
                .cornerRadius(24.dp)
                .clickable(actionStartActivity(openDayIntent(context, today)))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Column(
                modifier = GlanceModifier
                    .width(leftWidth.dp)
                    .fillMaxHeight(),
            ) {
                // Explicit size: the clock fills its box (auto-sized text) and
                // never claims the whole column.
                AndroidRemoteViews(
                    remoteViews = clockViews(context, s.look, s.color, lit, dark),
                    modifier = GlanceModifier
                        .width(leftWidth.dp)
                        .height(clockHeight.dp),
                )
                Spacer(modifier = GlanceModifier.height(4.dp))
                val line = DotRenderer.dateLine(context, today, s.weather, lit)
                val density = context.resources.displayMetrics.density
                // Shrink (never stretch) the line to the column width.
                val lineScale = minOf(1f, leftWidth / (line.width / density))
                Image(
                    provider = ImageProvider(line),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = GlanceModifier.size(
                        (line.width / density * lineScale).dp,
                        (line.height / density * lineScale).dp,
                    ),
                )
                Spacer(modifier = GlanceModifier.height(6.dp))
                Agenda(context, s, lit)
            }
            Spacer(modifier = GlanceModifier.width(10.dp))
            Image(
                provider = ImageProvider(
                    DotRenderer.month(
                        context = context,
                        data = s.month,
                        today = today,
                        widthDp = calendarWidth,
                        heightDp = innerHeight,
                        lit = lit,
                        holiday = if (s.color == DotColor.RED) {
                            if (dark) 0xFFF2F2F2.toInt() else 0xFF111111.toInt()
                        } else {
                            context.getColor(R.color.dot_holiday)
                        },
                        onDisc = if (s.color == DotColor.WHITE && dark) {
                            0xFF111111.toInt()
                        } else {
                            0xFFFFFFFF.toInt()
                        },
                    ),
                ),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = GlanceModifier
                    .width(calendarWidth.dp)
                    .height(innerHeight.dp),
            )
        }
    }

    /** Today's events, then today's open to-dos — one line each. */
    @Composable
    private fun Agenda(context: Context, s: Snapshot, lit: Int) {
        val markColor = androidx.glance.unit.ColorProvider(androidx.compose.ui.graphics.Color(lit))
        LazyColumn(modifier = GlanceModifier.fillMaxWidth()) {
            if (s.events.isEmpty() && s.tasks.isEmpty()) {
                item {
                    Text(
                        text = context.getString(R.string.no_events),
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 10.sp,
                        ),
                    )
                }
            }
            items(s.events) { event ->
                AgendaLine(
                    mark = formatWidgetTime(context, event),
                    title = event.title.ifBlank { context.getString(R.string.untitled) },
                    markColor = markColor,
                )
            }
            items(s.tasks) { task ->
                AgendaLine(
                    mark = task.time?.format(com.souru.koyomi.util.deviceTimeFormatter(context))
                        ?.let { "□ $it" } ?: "□",
                    title = task.title,
                    markColor = markColor,
                )
            }
        }
    }

    @Composable
    private fun AgendaLine(
        mark: String,
        title: String,
        markColor: androidx.glance.unit.ColorProvider,
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = mark,
                style = TextStyle(color = markColor, fontSize = 9.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
                modifier = GlanceModifier.width(44.dp),
            )
            Text(
                text = title,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 10.sp),
                maxLines = 1,
            )
        }
    }

    /**
     * The layered dot clock. Grids are view BACKGROUNDS (backgrounds tile;
     * an ImageView source would just stretch one dot across the clock).
     * The mask matches the widget's own background (every pack shares the
     * same white / near-black paper) and fades with the widget opacity, so a
     * see-through widget softens the dots instead of showing a dark slab.
     */
    private fun clockViews(
        context: Context,
        look: WidgetLook,
        color: DotColor,
        lit: Int,
        dark: Boolean,
    ): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_dot_clock).apply {
            setTextColor(R.id.dot_clock_time, lit)
            setInt(
                R.id.dot_clock_ghost,
                "setBackgroundResource",
                if (dark) color.ghostDark else color.ghostLight,
            )
            // Mask tiles are pre-baked at 20% steps of opacity (no runtime
            // alpha call, which older launchers can't apply to a view).
            val step = ((look.opacityPercent + 10) / 20).coerceIn(0, 5)
            if (step == 0) {
                setViewVisibility(R.id.dot_clock_mask, android.view.View.GONE)
            } else {
                setInt(R.id.dot_clock_mask, "setBackgroundResource", maskTile(dark, step))
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
            textSize = sp(context, 13f)
            color = lit
            textAlign = Paint.Align.CENTER
        }
        val cell = Paint(header).apply { textSize = sp(context, 9.5f) }
        val weekdayPaint = Paint(cell).apply { alpha = 180 }
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
