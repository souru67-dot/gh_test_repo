package com.souru.koyomi.ui.premium

import android.app.Activity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.souru.koyomi.BuildConfig
import com.souru.koyomi.KoyomiApplication
import com.souru.koyomi.R
import kotlinx.coroutines.launch

/**
 * こよみ プレミアム paywall: what the one-time unlock includes, the Play
 * price, and a restore path. Debug builds get an entitlement toggle so both
 * sides of every gate can be tested without Play.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as KoyomiApplication }
    val billing = app.container.billingRepository
    val isPremium by billing.isPremium.collectAsStateWithLifecycle(initialValue = false)
    val details by billing.productDetails.collectAsStateWithLifecycle()
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.premium_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.close),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.premium_lead),
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(modifier = Modifier.height(16.dp))
            val features = listOf(
                R.string.premium_feature_koyomi,
                R.string.premium_feature_weather,
                R.string.premium_feature_themes,
                R.string.premium_feature_widgets,
                R.string.premium_feature_shortcuts,
                R.string.premium_feature_backup,
            )
            for (feature in features) {
                Row(modifier = Modifier.padding(vertical = 6.dp)) {
                    Text(
                        text = "◆",
                        color = MaterialTheme.colorScheme.tertiary,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(feature),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 10.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            if (isPremium) {
                Text(
                    text = stringResource(R.string.premium_owned),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                val price = details?.oneTimePurchaseOfferDetails?.formattedPrice
                Button(
                    onClick = { (context as? Activity)?.let { billing.purchase(it) } },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = if (price != null) {
                            stringResource(R.string.premium_buy_price, price)
                        } else {
                            stringResource(R.string.premium_buy)
                        },
                    )
                }
                TextButton(
                    onClick = { billing.refresh() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.premium_restore))
                }
                Text(
                    text = stringResource(R.string.premium_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (BuildConfig.DEBUG) {
                Spacer(modifier = Modifier.height(24.dp))
                TextButton(
                    onClick = {
                        scope.launch { billing.debugSetPremium(!isPremium) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("(デバッグ) プレミアム切替: 現在 ${if (isPremium) "ON" else "OFF"}")
                }
            }
        }
    }
}
