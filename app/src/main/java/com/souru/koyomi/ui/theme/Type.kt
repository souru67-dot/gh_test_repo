package com.souru.koyomi.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Monochrome editorial type: confident, tightly tracked headlines (the month
 * name is the hero), calm body text, and small labels with a touch of
 * tracking so they read as crisp captions rather than shrunken body copy.
 */
val KoyomiTypography = Typography().let { base ->
    base.copy(
        headlineLarge = base.headlineLarge.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp,
        ),
        headlineMedium = base.headlineMedium.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp,
        ),
        titleLarge = base.titleLarge.copy(
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            letterSpacing = (-0.2).sp,
        ),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.4.sp,
        ),
        labelMedium = base.labelMedium.copy(letterSpacing = 0.3.sp),
        labelSmall = base.labelSmall.copy(fontSize = 10.sp, letterSpacing = 0.4.sp),
    )
}
