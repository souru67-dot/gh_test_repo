package com.souru.colorhunt.ui.roulette

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.souru.colorhunt.R
import com.souru.colorhunt.domain.color.Hsv
import com.souru.colorhunt.ui.common.label
import com.souru.colorhunt.ui.common.toHexCode
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

@Composable
fun RouletteScreen(
    onOpenSort: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RouletteViewModel = viewModel(factory = RouletteViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current

    var hue by remember { mutableFloatStateOf(300f) }
    var sat by remember { mutableFloatStateOf(0.7f) }

    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) viewModel.setReminder(true) }
    val onToggleReminder: (Boolean) -> Unit = { enabled ->
        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.setReminder(enabled)
        }
    }

    Scaffold(modifier = modifier, containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.roulette_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            Text(
                stringResource(R.string.roulette_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))

            ColorWheel(
                hue = hue,
                sat = sat,
                thumbColor = Color.hsv(hue, sat.coerceIn(0f, 1f), 1f),
                onPick = { h, s ->
                    hue = h; sat = s
                    viewModel.select(Color.hsv(h, s.coerceIn(0f, 1f), 1f).toArgb())
                },
                modifier = Modifier.fillMaxWidth(0.82f).aspectRatio(1f),
            )
            Spacer(Modifier.height(18.dp))

            Button(onClick = {
                val color = viewModel.pickToday()
                val hsv = Hsv.fromColorInt(color)
                hue = hsv.hue
                sat = hsv.saturation.coerceIn(0.2f, 1f)
            }) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.roulette_auto))
            }
            Spacer(Modifier.height(18.dp))

            val selected = state.selectedColor
            if (selected != null) {
                ResultCard(
                    color = Color(selected),
                    caption = if (state.isToday) stringResource(R.string.roulette_today) else stringResource(R.string.roulette_chosen),
                    bucketName = state.bucket?.label() ?: "",
                    hex = selected.toHexCode(),
                    onHunt = onOpenSort,
                )
            } else {
                Text(
                    stringResource(R.string.roulette_pick_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(24.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.roulette_reminder), Modifier.weight(1f))
                Switch(checked = state.reminderEnabled, onCheckedChange = onToggleReminder)
            }
        }
    }
}

@Composable
private fun ColorWheel(
    hue: Float,
    sat: Float,
    thumbColor: Color,
    onPick: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rainbow = remember { (0..12).map { Color.hsv((it * 30f) % 360f, 1f, 1f) } }
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val diameterPx = with(LocalDensity.current) { maxWidth.toPx() }
        val radius = diameterPx / 2f
        val center = Offset(radius, radius)

        fun emit(pos: Offset) {
            val dx = pos.x - center.x
            val dy = pos.y - center.y
            val s = (hypot(dx, dy) / radius).coerceIn(0f, 1f)
            var h = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
            if (h < 0f) h += 360f
            onPick(h, s)
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { emit(it) } }
                .pointerInput(Unit) { detectDragGestures { change, _ -> emit(change.position) } },
        ) {
            drawCircle(Brush.sweepGradient(rainbow, center), radius = radius, center = center)
            drawCircle(Brush.radialGradient(listOf(Color.White, Color.Transparent), center, radius), radius = radius, center = center)

            val angle = Math.toRadians(hue.toDouble())
            val rr = sat.coerceIn(0f, 1f) * radius
            val thumb = Offset(center.x + (cos(angle) * rr).toFloat(), center.y + (sin(angle) * rr).toFloat())
            drawCircle(Color.White, radius = radius * 0.065f + 3f, center = thumb)
            drawCircle(thumbColor, radius = radius * 0.065f, center = thumb)
        }
    }
}

@Composable
private fun ResultCard(color: Color, caption: String, bucketName: String, hex: String, onHunt: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(caption, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.layout.Box(
                Modifier.size(48.dp).clip(CircleShape).background(color).border(2.dp, MaterialTheme.colorScheme.outline, CircleShape),
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text(bucketName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
                Text(hex, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onHunt) {
            Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.roulette_hunt))
        }
    }
}
