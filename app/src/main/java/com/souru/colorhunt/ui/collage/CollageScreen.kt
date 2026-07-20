package com.souru.colorhunt.ui.collage

import android.Manifest
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import com.souru.colorhunt.domain.config.CollageGeometry
import com.souru.colorhunt.domain.config.CollageLayout
import com.souru.colorhunt.domain.config.CollageTemplates
import com.souru.colorhunt.domain.config.FocalPoint
import com.souru.colorhunt.domain.config.PalettePlacement
import com.souru.colorhunt.domain.model.HuntPhoto
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
    val shareCaption = stringResource(R.string.share_caption)
    val captionCopiedMsg = stringResource(R.string.share_caption_copied)

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
                is CollageEvent.ShareReady -> {
                    // Share assist: put a ready-made hashtag caption on the clipboard
                    // so posting is paste-and-go.
                    runCatching {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                            as android.content.ClipboardManager
                        clipboard.setPrimaryClip(
                            android.content.ClipData.newPlainText("ColorHunt", shareCaption),
                        )
                    }
                    ShareHelper.shareSingle(context, event.uri, shareTitle)
                    snackbar.showSnackbar(captionCopiedMsg)
                }
            }
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { CollageHeader(photoCount = state.photoCount) },
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
                onMove = viewModel::move,
                onSetFocal = viewModel::setFocal,
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
                Text("• " + stringResource(R.string.paywall_b_watermark), style = MaterialTheme.typography.bodySmall)
                Text("• " + stringResource(R.string.paywall_b_sizes), style = MaterialTheme.typography.bodySmall)
                Text("• " + stringResource(R.string.paywall_b_cells), style = MaterialTheme.typography.bodySmall)
                Text("• " + stringResource(R.string.paywall_b_map), style = MaterialTheme.typography.bodySmall)
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
    onMove: (Int, Int) -> Unit,
    onSetFocal: (Int, FocalPoint) -> Unit,
    modifier: Modifier = Modifier,
) {
    var adjustMode by remember { mutableStateOf(false) }
    // Cell being crop-edited: index + its aspect ratio (for the editor frame).
    var editTarget by remember { mutableStateOf<Pair<Int, Float>?>(null) }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        PreviewArea(
            state = state,
            adjustMode = adjustMode,
            onMove = onMove,
            onEditCell = { index, ratio -> editTarget = index to ratio },
        )
        Spacer(Modifier.size(10.dp))
        PreviewModeToggle(adjustMode = adjustMode, onChange = { adjustMode = it })
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

        // One-tap magazine presets (trend-curated). Applying one just rewrites the
        // style + size; photo order and crop focals stay untouched.
        SectionLabel(stringResource(R.string.collage_templates))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CollageTemplates.all.forEach { tpl ->
                val locked = tpl.proOnly && !state.isPro
                FilterChip(
                    selected = false,
                    onClick = {
                        if (locked) {
                            onUpgrade()
                        } else {
                            tpl.size?.let(onSizeSelected)
                            onStyleChange(tpl.apply)
                        }
                    },
                    leadingIcon = if (locked) {
                        { Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null,
                    label = { Text(templateLabel(tpl.id)) },
                )
            }
        }
        Spacer(Modifier.size(12.dp))

        // Card 1 — composition: cell count, SNS size, layout.
        ControlCard {
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
            Spacer(Modifier.size(14.dp))

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
            Spacer(Modifier.size(14.dp))

            SectionLabel(stringResource(R.string.collage_layout))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val layouts = listOf(
                    CollageLayout.GRID to R.string.collage_layout_grid,
                    CollageLayout.VERTICAL to R.string.collage_layout_vertical,
                    CollageLayout.TWO_COLUMN to R.string.collage_layout_two_col,
                )
                layouts.forEach { (lay, res) ->
                    FilterChip(
                        selected = state.style.layout == lay,
                        onClick = { onStyleChange { it.copy(layout = lay) } },
                        label = { Text(stringResource(res)) },
                    )
                }
            }
        }
        Spacer(Modifier.size(12.dp))

        // Card 2 — palette placement (Pro) + hue auto-sort.
        ControlCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel(stringResource(R.string.collage_palette))
                if (!state.isPro) {
                    Spacer(Modifier.width(8.dp))
                    ProPill(onClick = onUpgrade)
                }
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val placements = listOf(
                    PalettePlacement.NONE to R.string.collage_palette_none,
                    PalettePlacement.CENTER to R.string.collage_palette_center,
                    PalettePlacement.OVERLAY to R.string.collage_palette_overlay,
                    PalettePlacement.LEFT to R.string.collage_palette_left,
                    PalettePlacement.SIDE to R.string.collage_palette_side,
                )
                placements.forEach { (p, res) ->
                    val locked = !state.isPro && p != PalettePlacement.NONE
                    FilterChip(
                        selected = state.style.palette == p,
                        onClick = { if (locked) onUpgrade() else onStyleChange { it.copy(palette = p) } },
                        leadingIcon = if (locked) {
                            { Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        label = { Text(stringResource(res)) },
                    )
                }
            }
            // OVERLAY fine-tuning: orientation, position along the axis, band width.
            if (state.isPro && state.style.palette == PalettePlacement.OVERLAY) {
                Spacer(Modifier.size(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !state.style.overlayHorizontal,
                        onClick = { onStyleChange { it.copy(overlayHorizontal = false) } },
                        label = { Text(stringResource(R.string.collage_overlay_vertical)) },
                    )
                    FilterChip(
                        selected = state.style.overlayHorizontal,
                        onClick = { onStyleChange { it.copy(overlayHorizontal = true) } },
                        label = { Text(stringResource(R.string.collage_overlay_horizontal)) },
                    )
                }
                StyleSlider(
                    label = stringResource(R.string.collage_overlay_pos),
                    value = state.style.overlayPosFrac * 100f,
                    range = 0f..100f,
                    onChange = { v -> onStyleChange { it.copy(overlayPosFrac = v / 100f) } },
                )
                StyleSlider(
                    label = stringResource(R.string.collage_overlay_size),
                    value = state.style.overlayWidthFrac * 100f,
                    range = 8f..50f,
                    onChange = { v -> onStyleChange { it.copy(overlayWidthFrac = v / 100f) } },
                )
            }
            Spacer(Modifier.size(10.dp))

            // Pro: on-photo dot + HEX chips (like the Hunt thumbnails).
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.collage_hex_overlay),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                if (state.isPro) {
                    Switch(
                        checked = state.style.hexOverlay,
                        onCheckedChange = { on -> onStyleChange { it.copy(hexOverlay = on) } },
                    )
                } else {
                    ProPill(onClick = onUpgrade)
                }
            }
            Spacer(Modifier.size(10.dp))
            AssistChip(
                onClick = onHueSort,
                leadingIcon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp)) },
                label = { Text(stringResource(R.string.collage_hue_sort)) },
            )
        }
        Spacer(Modifier.size(12.dp))

        // Card 3 — fine styling: spacing, corners, border, background.
        ControlCard {
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
            Spacer(Modifier.size(10.dp))

            SectionLabel(stringResource(R.string.collage_border_color))
            SwatchRow(selected = state.style.borderColor) { c -> onStyleChange { it.copy(borderColor = c) } }
            Spacer(Modifier.size(14.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel(stringResource(R.string.collage_background))
                Spacer(Modifier.weight(1f))
                Text(stringResource(R.string.collage_bg_follow_theme), style = MaterialTheme.typography.labelMedium)
                Switch(
                    checked = state.style.backgroundFollowsTheme,
                    onCheckedChange = { on -> onStyleChange { it.copy(backgroundFollowsTheme = on) } },
                )
            }
            if (!state.style.backgroundFollowsTheme) {
                SwatchRow(selected = state.style.backgroundColor) { c -> onStyleChange { it.copy(backgroundColor = c) } }
            }
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

    editTarget?.let { (index, ratio) ->
        val photo = state.orderedPhotos.getOrNull(index)
        if (photo == null) {
            editTarget = null
        } else {
            CropEditorDialog(
                photo = photo,
                cellRatio = ratio,
                initial = state.focals[photo.id] ?: FocalPoint(),
                onConfirm = { focal ->
                    onSetFocal(index, focal)
                    editTarget = null
                },
                onDismiss = { editTarget = null },
            )
        }
    }
}

/**
 * WYSIWYG collage preview with an interactive drag layer. Long-press a cell and
 * drag it onto another to swap their order — the grid geometry mirrors
 * [CollageRenderer] exactly so the hit-zones line up with what's drawn.
 */
@Composable
private fun PreviewArea(
    state: CollageUiState,
    adjustMode: Boolean,
    onMove: (Int, Int) -> Unit,
    onEditCell: (index: Int, cellRatio: Float) -> Unit,
) {
    val cellCount = state.cellCount
    val placement = if (state.isPro) state.style.palette else PalettePlacement.NONE
    val spacingFrac = state.style.cellSpacingDp / 360f
    val filled = minOf(cellCount, state.orderedPhotos.size)

    var dragFrom by remember { mutableStateOf<Int?>(null) }
    var dragTo by remember { mutableStateOf<Int?>(null) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }

    // Cap the preview height so tall ratios (9:16) don't fill the whole screen —
    // for those the box shrinks in width and stays centered.
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val ratio = state.size.aspectRatio
        val previewCap = 440.dp
        val sizeMod = if (maxWidth / ratio > previewCap) {
            Modifier.height(previewCap).aspectRatio(ratio)
        } else {
            Modifier.fillMaxWidth().aspectRatio(ratio)
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
    BoxWithConstraints(
        sizeMod
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }

        // Same maths the renderer uses, so hit-zones line up with the drawn cells.
        val geo = CollageGeometry.compute(
            cellCount = cellCount,
            layout = state.style.layout,
            placement = placement,
            spacingFrac = spacingFrac,
            width = wPx,
            height = hPx,
            overlayHorizontal = state.style.overlayHorizontal,
            overlayPosFrac = state.style.overlayPosFrac,
            overlayWidthFrac = state.style.overlayWidthFrac,
        )
        val cells = geo.cells
        fun cellAt(p: Offset): Int? {
            for (i in 0 until minOf(filled, cells.size)) {
                val r = cells[i]
                if (p.x in r.left..r.right && p.y in r.top..r.bottom) return i
            }
            return null
        }

        val preview = state.preview
        if (preview != null) {
            androidx.compose.foundation.Image(
                bitmap = preview.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Drag layer: transparent, sits over the rendered preview.
        if (adjustMode) {
            // Tap a cell to open the dedicated crop editor (pan + pinch-zoom).
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(cellCount, state.style.layout, placement, spacingFrac, wPx, hPx, filled) {
                        detectTapGestures { offset ->
                            cellAt(offset)?.let { i ->
                                if (i < cells.size) onEditCell(i, cells[i].width / cells[i].height)
                            }
                        }
                    },
            )
        } else if (filled > 1) {
            // Long-press to rearrange cells.
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(cellCount, state.style.layout, placement, spacingFrac, wPx, hPx, filled) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { offset ->
                                dragFrom = cellAt(offset)
                                dragPos = offset
                                dragTo = dragFrom
                            },
                            onDrag = { change, _ ->
                                dragPos = change.position
                                cellAt(change.position)?.let { dragTo = it }
                            },
                            onDragEnd = {
                                val from = dragFrom; val to = dragTo
                                if (from != null && to != null && from != to) onMove(from, to)
                                dragFrom = null; dragTo = null
                            },
                            onDragCancel = { dragFrom = null; dragTo = null },
                        )
                    },
            )
        }

        // Highlight the drop target while dragging.
        val to = dragTo
        if (dragFrom != null && to != null && to < cells.size) {
            val r = cells[to]
            Box(
                Modifier
                    .offset { IntOffset(r.left.toInt(), r.top.toInt()) }
                    .size(with(density) { r.width.toDp() }, with(density) { r.height.toDp() })
                    .border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp)),
            )
        }

        // Floating thumbnail that follows the finger.
        val from = dragFrom
        if (from != null && from < state.orderedPhotos.size && from < cells.size) {
            val thumb = cells[from].width * 0.9f
            val thumbDp = with(density) { thumb.toDp() }
            AsyncImage(
                model = state.orderedPhotos[from].uri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .offset { IntOffset((dragPos.x - thumb / 2f).toInt(), (dragPos.y - thumb / 2f).toInt()) }
                    .size(thumbDp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(2.dp, Color.White, RoundedCornerShape(8.dp)),
            )
        }

        // Spinner only before the first frame — flashing it on every crop-pan
        // re-render would make the adjustment feel janky.
        if (state.loading || (state.rendering && state.preview == null)) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
    }
        }
    }
}

/**
 * Dedicated crop editor: the cell's frame at its real aspect ratio, drag to pan
 * and pinch to zoom, WYSIWYG with the renderer's crop maths. Commits only on
 * 完了 so backing out never mangles the collage.
 */
@Composable
private fun CropEditorDialog(
    photo: HuntPhoto,
    cellRatio: Float,
    initial: FocalPoint,
    onConfirm: (FocalPoint) -> Unit,
    onDismiss: () -> Unit,
) {
    var scale by remember { mutableStateOf(initial.scale.coerceIn(1f, FocalPoint.MAX_SCALE)) }
    var focalX by remember { mutableStateOf(initial.x) }
    var focalY by remember { mutableStateOf(initial.y) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
        ) {
            Text(
                stringResource(R.string.collage_crop_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.collage_crop_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(12.dp))

            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(cellRatio)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black),
            ) {
                val density = LocalDensity.current
                val boxW = with(density) { maxWidth.toPx() }
                val boxH = boxW / cellRatio
                val painter = rememberAsyncImagePainter(model = photo.uri)
                val intrinsic = painter.intrinsicSize
                val imgRatio =
                    if (intrinsic.isSpecified && intrinsic.height > 0f) intrinsic.width / intrinsic.height
                    else cellRatio
                // Base fill size at zoom 1, then zoom scales it up.
                val baseW: Float
                val baseH: Float
                if (imgRatio > cellRatio) {
                    baseH = boxH
                    baseW = boxH * imgRatio
                } else {
                    baseW = boxW
                    baseH = boxW / imgRatio
                }
                val dispW = baseW * scale
                val dispH = baseH * scale
                val overX = (dispW - boxW).coerceAtLeast(0f)
                val overY = (dispH - boxH).coerceAtLeast(0f)
                val ox = (0.5f - focalX) * overX
                val oy = (0.5f - focalY) * overY

                androidx.compose.foundation.Image(
                    painter = painter,
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(with(density) { dispW.toDp() }, with(density) { dispH.toDp() })
                        .graphicsLayer {
                            translationX = ox
                            translationY = oy
                        },
                )

                Box(
                    Modifier
                        .matchParentSize()
                        .pointerInput(imgRatio, boxW, boxH) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                // Recompute from live state — the gesture coroutine
                                // outlives recompositions.
                                val curScale = scale
                                val curOverX = (baseW * curScale - boxW).coerceAtLeast(0f)
                                val curOverY = (baseH * curScale - boxH).coerceAtLeast(0f)
                                val newScale = (curScale * zoom).coerceIn(1f, FocalPoint.MAX_SCALE)
                                val nOverX = (baseW * newScale - boxW).coerceAtLeast(0f)
                                val nOverY = (baseH * newScale - boxH).coerceAtLeast(0f)
                                val nOx = ((0.5f - focalX) * curOverX + pan.x).coerceIn(-nOverX / 2f, nOverX / 2f)
                                val nOy = ((0.5f - focalY) * curOverY + pan.y).coerceIn(-nOverY / 2f, nOverY / 2f)
                                focalX = if (nOverX > 0f) 0.5f - nOx / nOverX else 0.5f
                                focalY = if (nOverY > 0f) 0.5f - nOy / nOverY else 0.5f
                                scale = newScale
                            }
                        },
                )
            }
            Spacer(Modifier.size(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { scale = 1f; focalX = 0.5f; focalY = 0.5f }) {
                    Text(stringResource(R.string.collage_crop_reset))
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                Spacer(Modifier.width(4.dp))
                Button(onClick = { onConfirm(FocalPoint(focalX, focalY, scale)) }) {
                    Text(stringResource(R.string.collage_crop_done))
                }
            }
        }
    }
}

/** Segmented toggle above the preview: rearrange cells vs. adjust each crop. */
@Composable
private fun PreviewModeToggle(adjustMode: Boolean, onChange: (Boolean) -> Unit) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SegItem(stringResource(R.string.collage_mode_reorder), selected = !adjustMode, Modifier.weight(1f)) { onChange(false) }
            SegItem(stringResource(R.string.collage_mode_crop), selected = adjustMode, Modifier.weight(1f)) { onChange(true) }
        }
        if (adjustMode) {
            Spacer(Modifier.size(6.dp))
            Text(
                stringResource(R.string.collage_mode_crop_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SegItem(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun templateLabel(id: String): String = stringResource(
    when (id) {
        "white" -> R.string.collage_template_white
        "film" -> R.string.collage_template_film
        "kumisha" -> R.string.collage_template_kumisha
        "magazine" -> R.string.collage_template_magazine
        else -> R.string.collage_template_seamless
    },
)

/** A rounded surface that groups related controls — the Hunt tab's card look. */
@Composable
private fun ControlCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        content = content,
    )
}

/** Small "Pro" pill used to flag pay-gated controls. */
@Composable
private fun ProPill(onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(
            stringResource(R.string.collage_pro_locked),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

/**
 * A slim vivid-gradient header carrying the app's "映え" identity. Title and the
 * live photo count sit on one row with a tight hint underneath — compact, no
 * wasted vertical space.
 */
@Composable
private fun CollageHeader(photoCount: Int) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF7C4DFF), Color(0xFFEC407A), Color(0xFFFFA726)),
                ),
            )
            .statusBarsPadding()
            .padding(start = 20.dp, end = 16.dp, top = 8.dp, bottom = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.collage_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Text(
                    stringResource(R.string.collage_header_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.20f))
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            ) {
                Text(
                    stringResource(R.string.collage_photo_count, photoCount),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.12.em,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 6.dp),
    )
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
