package com.souru.colorhunt.domain.roulette

import com.souru.colorhunt.domain.color.ColorBucket
import java.time.LocalDate

/**
 * "Today's color" roulette (Phase 4).
 *
 * The colour-picking core is pure and deterministic: a given day always maps to
 * the same theme colour (so a daily notification and the in-app wheel agree),
 * while [randomColor] powers the manual spin.
 */
object DailyColorRoulette {

    /** Chromatic buckets only — a theme colour of grey/black/white is no fun. */
    val choices: List<ColorBucket> = ColorBucket.entries.filter { !it.isAchromatic }

    /** Deterministically pick the theme colour for a given day-seed (epoch day works well). */
    fun colorForSeed(seed: Long): ColorBucket {
        val index = ((seed % choices.size) + choices.size) % choices.size
        return choices[index.toInt()]
    }

    /** The theme colour for today, stable for the whole day. */
    fun todayColor(today: LocalDate = LocalDate.now()): ColorBucket = colorForSeed(today.toEpochDay())

    /** A random theme colour for a manual spin. */
    fun randomColor(): ColorBucket = choices.random()

    /** A random hue (0..360) for the interactive roulette spin — vivid every time. */
    fun randomHue(): Float = kotlin.random.Random.nextFloat() * 360f

    fun indexOf(bucket: ColorBucket): Int = choices.indexOf(bucket)
}
