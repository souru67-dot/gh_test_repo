package com.souru.colorhunt.ui.imports

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.remember
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
import com.souru.colorhunt.ui.common.composeColor
import com.souru.colorhunt.ui.common.label
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

    val picker = rememberLauncherForActivityResult(PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) viewModel.addPhotos(uris)
    }
    val launchPicker = { picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.importRecentDevicePhotos(context)
    }
    val autoSort = {
        val perm = readImagesPermission()
        if (ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED) {
            viewModel.importRecentDevicePhotos(context)
        } else {
            permissionLauncher.launch(perm)
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

            state.groups.forEach { group ->
                item(span = { GridItemSpan(maxLineSpan) }, key = "h_${group.bucket.name}") {
                    SectionHeader(group.bucket.composeColor(), group.bucket.label(), group.photos.size)
                }
                items(group.photos, key = { it.id }) { photo ->
                    PhotoThumb(photo, photo.id in state.selectedIds) { viewModel.toggleSelection(photo.id) }
                }
            }

            if (state.processing.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "h_proc") {
                    SectionHeader(MaterialTheme.colorScheme.secondary, stringResource(R.string.sort_processing_group), state.processing.size)
                }
                items(state.processing, key = { it.id }) { photo ->
                    PhotoThumb(photo, photo.id in state.selectedIds) { viewModel.toggleSelection(photo.id) }
                }
            }

            if (state.uncategorized.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "h_uncat") {
                    SectionHeader(MaterialTheme.colorScheme.outline, stringResource(R.string.sort_no_color_group), state.uncategorized.size)
                }
                items(state.uncategorized, key = { it.id }) { photo ->
                    PhotoThumb(photo, photo.id in state.selectedIds) { viewModel.toggleSelection(photo.id) }
                }
            }
        }
    }
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
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = fg)
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
private fun PhotoThumb(photo: HuntPhoto, selected: Boolean, onClick: () -> Unit) {
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
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = photo.uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().clip(shape),
        )

        if (photo.dominantColor != null) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color(photo.dominantColor))
                    .border(2.dp, Color.White.copy(alpha = 0.85f), CircleShape),
            )
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
