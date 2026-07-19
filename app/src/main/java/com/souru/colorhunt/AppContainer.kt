package com.souru.colorhunt

import android.content.Context
import com.souru.colorhunt.data.PaletteExtractor
import com.souru.colorhunt.data.PhotoRepository

/**
 * Minimal manual DI container. Holds the process-wide singletons the ViewModels
 * depend on; created once in [ColorHuntApplication]. Keeping wiring here (rather
 * than a DI framework) keeps the MVP light and the graph obvious.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val paletteExtractor: PaletteExtractor by lazy { PaletteExtractor(appContext) }
    val photoRepository: PhotoRepository by lazy { PhotoRepository(paletteExtractor, appContext) }
}
