package com.souru.koyomi

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.souru.koyomi.data.ThemeMode
import com.souru.koyomi.data.ThemePack
import com.souru.koyomi.ui.AppNavHost
import com.souru.koyomi.ui.theme.KoyomiTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    /** Epoch day passed by a widget tap; consumed by the month screen. */
    private val deepLinkEpochDay = MutableStateFlow<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        readDeepLink(intent)

        val settings = (application as KoyomiApplication).container.settingsRepository
        setContent {
            val themeMode by settings.themeMode
                .collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            val dynamicColor by settings.dynamicColor
                .collectAsStateWithLifecycle(initialValue = false)
            val themePack by settings.themePack
                .collectAsStateWithLifecycle(initialValue = ThemePack.SUMI)
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            val epochDay by deepLinkEpochDay.collectAsStateWithLifecycle()
            KoyomiTheme(
                darkTheme = darkTheme,
                dynamicColor = dynamicColor,
                themePack = themePack,
            ) {
                AppNavHost(
                    deepLinkEpochDay = epochDay,
                    onDeepLinkConsumed = { deepLinkEpochDay.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readDeepLink(intent)
    }

    private fun readDeepLink(intent: Intent?) {
        val epochDay = intent?.getLongExtra(EXTRA_EPOCH_DAY, -1L) ?: -1L
        if (epochDay >= 0) deepLinkEpochDay.value = epochDay
    }

    companion object {
        const val EXTRA_EPOCH_DAY = "com.souru.koyomi.extra.EPOCH_DAY"
    }
}
