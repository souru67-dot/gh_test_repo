package com.souru.colorhunt.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.souru.colorhunt.R
import com.souru.colorhunt.domain.color.ColorBucket

/** String resource for each bucket, so names are localized (en / ja / …). */
@StringRes
fun ColorBucket.labelRes(): Int = when (this) {
    ColorBucket.RED -> R.string.bucket_red
    ColorBucket.ORANGE -> R.string.bucket_orange
    ColorBucket.YELLOW -> R.string.bucket_yellow
    ColorBucket.YELLOW_GREEN -> R.string.bucket_yellow_green
    ColorBucket.GREEN -> R.string.bucket_green
    ColorBucket.CYAN -> R.string.bucket_cyan
    ColorBucket.BLUE -> R.string.bucket_blue
    ColorBucket.PURPLE -> R.string.bucket_purple
    ColorBucket.PINK -> R.string.bucket_pink
    ColorBucket.WHITE -> R.string.bucket_white
    ColorBucket.BLACK -> R.string.bucket_black
    ColorBucket.GRAY -> R.string.bucket_gray
}

@Composable
fun ColorBucket.label(): String = stringResource(labelRes())

/** The bucket's representative swatch as a Compose color. */
fun ColorBucket.composeColor(): Color = Color(swatch)

/** A small round colour dot; used on thumbnails and chips. */
@Composable
fun ColorDot(color: Color, modifier: Modifier = Modifier, size: Dp = 12.dp) {
    androidx.compose.foundation.layout.Box(
        modifier
            .size(size)
            .background(color, CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), CircleShape),
    )
}
