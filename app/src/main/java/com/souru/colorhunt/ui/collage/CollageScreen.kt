package com.souru.colorhunt.ui.collage

import android.Manifest
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.souru.colorhunt.BuildConfig
import com.souru.colorhunt.ColorHuntApplication
import com.souru.colorhunt.R
import com.souru.colorhunt.data.export.ShareHelper
import com.souru.colorhunt.domain.pro.ProState
import com.souru.colorhunt.domain.config.CellCountPreset
import com.souru.colorhunt.domain.config.CollageStyle
import com.souru.colorhunt.domain.config.SnsSize

private val SWATCHES = listOf(
    0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFFF5F5F5.toInt(), 0xFF212121.toInt(),
    0xFF7C4DFF.toInt(), 0xFF26C6DA.toInt(), 0xFFEC407A.toInt(), 0xFFFFC107.toInt(),
)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CollageScreen(
    modifier: Modifier = Modifier,
    viewModel: CollageViewModel = viewModel(factory = CollageViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val billing = remember { (context.applicationContext as ColorHuntApplication).container.billingManager }
    var showPaywall by remember { mutableStateOf(false) }

    val savedMsg = stringResource(R.string.collage_saved)
    val saveFailedMsg = stringResource(R.string.collage_save_failed)
    val shareFailedMsg = stringResource(R.string.collage_share_failed)
    val permissionMsg = stringResource(R.string.collage_save_permission)
    val shareTitle = stringResource(R.string.share_chooser_title)

    val storagePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) viewModel.onStoragePermissionGranted() }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                CollageEvent.Saved -> snackbar.showSnackbar(savedMsg)
                CollageEvent.SaveFailed -> snackbar.showSnackbar(saveFailedMsg)
                CollageEvent.ShareFailed -> snackbar.showSnackbar(shareFailedMsg)
                CollageEvent.SaveNeedsPermission -> {
                    snackbar.showSnackbar(permissionMsg)
                    storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
                is CollageEvent.ShareReady -> ShareHelper.shareSingle(context, event.uri, shareTitle)
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.collage_title)) }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when {
            state.isEmpty -> EmptyState(Modifier.padding(padding))
            else -> CollageContent(
                state = state,
                onSizeSelected = viewModel::setSize,
                onCountSelected = viewModel::setCountPreset,
                onHueSort = viewModel::sortByHue,
                onStyleChange = viewModel::updateStyle,
                onSave = viewModel::save,
                onShare = viewModel::share,
                onUpgrade = { showPaywall = true },
                modifier = Modifier.padding(padding),
            )
        }
    }

    if (showPaywall) {
        PaywallDialog(
            onDismiss = { showPaywall = false },
            onPurchase = {
                (context as? Activity)?.let { billing.launchPurchase(it) }
                showPaywall = false
            },
        )
    }
}

@Composable
private fun PaywallDialog(onDismiss: () -> Unit, onPurchase: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.WorkspacePremium, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(stringResource(R.string.paywall_title), fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(stringResource(R.string.paywall_body), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.size(8.dp))
                Text("・" + stringResource(R.string.paywall_b_watermark), style = MaterialTheme.typography.bodySmall)
                Text("・" + stringResource(R.string.paywall_b_sizes), style = MaterialTheme.typography.bodySmall)
                Text("・" + stringResource(R.string.paywall_b_cells), style = MaterialTheme.typography.bodySmall)
                Text("・" + stringResource(R.string.paywall_b_map), style = MaterialTheme.typography.bodySmall)
                if (BuildConfig.DEBUG) {
                    Spacer(Modifier.size(12.dp))
                    TextButton(onClick = { ProState.update(true); onDismiss() }) {
                        Text(stringResource(R.string.paywall_debug_unlock))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onPurchase) { Text(stringResource(R.string.paywall_purchase)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.collage_empty_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.size(8.dp))
        Text(
            stringResource(R.string.collage_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CollageContent(
    state: CollageUiState,
    onSizeSelected: (SnsSize) -> Unit,
    onCountSelected: (CellCountPreset) -> Unit,
    onHueSort: () -> Unit,
    onStyleChange: ((CollageStyle) -> CollageStyle) -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onUpgrade: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        PreviewArea(state)
        Spacer(Modifier.size(16.dp))

        if (!state.isPro) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                    .clickable(onClick = onUpgrade)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.WorkspacePremium, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.collage_upgrade),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.size(12.dp))
        }

        // Cell count presets
        SectionLabel(stringResource(R.string.collage_cells))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CellCountPreset.entries.forEach { preset ->
                FilterChip(
                    selected = state.countPreset == preset,
                    onClick = { onCountSelected(preset) },
                    label = { Text(preset.count?.toString() ?: stringResource(R.string.collage_count_custom)) },
                )
            }
        }
        Spacer(Modifier.size(12.dp))

        // SNS sizes
        SectionLabel(stringResource(R.string.collage_size))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SnsSize.entries.forEach { size ->
                val locked = size.proOnly && !state.isPro
                FilterChip(
                    selected = state.size == size,
                    onClick = { onSizeSelected(size) },
                    enabled = !locked,
                    leadingIcon = if (locked) {
                        { Icon(Icons.Filled.Lock, contentDescription = stringResource(R.string.collage_pro_locked), modifier = Modifier.size(16.dp)) }
                    } else null,
                    label = { Text(size.sizeLabel()) },
                )
            }
        }
        Spacer(Modifier.size(12.dp))

        // Hue sort
        AssistChip(
            onClick = onHueSort,
            leadingIcon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp)) },
            label = { Text(stringResource(R.string.collage_hue_sort)) },
        )
        Spacer(Modifier.size(16.dp))

        // Pro template: palette strip (hex codes)
        SectionLabel(stringResource(R.string.collage_templates))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.WorkspacePremium, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.collage_palette_strip), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.collage_palette_strip_sub), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.isPro) {
                Switch(
                    checked = state.style.paletteStrip,
                    onCheckedChange = { on -> onStyleChange { it.copy(paletteStrip = on) } },
                )
            } else {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(onClick = onUpgrade)
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                ) {
                    Text(
                        stringResource(R.string.collage_pro_locked),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
        Spacer(Modifier.size(16.dp))

        // Sliders
        StyleSlider(
            label = stringResource(R.string.collage_spacing),
            value = state.style.cellSpacingDp,
            range = 0f..24f,
            onChange = { v -> onStyleChange { it.copy(cellSpacingDp = v) } },
        )
        StyleSlider(
            label = stringResource(R.string.collage_corner),
            value = state.style.cornerRadiusDp,
            range = 0f..48f,
            onChange = { v -> onStyleChange { it.copy(cornerRadiusDp = v) } },
        )
        StyleSlider(
            label = stringResource(R.string.collage_border),
            value = state.style.borderWidthDp,
            range = 0f..8f,
            onChange = { v -> onStyleChange { it.copy(borderWidthDp = v) } },
        )
        Spacer(Modifier.size(8.dp))

        // Border colour
        SectionLabel(stringResource(R.string.collage_border_color))
        SwatchRow(selected = state.style.borderColor) { c -> onStyleChange { it.copy(borderColor = c) } }
        Spacer(Modifier.size(12.dp))

        // Background colour (+ follow-theme toggle)
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(stringResource(R.string.collage_background))
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.collage_bg_follow_theme), style = MaterialTheme.typography.labelMedium)
            Switch(
                checked = state.style.backgroundFollowsTheme,
                onCheckedChange = { on -> onStyleChange { it.copy(backgroundFollowsTheme = on) } },
            )
        }
        if (!state.style.backgroundFollowsTheme) {
            SwatchRow(selected = state.style.backgroundColor) { c -> onStyleChange { it.copy(backgroundColor = c) } }
        }
        Spacer(Modifier.size(20.dp))

        // Export actions
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Share, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.collage_share))
            }
            Button(onClick = onSave, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Save, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.collage_save))
            }
        }
        Spacer(Modifier.size(24.dp))
    }
}

@Composable
private fun PreviewArea(state: CollageUiState) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(state.size.aspectRatio)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        val preview = state.preview
        if (preview != null) {
            androidx.compose.foundation.Image(
                bitmap = preview.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (state.rendering || state.loading) {
            CircularProgressIndicator()
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 4.dp))
}

@Composable
private fun StyleSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().wrapContentHeight()) {
        Row {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.weight(1f))
            Text(value.toInt().toString(), style = MaterialTheme.typography.labelMedium)
        }
        androidx.compose.material3.Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
        )
    }
}

@Composable
private fun SwatchRow(selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SWATCHES.forEach { color ->
            val isSelected = color == selected
            Box(
                Modifier
                    .size(32.dp)
                    .background(Color(color), CircleShape)
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        shape = CircleShape,
                    )
                    .clickable { onSelect(color) },
            )
        }
    }
}

@Composable
private fun SnsSize.sizeLabel(): String = when (this) {
    SnsSize.SQUARE -> stringResource(R.string.size_square)
    SnsSize.PORTRAIT_4_5 -> stringResource(R.string.size_portrait)
    SnsSize.STORY_9_16 -> stringResource(R.string.size_story)
    SnsSize.LANDSCAPE_16_9 -> stringResource(R.string.size_landscape)
}
