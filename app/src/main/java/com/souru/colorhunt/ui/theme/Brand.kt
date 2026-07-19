package com.souru.colorhunt.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Shared brand gradients. The signature ColorHunt look is a vivid
 * violet → pink → cyan sweep used for the hero header, primary CTAs and accents.
 */
object BrandGradients {

    val hero: Brush
        get() = Brush.linearGradient(
            colors = listOf(
                Color(0xFF6D28D9),
                BrandViolet,
                BrandPink,
                Color(0xFF0EA5B7),
            ),
        )

    val cta: Brush
        get() = Brush.horizontalGradient(listOf(BrandViolet, BrandPink))

    /** A soft translucent wash used behind cards/sections. */
    fun tint(color: Color): Brush = Brush.verticalGradient(
        listOf(color.copy(alpha = 0.20f), color.copy(alpha = 0.04f)),
    )
}
