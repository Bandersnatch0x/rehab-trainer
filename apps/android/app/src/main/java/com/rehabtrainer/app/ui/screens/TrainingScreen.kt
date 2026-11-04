package com.rehabtrainer.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.rehabtrainer.app.core.pose.PoseAnalyzer
import com.rehabtrainer.app.core.pose.PoseFrame
import com.rehabtrainer.app.ui.components.ElderlyButton
import com.rehabtrainer.app.ui.components.ElderlyButtonVariant
import com.rehabtrainer.app.ui.theme.LocalElderlyDimens
import java.util.concurrent.Executors

/**
 * 训练：摄像头预览 + 实时上肢计数 + 采集条件提示。
 *
 * 当前只验证上肢计数（T6 实测阶段的端上管线）；步态指标需脚部入镜（D17）。
 */
@Composable
fun TrainingScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (!hasCameraPermission) {
        Column(
            modifier = modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("需要摄像头权限才能进行姿态识别", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))
            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                Text("授予权限")
            }
        }
        return
    }

    var armRaiseCount by remember { mutableIntStateOf(0) }
    var statusText by remember { mutableStateOf("正在寻找人体…") }
    var footVisible by remember { mutableStateOf(true) }

    val analyzer = remember {
        PoseAnalyzer(context) { frame ->
            when (frame) {
                is PoseFrame.Detected -> {
                    armRaiseCount = frame.armRaiseCount
                    footVisible = frame.footVisibility >= 0.5f
                    statusText = if (footVisible) "已检测到人体" else "脚部不在画面内"
                }

                PoseFrame.NoPose -> statusText = "未检测到人体，请站到画面中央"
                is PoseFrame.Failure -> statusText = frame.message
            }
        }
    }

    val executor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember { PreviewView(context) }

    DisposableEffect(Unit) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            val cameraProvider = providerFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(executor, analyzer)
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_FRONT_CAMERA,
                preview,
                analysis
            )
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            analyzer.close()
            executor.shutdown()
        }
    }

    var metronomeEnabled by remember { mutableStateOf(true) }
    val dimens = LocalElderlyDimens.current

    Column(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

            // Top Status Overlay
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "RAS 节拍: 64 BPM",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Switch(
                        checked = metronomeEnabled,
                        onCheckedChange = { metronomeEnabled = it }
                    )
                }
            }
        }

        // Action Count & Controls Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Column(
                modifier = Modifier.padding(dimens.screenPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "动作完成次数",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "$armRaiseCount 次",
                            style = MaterialTheme.typography.displayLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                if (!footVisible) {
                    Text(
                        text = "⚠️ 脚部未入镜，本次只能计数，无法计算步态指标（D17）",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(dimens.itemSpacing)
                ) {
                    ElderlyButton(
                        text = "暂停",
                        onClick = { /* 暂停动作 */ },
                        variant = ElderlyButtonVariant.Secondary,
                        modifier = Modifier.weight(1f)
                    )
                    ElderlyButton(
                        text = "完成本组",
                        onClick = { /* 提交本组完成并进入休息 */ },
                        variant = ElderlyButtonVariant.Primary,
                        modifier = Modifier.weight(2f)
                    )
                }

                ElderlyButton(
                    text = "🛑 紧急停止训练",
                    onClick = { /* 触发紧急安全停止与通知 */ },
                    variant = ElderlyButtonVariant.Danger
                )
            }
        }
    }
}
