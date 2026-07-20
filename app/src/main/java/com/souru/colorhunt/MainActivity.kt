package com.souru.colorhunt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.souru.colorhunt.ui.ColorHuntApp
import com.souru.colorhunt.ui.onboarding.WelcomeGate
import com.souru.colorhunt.ui.theme.ColorHuntTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ColorHuntTheme {
                // Ask for photo/location/notification access on first launch,
                // then show the app.
                WelcomeGate {
                    ColorHuntApp()
                }
            }
        }
    }
}
