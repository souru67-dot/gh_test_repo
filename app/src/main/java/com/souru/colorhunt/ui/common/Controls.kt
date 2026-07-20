package com.souru.colorhunt.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable

/**
 * Switch colours with a clearly visible OFF state.
 *
 * The Material3 default paints the unchecked track/thumb in near-surface tones
 * that vanish on our dark background — so the toggle looked invisible until
 * turned on. This gives the OFF state an outlined track and a light thumb.
 */
@Composable
fun brandSwitchColors(): SwitchColors = SwitchDefaults.colors(
    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
    checkedTrackColor = MaterialTheme.colorScheme.primary,
    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
    uncheckedBorderColor = MaterialTheme.colorScheme.outline,
)
