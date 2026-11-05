package com.rehabtrainer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import com.rehabtrainer.app.data.offline.MediaCache
import com.rehabtrainer.app.ui.RehabApp
import com.rehabtrainer.app.ui.theme.RehabTheme

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MediaCache.install(applicationContext)
        enableEdgeToEdge()
        setContent {
            RehabTheme {
                RehabApp(windowSizeClass = calculateWindowSizeClass(this))
            }
        }
    }
}
