package com.rehabtrainer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import com.rehabtrainer.app.core.network.ApiContainer
import com.rehabtrainer.app.core.network.SharedPreferencesTokenStore
import com.rehabtrainer.app.data.offline.MediaCache
import com.rehabtrainer.app.core.training.ModelFingerprint
import com.rehabtrainer.app.data.session.SessionRepository
import com.rehabtrainer.app.data.session.AppData
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.rehabtrainer.app.ui.RehabApp
import com.rehabtrainer.app.ui.theme.RehabTheme

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MediaCache.install(applicationContext)
        val tokenStore = SharedPreferencesTokenStore(applicationContext)
        val api = ApiContainer(tokenStore = tokenStore)
        val sessionRepository = SessionRepository(api = api, context = applicationContext)
        AppData.install(applicationContext, api)
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { ModelFingerprint.sha256(applicationContext) }
            // 未绑定/已登出时补传只会得到 401，并把记录推进退避；有会话再补传。
            if (sessionRepository.hasSession()) AppData.flushPending()
        }
        enableEdgeToEdge()
        setContent {
            RehabTheme {
                RehabApp(
                    windowSizeClass = calculateWindowSizeClass(this),
                    sessionRepository = sessionRepository
                )
            }
        }
    }
}
