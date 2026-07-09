package com.souru.lumina

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.souru.lumina.ui.AppNavHost
import com.souru.lumina.ui.theme.LuminaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            LuminaTheme {
                AppNavHost()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 復帰のたびに購入照会(別端末購入・払い戻しの反映、Pending確定の拾い上げ)
        (application as? LuminaApplication)?.container?.entitlementRepository?.refresh()
    }
}
