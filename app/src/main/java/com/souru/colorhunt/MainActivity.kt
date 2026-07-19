package com.souru.colorhunt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.souru.colorhunt.ui.ColorHuntApp
import com.souru.colorhunt.ui.theme.ColorHuntTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ColorHuntTheme {
                ColorHuntApp()
            }
        }
    }
}
