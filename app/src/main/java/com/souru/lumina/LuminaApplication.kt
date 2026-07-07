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
import com.souru.lumina.data.coil.ExternalThumb
import com.souru.lumina.data.coil.ExternalThumbFetcher
import com.souru.lumina.data.coil.ExternalThumbKeyer
import com.souru.lumina.data.coil.MediaThumb
import com.souru.lumina.data.coil.MediaThumbFetcher
import com.souru.lumina.data.coil.MediaThumbKeyer
import com.souru.lumina.data.luts.LutRepository

class AppContainer(context: Context) {
    val settingsRepository = SettingsRepository(context)
    val mediaRepository = MediaRepository(context)
    val lutRepository = LutRepository(context)
    val albumsSource: com.souru.lumina.data.albums.AlbumsSource =
        com.souru.lumina.data.albums.BucketAlbumsSource(mediaRepository)
    val videoEditSession = com.souru.lumina.data.video.VideoEditSession()
    val externalDeviceRepository =
        com.souru.lumina.data.external.ExternalDeviceRepository(context, settingsRepository)
}

class LuminaApplication : Application(), SingletonImageLoader.Factory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        installCrashLogger()
        container = AppContainer(this)
    }

    /**
     * クラッシュ時のスタックトレースを filesDir/crash.txt に書き出してから
     * 既定ハンドラへ渡す。実機での原因特定用:
     * adb shell run-as com.souru.lumina cat files/crash.txt
     */
    private fun installCrashLogger() {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                java.io.File(filesDir, "crash.txt").writeText(
                    "${java.util.Date()}\nthread=${thread.name}\n" +
                        android.util.Log.getStackTraceString(throwable),
                )
            }
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(MediaThumbKeyer(), MediaThumb::class)
                add(MediaThumbFetcher.Factory(this@LuminaApplication), MediaThumb::class)
                add(DngPreviewKeyer(), DngPreview::class)
                add(DngPreviewFetcher.Factory(this@LuminaApplication), DngPreview::class)
                add(ExternalThumbKeyer(), ExternalThumb::class)
                add(ExternalThumbFetcher.Factory(this@LuminaApplication), ExternalThumb::class)
            }
            .build()
}
