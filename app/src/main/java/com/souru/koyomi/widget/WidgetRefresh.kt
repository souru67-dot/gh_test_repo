package com.souru.koyomi.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.currentState

/**
 * Why this exists: Glance keeps a widget's session alive for a while after
 * it renders, and `update()` on a LIVE session only recomposes — it does not
 * re-run provideGlance. Data loaded before provideContent was therefore
 * frozen for the session's lifetime: an event added (or an opacity changed)
 * right after a refresh never reached the home screen.
 *
 * Fix: every refresh bumps a per-widget tick in the widget's Glance state;
 * [provideFreshContent] reloads its snapshot whenever that tick changes, so
 * a live session redraws with fresh data too.
 */
private val RefreshTick = longPreferencesKey("koyomi_refresh_tick")

/** Renders [content] with [load]'s result, reloading on every refresh tick. */
suspend fun <T> GlanceAppWidget.provideFreshContent(
    load: suspend () -> T,
    content: @Composable (T) -> Unit,
) {
    val first = load()
    provideContent {
        val tick = currentState(RefreshTick) ?: 0L
        var snapshot by remember { mutableStateOf(first) }
        LaunchedEffect(tick) {
            if (tick != 0L) snapshot = runCatching { load() }.getOrDefault(snapshot)
        }
        content(snapshot)
    }
}

/** Bumps every placed instance's tick, then updates it (fresh data guaranteed). */
suspend fun GlanceAppWidget.refreshAll(context: Context) {
    val ids = runCatching {
        GlanceAppWidgetManager(context).getGlanceIds(this::class.java)
    }.getOrDefault(emptyList())
    for (id in ids) {
        runCatching {
            updateAppWidgetState(context, id) { prefs ->
                prefs[RefreshTick] = System.currentTimeMillis()
            }
            update(context, id)
        }
    }
}
