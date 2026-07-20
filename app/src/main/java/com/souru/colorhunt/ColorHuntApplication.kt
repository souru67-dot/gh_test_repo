package com.souru.colorhunt

import android.app.Application
import android.content.Context
import com.souru.colorhunt.domain.pro.ProState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import java.io.File

class ColorHuntApplication : Application() {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // osmdroid: identify politely to the tile server and keep caches app-private
        // (no storage permission needed).
        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = File(cacheDir, "osmdroid")
            osmdroidTileCache = File(osmdroidBasePath, "tiles")
        }

        // Pro entitlement: apply the cached value instantly (no watermark flash /
        // locked UI while offline), then let Play Billing confirm and re-persist.
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        ProState.update(prefs.getBoolean(KEY_IS_PRO, false))
        appScope.launch {
            ProState.isPro.collect { isPro ->
                prefs.edit().putBoolean(KEY_IS_PRO, isPro).apply()
            }
        }
        container.billingManager.connect()
    }

    private companion object {
        const val PREFS = "colorhunt_prefs"
        const val KEY_IS_PRO = "is_pro_cached"
    }
}
