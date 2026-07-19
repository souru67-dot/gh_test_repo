package com.souru.colorhunt.ui.roulette

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.souru.colorhunt.ui.common.label
import com.souru.colorhunt.ui.common.toHexCode
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private const val SPIN_TURNS = 4

@Composable
fun RouletteScreen(
    onOpenSort: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RouletteViewModel = viewModel(factory = RouletteViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    // The ring shows one hue at a time (angle); saturation is fixed vivid, like the app icon.
    val sat = 0.92f
    val hueAnim = remember { Animatable(210f) }
    var spinning by remember { mutableStateOf(false) }
    val displayHue = ((hueAnim.value % 360f) + 360f) % 360f

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
                hue = displayHue,
                centerColor = Color.hsv(displayHue, sat, 1f),
                spinning = spinning,
                onPick = { h ->
                    if (spinning) return@ColorWheel
                    scope.launch { hueAnim.snapTo(h) }
                    viewModel.select(Color.hsv(h, sat, 1f).toArgb(), fromSpin = false)
                },
                modifier = Modifier.fillMaxWidth(0.82f).aspectRatio(1f),
            )
            Spacer(Modifier.height(18.dp))

            Button(
                enabled = !spinning,
                onClick = {
                    if (spinning) return@Button
                    spinning = true
                    val targetHue = viewModel.randomSpinHue()
                    // Spin forward several turns, easing to a stop on the target hue.
                    val start = hueAnim.value
                    val landing = start - (start % 360f) + 360f * SPIN_TURNS + targetHue
                    scope.launch {
                        hueAnim.animateTo(
                            targetValue = landing,
                            animationSpec = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
                        )
                        viewModel.select(Color.hsv(targetHue, sat, 1f).toArgb(), fromSpin = true)
                        spinning = false
                    }
                },
            ) {
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

/**
 * A rainbow **ring** colour wheel echoing the app icon: pick a hue by angle, with
 * the chosen colour previewed in the hollow centre. The [spinning] flag drives a
 * subtle pulse so a roulette spin reads as "deciding".
 */
@Composable
private fun ColorWheel(
    hue: Float,
    centerColor: Color,
    spinning: Boolean,
    onPick: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rainbow = remember { (0..12).map { Color.hsv((it * 30f) % 360f, 1f, 1f) } }
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val diameterPx = with(LocalDensity.current) { maxWidth.toPx() }
        val radius = diameterPx / 2f
        val center = Offset(radius, radius)
        val ringWidth = radius * 0.24f
        val ringRadius = radius - ringWidth / 2f

        fun emit(pos: Offset) {
            val dx = pos.x - center.x
            val dy = pos.y - center.y
            var h = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
            if (h < 0f) h += 360f
            onPick(h)
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { emit(it) } }
                .pointerInput(Unit) { detectDragGestures { change, _ -> emit(change.position) } },
        ) {
            // Rainbow ring.
            drawCircle(
                brush = Brush.sweepGradient(rainbow, center),
                radius = ringRadius,
                center = center,
                style = Stroke(width = ringWidth),
            )
            // Selected-colour swatch filling the hollow centre.
            val centerR = ringRadius - ringWidth / 2f - radius * 0.06f
            drawCircle(centerColor, radius = centerR.coerceAtLeast(1f), center = center)

            // Marker riding the ring at the current hue.
            val angle = Math.toRadians(hue.toDouble())
            val marker = Offset(
                center.x + (cos(angle) * ringRadius).toFloat(),
                center.y + (sin(angle) * ringRadius).toFloat(),
            )
            val markerR = ringWidth * (if (spinning) 0.62f else 0.5f)
            drawCircle(Color.White, radius = markerR + 3f, center = marker)
            drawCircle(centerColor, radius = markerR, center = marker)
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
