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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.souru.colorhunt.R
import com.souru.colorhunt.domain.roulette.DailyColorRoulette
import com.souru.colorhunt.ui.common.composeColor
import com.souru.colorhunt.ui.common.label
import kotlin.math.floor

@Composable
fun RouletteScreen(
    onOpenSort: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RouletteViewModel = viewModel(factory = RouletteViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

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

    val rotation = remember { Animatable(0f) }
    LaunchedEffect(state.spinToken) {
        val target = landingRotation(rotation.value, state.bucket)
        rotation.animateTo(target, tween(durationMillis = 1400, easing = FastOutSlowInEasing))
    }

    Scaffold(modifier = modifier, containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.roulette_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                stringResource(R.string.roulette_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))

            Box(contentAlignment = Alignment.Center) {
                Wheel(rotation = rotation.value, modifier = Modifier.fillMaxWidth(0.85f).aspectRatio(1f))
                CenterBadge(
                    color = state.bucket.composeColor(),
                    caption = if (state.isToday) stringResource(R.string.roulette_today) else stringResource(R.string.roulette_random),
                )
            }
            Spacer(Modifier.height(16.dp))

            Text(
                state.bucket.label(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = state.bucket.composeColor(),
            )
            Spacer(Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = viewModel::spin) {
                    Icon(Icons.Filled.Casino, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.roulette_spin))
                }
                OutlinedButton(onClick = viewModel::resetToday) {
                    Text(stringResource(R.string.roulette_reset_today))
                }
            }
            Spacer(Modifier.height(12.dp))

            OutlinedButton(onClick = onOpenSort) {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.roulette_hunt))
            }

            Spacer(Modifier.height(24.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
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
private fun Wheel(rotation: Float, modifier: Modifier = Modifier) {
    val holeColor = MaterialTheme.colorScheme.background
    val pointerColor = MaterialTheme.colorScheme.onBackground
    Canvas(modifier) {
        val choices = DailyColorRoulette.choices
        val n = choices.size
        val sweep = 360f / n
        choices.forEachIndexed { i, bucket ->
            drawArc(
                color = Color(bucket.swatch),
                startAngle = i * sweep + rotation,
                sweepAngle = sweep,
                useCenter = true,
            )
        }
        // Center hole.
        drawCircle(color = holeColor, radius = size.minDimension * 0.22f, center = center)
        // Top pointer.
        val cx = size.width / 2f
        val pointer = Path().apply {
            moveTo(cx - 16f, 0f)
            lineTo(cx + 16f, 0f)
            lineTo(cx, 34f)
            close()
        }
        drawPath(pointer, color = pointerColor)
    }
}

@Composable
private fun CenterBadge(color: Color, caption: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(color)
                .border(3.dp, MaterialTheme.colorScheme.background, CircleShape),
        )
        Spacer(Modifier.height(4.dp))
        Text(caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Rotation (deg) that lands [bucket]'s wheel segment under the top pointer, after ~4 turns. */
private fun landingRotation(current: Float, bucket: com.souru.colorhunt.domain.color.ColorBucket): Float {
    val n = DailyColorRoulette.choices.size
    val sweep = 360f / n
    val index = DailyColorRoulette.indexOf(bucket).coerceAtLeast(0)
    val segmentCenter = index * sweep + sweep / 2f
    val desiredMod = ((270f - segmentCenter) % 360f + 360f) % 360f
    var target = floor(current / 360f) * 360f + desiredMod
    val minAdvance = current + 4 * 360f
    while (target < minAdvance) target += 360f
    return target
}
