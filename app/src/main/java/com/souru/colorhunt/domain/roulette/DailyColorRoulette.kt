package com.souru.colorhunt.domain.roulette

import com.souru.colorhunt.domain.color.ColorBucket

/**
 * Phase 4 seam — "today's color" roulette.
 *
 * The full feature spins a hue wheel to pick a daily theme colour and fires a
 * daily notification. This stub provides the deterministic colour-picking core
 * (pure, testable) so the UI wheel and the notification scheduler can be layered
 * on later without changing how a day maps to a colour.
 */
object DailyColorRoulette {

    /** Chromatic buckets only — a "theme colour" of grey/black/white is no fun. */
    private val themeChoices: List<ColorBucket> = ColorBucket.entries.filter { !it.isAchromatic }

    /** Deterministically pick the theme colour for a given day (epoch-day works well as [seed]). */
    fun colorForSeed(seed: Long): ColorBucket {
        val index = ((seed % themeChoices.size) + themeChoices.size) % themeChoices.size
        return themeChoices[index.toInt()]
    }
}

/**
 * Phase 4 stub for the daily "today's color" notification. Empty on purpose —
 * marks where a WorkManager periodic job + notification channel will go.
 */
object DailyThemeNotifier {
    fun enableDailyReminder() {
        // TODO(Phase 4): schedule a daily WorkManager job that posts the roulette colour.
    }

    fun disableDailyReminder() {
        // TODO(Phase 4): cancel the scheduled job.
    }
}
