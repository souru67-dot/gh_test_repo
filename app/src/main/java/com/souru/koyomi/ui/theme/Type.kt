package com.souru.koyomi.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Defaults with a slightly quieter title — the calendar grid is the hero. */
val KoyomiTypography = Typography().let { base ->
    base.copy(
        titleLarge = base.titleLarge.copy(
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
        ),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = base.labelSmall.copy(fontSize = 10.sp),
    )
}
