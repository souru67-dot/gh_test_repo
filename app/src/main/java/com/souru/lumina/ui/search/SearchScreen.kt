package com.souru.lumina.ui.search

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.souru.lumina.data.coil.MediaThumb
import com.souru.lumina.data.model.MediaKind
import com.souru.lumina.ui.viewer.ViewerScreen
import com.souru.lumina.util.formatDuration

/**
 * ローカル検索画面。ファイル名 / アルバム名 / 年月(2024年5月 等)/
 * 形式(RAW・動画 等)をスペース区切りで AND 検索する。
 */
@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@Composable
fun SearchScreen(
    onClose: () -> Unit,
    viewModel: SearchViewModel = viewModel(factory = SearchViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val queryText by viewModel.queryText.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var viewerIndex by rememberSaveable { mutableStateOf<Int?>(null) }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "戻る",
                        tint = Color.White,
                    )
                }
                TextField(
                    value = queryText,
                    onValueChange = viewModel::setQuery,
                    placeholder = {
                        Text(
                            "ファイル名・アルバム・年月・形式",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = MaterialTheme.colorScheme.outline,
                        unfocusedIndicatorColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color.White,
                    ),
                    trailingIcon = {
                        if (queryText.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setQuery("") }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "クリア",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                )
            }

            when {
                state.idle -> {
                    // 検索前のヒント(検索例)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "検索の例",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            text = "DSC0012 / Camera / 2024年5月 / RAW / 動画\nスペース区切りで組み合わせ(例: 2024 RAW)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                state.results.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "「${state.query.trim()}」に一致する項目はありません",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                else -> {
                    Text(
                        text = "${state.results.size}件",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        itemsIndexed(state.results, key = { _, e -> e.id }) { index, entry ->
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clickable { viewerIndex = index },
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(
                                            MediaThumb(
                                                uri = entry.item.uri,
                                                id = entry.item.id,
                                                rotationDeg = if (entry.item.isRaw) {
                                                    entry.item.orientationDeg
                                                } else {
                                                    0
                                                },
                                            ),
                                        )
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = entry.item.displayName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.surfaceContainer),
                                )
                                if (entry.item.kind == MediaKind.VIDEO) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(6.dp),
                                    ) {
                                        Icon(
                                            Icons.Default.PlayCircle,
                                            contentDescription = "動画",
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp),
                                        )
                                        Spacer(Modifier.size(3.dp))
                                        Text(
                                            text = formatDuration(entry.item.durationMs),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        viewerIndex?.let { index ->
            if (state.results.isNotEmpty()) {
                ViewerScreen(
                    entries = state.results,
                    initialIndex = index.coerceIn(state.results.indices),
                    onClose = { viewerIndex = null },
                )
            } else {
                viewerIndex = null
            }
        }
    }
}
