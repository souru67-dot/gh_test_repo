package com.souru.colorhunt.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.souru.colorhunt.R

/**
 * First-launch gate: explains why ColorHunt wants photo/location/notification
 * access (Google recommends showing rationale before the system dialog), then
 * requests them all at once. Shown only until the user gets past it once.
 */
@Composable
fun WelcomeGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var done by remember { mutableStateOf(isOnboarded(context)) }

    if (done) {
        content()
        return
    }

    val permissions = remember { onboardingPermissions() }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { _ ->
        markOnboarded(context)
        done = true
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF161326), Color(0xFF0E0E12)),
                ),
            )
            .padding(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.sweepGradient(
                            (0..12).map { Color.hsv((it * 30f) % 360f, 0.85f, 1f) },
                        ),
                    ),
            )
            Spacer(Modifier.size(20.dp))
            Text(
                stringResource(R.string.onboard_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
            )
            Spacer(Modifier.size(8.dp))
            Text(
                stringResource(R.string.onboard_body),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
            )
            Spacer(Modifier.size(24.dp))

            Perk(Icons.Filled.Palette, stringResource(R.string.onboard_perk_photos))
            Perk(Icons.Filled.Place, stringResource(R.string.onboard_perk_location))
            Perk(Icons.Filled.AutoAwesome, stringResource(R.string.onboard_perk_notify))
            Spacer(Modifier.size(28.dp))

            Button(
                onClick = {
                    if (permissions.isEmpty()) {
                        markOnboarded(context); done = true
                    } else {
                        launcher.launch(permissions.toTypedArray())
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.onboard_start), fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = { markOnboarded(context); done = true }) {
                Text(stringResource(R.string.onboard_skip), color = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
private fun Perk(icon: ImageVector, text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f))
    }
}

private const val PREFS = "colorhunt_prefs"
private const val KEY_ONBOARDED = "onboarded_v1"

private fun isOnboarded(context: Context): Boolean =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ONBOARDED, false)

private fun markOnboarded(context: Context) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ONBOARDED, true).apply()
}

/** Photo (+ location for the map, notifications for the daily colour) permissions to request up front. */
private fun onboardingPermissions(): List<String> = buildList {
    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.READ_MEDIA_IMAGES)
    else add(Manifest.permission.READ_EXTERNAL_STORAGE)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(Manifest.permission.ACCESS_MEDIA_LOCATION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
}
