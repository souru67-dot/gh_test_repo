package com.souru.lumina

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.souru.lumina.data.MediaRepository
import com.souru.lumina.data.SettingsRepository
import com.souru.lumina.data.coil.DngPreview
import com.souru.lumina.data.coil.DngPreviewFetcher
import com.souru.lumina.data.coil.DngPreviewKeyer
import com.souru.lumina.data.coil.MediaThumb
import com.souru.lumina.data.coil.MediaThumbFetcher
import com.souru.lumina.data.coil.MediaThumbKeyer

class AppContainer(context: Context) {
    val settingsRepository = SettingsRepository(context)
    val mediaRepository = MediaRepository(context)
}

class LuminaApplication : Application(), SingletonImageLoader.Factory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(MediaThumbKeyer(), MediaThumb::class)
                add(MediaThumbFetcher.Factory(this@LuminaApplication), MediaThumb::class)
                add(DngPreviewKeyer(), DngPreview::class)
                add(DngPreviewFetcher.Factory(this@LuminaApplication), DngPreview::class)
            }
            .build()
}
