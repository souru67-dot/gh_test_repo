package com.souru.lumina

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.souru.lumina.data.MediaRepository
import com.souru.lumina.data.SettingsRepository
import com.souru.lumina.data.billing.BillingRepository
import com.souru.lumina.data.billing.EntitlementRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
    /** アプリ全体で共有する長寿命スコープ(課金の照会・権利同期などに使用)。 */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settingsRepository = SettingsRepository(context)
    val mediaRepository = MediaRepository(context)
    val lutRepository = LutRepository(context)
    val albumsSource: com.souru.lumina.data.albums.AlbumsSource =
        com.souru.lumina.data.albums.BucketAlbumsSource(mediaRepository)
    val videoEditSession = com.souru.lumina.data.video.VideoEditSession()
    val externalDeviceRepository =
        com.souru.lumina.data.external.ExternalDeviceRepository(context, settingsRepository)

    val billingRepository = BillingRepository(context)
    val entitlementRepository = EntitlementRepository(
        billing = billingRepository,
        settings = settingsRepository,
        scope = appScope,
        isDebugBuild = BuildConfig.DEBUG,
    )
}

class LuminaApplication : Application(), SingletonImageLoader.Factory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) enableStrictMode()
        installCrashLogger()
        container = AppContainer(this)
        // 起動時に課金接続と購入照会(復元)を開始する
        container.entitlementRepository.refresh()
    }

    /**
     * デバッグ時のみ StrictMode を有効化し、メインスレッドのI/O・ネットワーク・
     * リークをログに出す(penaltyDeathにはせず、既存挙動を壊さず可視化のみ)。
     */
    private fun enableStrictMode() {
        android.os.StrictMode.setThreadPolicy(
            android.os.StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .detectCustomSlowCalls()
                .penaltyLog()
                .build(),
        )
        android.os.StrictMode.setVmPolicy(
            android.os.StrictMode.VmPolicy.Builder()
                .detectLeakedClosableObjects()
                .detectLeakedSqlLiteObjects()
                .detectActivityLeaks()
                .penaltyLog()
                .build(),
        )
    }

    /** メモリ逼迫時に画像メモリキャッシュを解放して OOM を避ける。 */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            runCatching { coil3.SingletonImageLoader.get(this).memoryCache?.clear() }
        }
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
