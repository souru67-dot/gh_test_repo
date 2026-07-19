package com.souru.colorhunt.ui.grid

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.souru.colorhunt.R
import com.souru.colorhunt.domain.model.HuntPhoto
import com.souru.colorhunt.ui.theme.BrandGradients

private const val COLUMNS = 3

// Instagram's current feed grid uses 4:5 portrait tiles (width : height).
private const val CELL_ASPECT = 4f / 5f

@Composable
fun GridPreviewScreen(
    modifier: Modifier = Modifier,
    viewModel: GridPreviewViewModel = viewModel(factory = GridPreviewViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }

    Scaffold(modifier = modifier, containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ProfileBar(
                profile = state.profile,
                postCount = state.postCount,
                onEdit = { editing = true },
            )
            if (!state.isEmpty) {
                Text(
                    stringResource(R.string.grid_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
            ReorderableFeed(
                feed = state.feed,
                onMove = viewModel::move,
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (editing) {
        EditProfileDialog(
            initial = state.profile,
            onDismiss = { editing = false },
            onConfirm = { name, followers, following ->
                viewModel.updateProfile(name, followers, following)
                editing = false
            },
        )
    }
}

@Composable
private fun ProfileBar(profile: ProfileHeader, postCount: Int, onEdit: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(BrandGradients.hero),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                profile.name.take(1).uppercase(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Stat(postCount.toString(), stringResource(R.string.grid_posts))
                Stat(formatCount(profile.followers), stringResource(R.string.grid_followers))
                Stat(formatCount(profile.following), stringResource(R.string.grid_following))
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(profile.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = onEdit, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)) {
                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.grid_edit_profile), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 6.dp)) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ReorderableFeed(
    feed: List<HuntPhoto>,
    onMove: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (feed.isEmpty()) {
        GridEmptyState(modifier)
        return
    }

    val gridState = rememberLazyGridState()
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var pointer by remember { mutableStateOf(Offset.Zero) }

    fun itemInfoAt(pos: Offset): LazyGridItemInfo? =
        gridState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
            pos.x >= info.offset.x && pos.x <= info.offset.x + info.size.width &&
                pos.y >= info.offset.y && pos.y <= info.offset.y + info.size.height
        }

    LazyVerticalGrid(
        columns = GridCells.Fixed(COLUMNS),
        state = gridState,
        modifier = modifier
            .fillMaxSize()
            .pointerInput(feed.size) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        val idx = itemInfoAt(offset)?.index
                        if (idx != null && idx < feed.size) {
                            draggingIndex = idx
                            pointer = offset
                        }
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        val current = draggingIndex
                        if (current != null) {
                            pointer += amount
                            val target = itemInfoAt(pointer)?.index
                            if (target != null && target < feed.size && target != current) {
                                onMove(current, target)
                                draggingIndex = target
                            }
                        }
                    },
                    onDragEnd = { draggingIndex = null },
                    onDragCancel = { draggingIndex = null },
                )
            },
        contentPadding = PaddingValues(0.dp),
        horizontalArrangement = Arrangement.spacedBy(1.5.dp),
        verticalArrangement = Arrangement.spacedBy(1.5.dp),
    ) {
        items(
            count = feed.size,
            key = { i -> feed[i].id },
        ) { i ->
            val isDragging = draggingIndex == i
            FeedCell(
                photo = feed[i],
                modifier = Modifier.graphicsLayer {
                    if (isDragging) {
                        val info = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == i }
                        if (info != null) {
                            translationX = pointer.x - (info.offset.x + info.size.width / 2f)
                            translationY = pointer.y - (info.offset.y + info.size.height / 2f)
                        }
                        scaleX = 1.05f
                        scaleY = 1.05f
                        shadowElevation = 16f
                    }
                }.zIndex(if (isDragging) 1f else 0f),
            )
        }
    }
}

@Composable
private fun GridEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.grid_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FeedCell(photo: HuntPhoto, modifier: Modifier = Modifier) {
    Box(modifier.aspectRatio(CELL_ASPECT).background(MaterialTheme.colorScheme.surfaceVariant)) {
        AsyncImage(
            model = photo.uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (photo.dominantColor != null) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Color(photo.dominantColor))
                    .border(1.dp, Color.White.copy(alpha = 0.8f), CircleShape),
            )
        }
    }
}

@Composable
private fun EditProfileDialog(
    initial: ProfileHeader,
    onDismiss: () -> Unit,
    onConfirm: (String, Int, Int) -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var followers by remember { mutableStateOf(initial.followers.toString()) }
    var following by remember { mutableStateOf(initial.following.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.grid_edit_profile)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.grid_name_label)) },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = followers,
                    onValueChange = { followers = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.grid_followers)) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = following,
                    onValueChange = { following = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.grid_following)) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(name, followers.toIntOrNull() ?: 0, following.toIntOrNull() ?: 0)
            }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private fun formatCount(n: Int): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000f)
    n >= 1_000 -> "%.1fK".format(n / 1_000f)
    else -> n.toString()
}
