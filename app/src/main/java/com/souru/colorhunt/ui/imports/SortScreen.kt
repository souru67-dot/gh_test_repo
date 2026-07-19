package com.souru.colorhunt.ui.imports

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.souru.colorhunt.R
import com.souru.colorhunt.domain.color.ColorBucket
import com.souru.colorhunt.domain.model.AnalysisState
import com.souru.colorhunt.domain.model.HuntPhoto
import com.souru.colorhunt.ui.common.HueRing
import com.souru.colorhunt.ui.common.composeColor
import com.souru.colorhunt.ui.common.label
import com.souru.colorhunt.ui.common.toHexCode
import com.souru.colorhunt.ui.theme.BrandGradients

private fun readImagesPermission(): String =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
    else Manifest.permission.READ_EXTERNAL_STORAGE

@Composable
fun SortScreen(
    onOpenCollage: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SortViewModel = viewModel(factory = SortViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val deniedMsg = stringResource(R.string.sort_permission_denied)
    // Collapsed by default so the photo groups are visible without scrolling past the wheel.
    var wheelExpanded by rememberSaveable { mutableStateOf(false) }
    // Photo whose colour bucket the user is manually re-filing (long-press).
    var rebucketTarget by remember { mutableStateOf<HuntPhoto?>(null) }

    val picker = rememberLauncherForActivityResult(PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) viewModel.addPhotos(uris)
    }
    val launchPicker = { picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        // Import as long as image access was granted; media-location is a bonus for GPS.
        if (grants[readImagesPermission()] == true) viewModel.importRecentDevicePhotos(context)
    }
    val autoSort = {
        val readPerm = readImagesPermission()
        if (ContextCompat.checkSelfPermission(context, readPerm) == PackageManager.PERMISSION_GRANTED) {
            viewModel.importRecentDevicePhotos(context)
        } else {
            val perms = buildList {
                add(readPerm)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(Manifest.permission.ACCESS_MEDIA_LOCATION)
            }
            permissionLauncher.launch(perms.toTypedArray())
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (state.hasSelection) {
                ExtendedFloatingActionButton(
                    onClick = onOpenCollage,
                    containerColor = MaterialTheme.colorScheme.primary,
                    text = { Text(stringResource(R.string.sort_make_collage) + "  (${state.selectedIds.size})") },
                    icon = { Icon(Icons.Filled.CheckCircle, contentDescription = null) },
                )
            }
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 108.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 12.dp, end = 12.dp, top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "hero") {
                HeroHeader(
                    photoCount = state.totalCount,
                    colorCount = state.availableFilters.size,
                    onPick = launchPicker,
                    onAuto = autoSort,
                    onClear = { viewModel.clearAll() },
                    showClear = !state.isEmpty,
                )
            }

            if (state.availableFilters.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "filters") {
                    FilterRow(
                        filters = state.availableFilters,
                        active = state.activeFilter,
                        onSelect = viewModel::setFilter,
                    )
                }
            }

            if (state.isEmpty) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "empty") { EmptyHint() }
            }

            if (state.activeFilter == null) {
                val wheelColors = state.groups.flatMap { it.photos }.mapNotNull { it.dominantColor }
                if (wheelColors.size >= 3) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "wheel") {
                        HueRingCard(
                            colors = wheelColors,
                            colorCount = state.availableFilters.size,
                            expanded = wheelExpanded,
                            onToggle = { wheelExpanded = !wheelExpanded },
                        )
                    }
                }
            }

            if (!state.isEmpty) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "rebucket_hint") {
                    Text(
                        stringResource(R.string.sort_rebucket_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

            state.groups.forEach { group ->
                item(span = { GridItemSpan(maxLineSpan) }, key = "h_${group.bucket.name}") {
                    SectionHeader(group.bucket.composeColor(), group.bucket.label(), group.photos.size)
                }
                items(group.photos, key = { it.id }) { photo ->
                    PhotoThumb(
                        photo = photo,
                        selected = photo.id in state.selectedIds,
                        onLongClick = { rebucketTarget = photo },
                        onClick = { viewModel.toggleSelection(photo.id) },
                    )
                }
            }

            if (state.processing.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "h_proc") {
                    SectionHeader(MaterialTheme.colorScheme.secondary, stringResource(R.string.sort_processing_group), state.processing.size)
                }
                items(state.processing, key = { it.id }) { photo ->
                    PhotoThumb(
                        photo = photo,
                        selected = photo.id in state.selectedIds,
                        onClick = { viewModel.toggleSelection(photo.id) },
                    )
                }
            }

            if (state.uncategorized.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "h_uncat") {
                    SectionHeader(MaterialTheme.colorScheme.outline, stringResource(R.string.sort_no_color_group), state.uncategorized.size)
                }
                items(state.uncategorized, key = { it.id }) { photo ->
                    PhotoThumb(
                        photo = photo,
                        selected = photo.id in state.selectedIds,
                        onLongClick = { rebucketTarget = photo },
                        onClick = { viewModel.toggleSelection(photo.id) },
                    )
                }
            }
        }
    }

    rebucketTarget?.let { target ->
        RebucketDialog(
            photo = target,
            onPick = { bucket ->
                viewModel.reassignBucket(target.id, bucket)
                rebucketTarget = null
            },
            onDismiss = { rebucketTarget = null },
        )
    }
}

/**
 * Manual bucket override: auto-classification is a best guess, so the hunter can
 * re-file any photo into the colour they meant to collect.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun RebucketDialog(
    photo: HuntPhoto,
    onPick: (ColorBucket) -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sort_rebucket_title), fontWeight = FontWeight.Bold) },
        text = {
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ColorBucket.entries.forEach { bucket ->
                    val current = bucket == photo.bucket
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (current) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable { onPick(bucket) }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(14.dp).clip(CircleShape).background(bucket.composeColor())
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), CircleShape),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(bucket.label(), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun HeroHeader(
    photoCount: Int,
    colorCount: Int,
    onPick: () -> Unit,
    onAuto: () -> Unit,
    onClear: () -> Unit,
    showClear: Boolean,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(BrandGradients.hero)
            .padding(20.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "ColorHunt",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                )
                Spacer(Modifier.weight(1f))
                if (showClear) {
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.18f))
                            .clickable(onClick = onClear)
                            .padding(8.dp),
                    ) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = stringResource(R.string.sort_clear), tint = Color.White)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.sort_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
            )
            if (photoCount > 0) {
                Spacer(Modifier.height(10.dp))
                Row {
                    StatPill("$photoCount", stringResource(R.string.sort_stat_photos))
                    Spacer(Modifier.width(8.dp))
                    StatPill("$colorCount", stringResource(R.string.sort_stat_colors))
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HeroButton(
                    text = stringResource(R.string.sort_pick),
                    icon = Icons.Filled.PhotoLibrary,
                    filled = true,
                    modifier = Modifier.weight(1f),
                    onClick = onPick,
                )
                HeroButton(
                    text = stringResource(R.string.sort_auto),
                    icon = Icons.Filled.AutoAwesome,
                    filled = false,
                    modifier = Modifier.weight(1f),
                    onClick = onAuto,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.sort_auto_hint, RECENT_IMPORT_LIMIT),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.8f),
            )
        }
    }
}

@Composable
private fun StatPill(value: String, label: String) {
    Row(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f))
    }
}

@Composable
private fun HeroButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    filled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val bg = if (filled) Modifier.background(Color.White) else Modifier.background(Color.White.copy(alpha = 0.12f))
    val border = if (filled) Modifier else Modifier.border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
    val fg = if (filled) Color(0xFF6D28D9) else Color.White
    Row(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .then(bg)
            .then(border)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            maxLines = 1,
            softWrap = false,
            overflow = androidx.compose.ui.text.style.TextOverflow.Visible,
        )
    }
}

@Composable
private fun FilterRow(filters: List<ColorBucket>, active: ColorBucket?, onSelect: (ColorBucket?) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ColorFilterChip(null, stringResource(R.string.sort_filter_all), active == null) { onSelect(null) }
        filters.forEach { bucket ->
            ColorFilterChip(bucket.composeColor(), bucket.label(), active == bucket) {
                onSelect(if (active == bucket) null else bucket)
            }
        }
    }
}

@Composable
private fun ColorFilterChip(dot: Color?, label: String, selected: Boolean, onClick: () -> Unit) {
    val container = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surface
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(container)
            .border(1.dp, borderColor, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) {
            Box(Modifier.size(14.dp).clip(CircleShape).background(dot).border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
    }
}

/**
 * The collected-colours wheel, collapsed by default so it never buries the photo
 * groups. Tap the header to reveal the ring; a compact swatch strip hints at the
 * palette while collapsed.
 */
@Composable
private fun HueRingCard(colors: List<Int>, colorCount: Int, expanded: Boolean, onToggle: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onToggle)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.sort_wheel_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(R.string.sort_wheel_sub, colorCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!expanded) {
                MiniSwatches(colors)
                Spacer(Modifier.width(10.dp))
            }
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.size(14.dp))
                HueRing(colors = colors, modifier = Modifier.size(180.dp))
            }
        }
    }
}

/** A tiny row of representative swatches shown while the wheel is collapsed. */
@Composable
private fun MiniSwatches(colors: List<Int>) {
    Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
        colors.take(5).forEach { c ->
            Box(
                Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color(c))
                    .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
            )
        }
    }
}

@Composable
private fun SectionHeader(color: Color, title: String, count: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(color, color.copy(alpha = 0.5f)))),
        )
        Spacer(Modifier.width(10.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        Text(
            "$count",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun EmptyHint() {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.sort_empty_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.sort_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun PhotoThumb(
    photo: HuntPhoto,
    selected: Boolean,
    onLongClick: () -> Unit = {},
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(
                if (selected) Modifier.border(3.dp, BrandGradients.cta, shape)
                else Modifier.border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), shape)
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        AsyncImage(
            model = photo.uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().clip(shape),
        )

        if (photo.dominantColor != null) {
            Row(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(start = 4.dp, end = 7.dp, top = 3.dp, bottom = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(12.dp).clip(CircleShape).background(Color(photo.dominantColor))
                        .border(1.dp, Color.White.copy(alpha = 0.8f), CircleShape),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    photo.dominantColor.toHexCode(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    maxLines = 1,
                )
            }
        } else if (photo.analysis == AnalysisState.Pending) {
            CircularProgressIndicator(
                Modifier.align(Alignment.BottomStart).padding(6.dp).size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.secondary,
            )
        }

        if (selected) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}
