package com.souru.lumina.util

import android.util.Log
import com.souru.lumina.BuildConfig

/**
 * LUT適用パイプラインの切り分け用デバッグログ(デバッグビルド限定)。
 * 実機では `adb logcat -s LutPipeline` で確認する。
 */
const val LUT_PIPELINE_TAG = "LutPipeline"

inline fun lutLog(message: () -> String) {
    if (BuildConfig.DEBUG) Log.d(LUT_PIPELINE_TAG, message())
}
