package com.souru.colorhunt.ui.imports

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.souru.colorhunt.R
import com.souru.colorhunt.domain.color.ColorBucket
import com.souru.colorhunt.domain.model.AnalysisState
import com.souru.colorhunt.domain.model.HuntPhoto
import com.souru.colorhunt.ui.common.ColorDot
import com.souru.colorhunt.ui.common.composeColor
import com.souru.colorhunt.ui.common.label

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SortScreen(
    onOpenCollage: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SortViewModel = viewModel(factory = SortViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val picker = rememberLauncherForActivityResult(PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) viewModel.addPhotos(uris)
    }
    val launchPicker = {
        picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sort_title)) },
                actions = {
                    if (!state.isEmpty) {
                        IconButton(onClick = viewModel::clearAll) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = stringResource(R.string.sort_clear))
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (state.hasSelection) {
                ExtendedFloatingActionButton(
                    onClick = onOpenCollage,
                    text = { Text(stringResource(R.string.sort_make_collage)) },
                    icon = { Icon(Icons.Filled.CheckCircle, contentDescription = null) },
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            AddBar(
                selectedCount = state.selectedIds.size,
                analyzingCount = state.analyzingCount,
                onAdd = launchPicker,
            )
            if (state.availableFilters.isNotEmpty()) {
                FilterRow(
                    filters = state.availableFilters,
                    active = state.activeFilter,
                    onSelect = viewModel::setFilter,
                )
            }
            if (state.isEmpty) {
                EmptyState(onAdd = launchPicker)
            } else {
                PhotoGrid(
                    state = state,
                    onToggle = viewModel::toggleSelection,
                )
            }
        }
    }
}

@Composable
private fun AddBar(selectedCount: Int, analyzingCount: Int, onAdd: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalButton(onClick = onAdd) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.sort_pick))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            if (selectedCount > 0) {
                Text(
                    stringResource(R.string.sort_selected_count, selectedCount),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            if (analyzingCount > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.sort_analyzing), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun FilterRow(
    filters: List<ColorBucket>,
    active: ColorBucket?,
    onSelect: (ColorBucket?) -> Unit,
) {
    LazyRow(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(
                selected = active == null,
                onClick = { onSelect(null) },
                label = { Text(stringResource(R.string.sort_filter_all)) },
            )
        }
        listItems(filters, key = { it.name }) { bucket ->
            FilterChip(
                selected = active == bucket,
                onClick = { onSelect(if (active == bucket) null else bucket) },
                leadingIcon = { ColorDot(bucket.composeColor(), size = 14.dp) },
                label = { Text(bucket.label()) },
            )
        }
    }
}

@Composable
private fun EmptyState(onAdd: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            stringResource(R.string.sort_empty_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.size(8.dp))
        Text(
            stringResource(R.string.sort_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(20.dp))
        FilledTonalButton(onClick = onAdd) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.sort_pick))
        }
    }
}

@Composable
private fun PhotoGrid(state: SortUiState, onToggle: (String) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 100.dp),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.groups.forEach { group ->
            item(span = { GridItemSpan(maxLineSpan) }, key = "h_${group.bucket.name}") {
                SectionHeader(
                    color = group.bucket.composeColor(),
                    title = group.bucket.label(),
                    count = group.photos.size,
                )
            }
            items(group.photos, key = { it.id }) { photo ->
                PhotoThumb(
                    photo = photo,
                    selected = photo.id in state.selectedIds,
                    onClick = { onToggle(photo.id) },
                )
            }
        }
        if (state.uncategorized.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "h_uncat") {
                SectionHeader(
                    color = MaterialTheme.colorScheme.outline,
                    title = stringResource(R.string.sort_no_color_group),
                    count = state.uncategorized.size,
                )
            }
            items(state.uncategorized, key = { it.id }) { photo ->
                PhotoThumb(
                    photo = photo,
                    selected = photo.id in state.selectedIds,
                    onClick = { onToggle(photo.id) },
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(color: Color, title: String, count: Int) {
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(14.dp).background(color, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.width(6.dp))
        Text("($count)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PhotoThumb(photo: HuntPhoto, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = photo.uri,
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant, shape)
                .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
                .padding(0.dp),
        )

        // Dominant colour indicator (bottom-left).
        if (photo.dominantColor != null) {
            ColorDot(
                Color(photo.dominantColor),
                Modifier.align(Alignment.BottomStart).padding(6.dp),
                size = 14.dp,
            )
        } else if (photo.analysis == AnalysisState.Pending) {
            CircularProgressIndicator(
                Modifier.align(Alignment.BottomStart).padding(6.dp).size(14.dp),
                strokeWidth = 2.dp,
            )
        }

        if (selected) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp)
                    .background(MaterialTheme.colorScheme.surface, CircleShape),
            )
        }
    }
}
