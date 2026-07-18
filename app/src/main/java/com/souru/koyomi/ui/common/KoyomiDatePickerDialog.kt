package com.souru.koyomi.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.souru.koyomi.R

/**
 * Replacement for Material 3's DatePickerDialog, which has a known bug:
 * tapping the year/month selector re-measures the dialog with the year
 * list's zero intrinsic height and the whole dialog collapses to a blank
 * sliver. A plain [Dialog] with an explicitly bounded, scrollable content
 * area cannot collapse, whatever display mode the picker switches to.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KoyomiDatePickerDialog(
    state: DatePickerState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmLabel: String = stringResource(R.string.ok),
) {
    // usePlatformDefaultWidth = false: the platform's default dialog width is
    // narrower than the DatePicker's fixed ~360dp, which clipped the Saturday
    // column. Letting the surface size to the picker fixes that. NOTE: the
    // picker must NOT be wrapped in a horizontalScroll — that hands it an
    // infinite width constraint and its internal Row weights crash at measure.
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.padding(16.dp),
        ) {
            Column(modifier = Modifier.heightIn(max = 560.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                ) {
                    DatePicker(state = state)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                    TextButton(onClick = onConfirm) {
                        Text(confirmLabel)
                    }
                }
            }
        }
    }
}
