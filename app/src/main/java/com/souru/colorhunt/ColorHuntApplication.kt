package com.souru.colorhunt

import android.app.Application
import org.osmdroid.config.Configuration
import java.io.File

class ColorHuntApplication : Application() {
    lateinit var container: AppContainer
        private set

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
    }
}
