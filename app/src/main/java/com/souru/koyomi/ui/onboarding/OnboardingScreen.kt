package com.souru.koyomi.ui.onboarding

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.souru.koyomi.KoyomiApplication
import com.souru.koyomi.R
import kotlinx.coroutines.launch

/**
 * Explains WHY calendar access is needed before the system dialog appears.
 * Whatever the user decides, we mark onboarding done and continue — the month
 * screen shows an empty calendar plus a settings link when access is missing.
 */
@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as KoyomiApplication }
    val scope = rememberCoroutineScope()

    fun finish() {
        scope.launch {
            app.container.settingsRepository.setOnboardingDone()
            onFinished()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { finish() }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.CalendarMonth,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.onboarding_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.onboarding_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(40.dp))
            Button(
                onClick = {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.READ_CALENDAR,
                            Manifest.permission.WRITE_CALENDAR,
                        ),
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text(stringResource(R.string.onboarding_grant))
            }
            TextButton(
                onClick = { finish() },
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text(stringResource(R.string.onboarding_skip))
            }
        }
    }
}
