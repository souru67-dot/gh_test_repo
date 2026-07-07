package com.souru.lumina.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * システムバーを必ず表示状態に戻す。ビューア(没入表示でバーを隠す)から
 * 遷移してくる画面の先頭で呼ぶことで、バー非表示状態を引き継いで
 * インセット0でレイアウトされる事故を防ぐ。
 */
@Composable
fun EnsureSystemBarsVisible() {
    val view = LocalView.current
    LaunchedEffect(Unit) {
        view.context.findActivityContext()?.window?.let { window ->
            val controller = WindowCompat.getInsetsController(window, view)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}
