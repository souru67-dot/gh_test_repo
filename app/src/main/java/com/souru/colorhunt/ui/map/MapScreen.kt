package com.souru.colorhunt.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.souru.colorhunt.R
import com.souru.colorhunt.data.export.ImageExporter
import com.souru.colorhunt.data.export.ShareHelper
import com.souru.colorhunt.domain.color.ColorBucket
import com.souru.colorhunt.domain.model.HuntPhoto
import com.souru.colorhunt.ui.common.composeColor
import com.souru.colorhunt.ui.common.label
import kotlinx.coroutines.launch
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

@Composable
fun MapScreen(
    modifier: Modifier = Modifier,
    viewModel: MapViewModel = viewModel(factory = MapViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val savedMsg = stringResource(R.string.collage_saved)
    val failMsg = stringResource(R.string.collage_share_failed)
    val shareTitle = stringResource(R.string.map_export)

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(4.0)
            controller.setCenter(GeoPoint(35.681, 139.767)) // sensible default
        }
    }
    LaunchedEffect(Unit) { mapView.onResume() }
    DisposableEffect(Unit) {
        onDispose {
            mapView.onPause()
            mapView.onDetach()
        }
    }
    // Re-plot only when the located set actually changes, so the user's pan/zoom
    // isn't reset on unrelated recompositions.
    LaunchedEffect(state.located) { refreshMarkers(mapView, context, state.located) }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (state.located.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = {
                        scope.launch {
                            val bmp = snapshot(mapView)
                            if (bmp == null) {
                                snackbar.showSnackbar(failMsg)
                                return@launch
                            }
                            val name = "ColorHunt_walk_" + System.currentTimeMillis()
                            ImageExporter.saveToGallery(context, bmp, name)
                            val uri = ShareHelper.cacheForShare(context, bmp, name)
                            if (uri != null) ShareHelper.shareSingle(context, uri, shareTitle)
                            snackbar.showSnackbar(savedMsg)
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    icon = { Icon(Icons.Filled.Share, contentDescription = null) },
                    text = { Text(stringResource(R.string.map_export)) },
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.availableFilters.isNotEmpty()) {
                MapFilterRow(state.availableFilters, state.activeFilter, viewModel::setFilter)
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(
                    factory = { mapView },
                    modifier = Modifier.fillMaxSize(),
                )
                if (state.located.isEmpty()) {
                    Column(
                        Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(Icons.Filled.Map, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.map_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (state.noLocation.isNotEmpty()) {
                NoLocationBar(state.noLocation)
            }
        }
    }
}

@Composable
private fun MapFilterRow(filters: List<ColorBucket>, active: ColorBucket?, onSelect: (ColorBucket?) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MapChip(null, stringResource(R.string.sort_filter_all), active == null) { onSelect(null) }
        filters.forEach { bucket ->
            MapChip(bucket.composeColor(), bucket.label(), active == bucket) {
                onSelect(if (active == bucket) null else bucket)
            }
        }
    }
}

@Composable
private fun MapChip(dot: Color?, label: String, selected: Boolean, onClick: () -> Unit) {
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
            Box(Modifier.size(14.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun NoLocationBar(photos: List<HuntPhoto>) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            stringResource(R.string.map_no_location) + "  (${photos.size})",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(photos, key = { it.id }) { photo ->
                Box(Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    AsyncImage(
                        model = photo.uri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (photo.dominantColor != null) {
                        Box(
                            Modifier.align(Alignment.BottomStart).padding(3.dp).size(10.dp)
                                .clip(CircleShape).background(Color(photo.dominantColor))
                                .border(1.dp, Color.White.copy(alpha = 0.8f), CircleShape),
                        )
                    }
                }
            }
        }
    }
}

private fun refreshMarkers(view: MapView, context: Context, located: List<HuntPhoto>) {
    view.overlays.clear()
    val points = ArrayList<GeoPoint>(located.size)
    located.forEach { photo ->
        val lat = photo.latitude ?: return@forEach
        val lon = photo.longitude ?: return@forEach
        val point = GeoPoint(lat, lon)
        points.add(point)
        val marker = Marker(view).apply {
            position = point
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            icon = pinDrawable(context, photo.dominantColor ?: AndroidColor.GRAY)
        }
        view.overlays.add(marker)
    }
    view.invalidate()

    if (points.isNotEmpty()) {
        view.post {
            if (points.size == 1) {
                view.controller.setZoom(15.0)
                view.controller.setCenter(points.first())
            } else {
                runCatching {
                    view.zoomToBoundingBox(BoundingBox.fromGeoPoints(points), false, 96)
                }
            }
        }
    }
}

private fun pinDrawable(context: Context, colorInt: Int): Drawable {
    val density = context.resources.displayMetrics.density
    val size = (22 * density).toInt()
    return GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(colorInt)
        setStroke((2 * density).toInt(), AndroidColor.WHITE)
        setSize(size, size)
    }
}

private fun snapshot(view: MapView): Bitmap? {
    if (view.width <= 0 || view.height <= 0) return null
    return try {
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        bmp
    } catch (t: Throwable) {
        null
    }
}
