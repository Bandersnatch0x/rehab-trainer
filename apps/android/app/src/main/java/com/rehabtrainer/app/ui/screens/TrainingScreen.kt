package com.rehabtrainer.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.rehabtrainer.app.R
import com.rehabtrainer.app.core.pose.PoseAnalyzer
import com.rehabtrainer.app.core.pose.PoseFrame
import com.rehabtrainer.app.ui.components.ElderlyButton
import com.rehabtrainer.app.ui.components.ElderlyButtonVariant
import com.rehabtrainer.app.ui.theme.LocalElderlyDimens
import java.util.concurrent.Executors

/**
 * 训练执行页：双播放架构 (D22) + 演示版本 Mock 规范 (D21)。
 *
 * 核心要求：
 * 1. 双播放同屏：教学视频示范区与患者实时摄像头捕获区并存；
 * 2. 布局自适应：手机竖屏上下各半，平板横屏左右分栏；
 * 3. 画面叠加元素：实时动作计数、RAS 节拍开关、脚部完整入镜提示（D17）浮动叠加于实时画面；
 * 4. 适老化安全底线：紧急停止按钮始终常驻可见且高度 >= 80dp；
 * 5. 演示版本标注：所有临床数值打上「演示数据」标识。
 */
@Composable
fun TrainingScreen(
    widthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
    exerciseCode: String = "",
    onEmergencyStop: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val configuration = LocalConfiguration.current
    val dimens = LocalElderlyDimens.current

    // 判断是否为横屏平板布局 (D22)
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE ||
        widthSizeClass == WindowWidthSizeClass.Expanded

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
            Text("需要摄像头权限以进行实时姿态与防颤动作识别", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))
            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                Text("授予摄像头权限")
            }
        }
        return
    }

    // 状态流（结合端上实时分析与 D21 演示数据）
    var armRaiseCount by remember { mutableIntStateOf(0) }
    var statusText by remember { mutableStateOf("正在寻找人体站位…") }
    var footVisible by remember { mutableStateOf(true) }
    // D17：步态与下肢类必须全景，上肢类可用半身构图。
    val requiresFullBody = exerciseCode in setOf("EX-RAS-01", "EX-STRENGTH-01", "EX-POSTURE-01")
    var metronomeEnabled by remember { mutableStateOf(true) }
    var isPaused by remember { mutableStateOf(false) }

    val analyzer = remember {
        PoseAnalyzer(context) { frame ->
            if (isPaused) return@PoseAnalyzer
            when (frame) {
                is PoseFrame.Detected -> {
                    armRaiseCount = frame.armRaiseCount
                    footVisible = frame.footVisibility >= 0.5f
                    statusText = when {
                        !requiresFullBody -> "上半身姿态识别正常"
                        footVisible -> "全身姿态识别正常"
                        else -> "脚部不在画面内"
                    }
                }
                PoseFrame.NoPose -> statusText = "未检测到人体，请站入画面中央"
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

    // 整体布局容器
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 双播放视口主体：根据屏幕形态选择上下布局还是左右分栏
        if (!isLandscape) {
            // 手机竖屏：上下布局，各占约一半高度 (D22)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 上半部：示范教学视频区 (Weight: 1f)
                InstructionalVideoPane(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    isPaused = isPaused
                )

                // 下半部：患者实时摄像头区 + 叠加层 (Weight: 1f)
                PatientRealtimeCameraPane(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    previewView = previewView,
                    armRaiseCount = armRaiseCount,
                    statusText = statusText,
                    footVisible = footVisible,
                    requiresFullBody = requiresFullBody,
                    metronomeEnabled = metronomeEnabled,
                    onToggleMetronome = { metronomeEnabled = it }
                )
            }
        } else {
            // 平板横屏：左右分栏，各占一半宽度 (D22)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 左侧栏：示范教学视频区 (Weight: 1f)
                InstructionalVideoPane(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    isPaused = isPaused
                )

                // 右侧栏：患者实时摄像头区 + 叠加层 (Weight: 1f)
                PatientRealtimeCameraPane(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    previewView = previewView,
                    armRaiseCount = armRaiseCount,
                    statusText = statusText,
                    footVisible = footVisible,
                    requiresFullBody = requiresFullBody,
                    metronomeEnabled = metronomeEnabled,
                    onToggleMetronome = { metronomeEnabled = it }
                )
            }
        }

        // 常驻操作底部安全控制栏：紧急停止始终常驻可见且高度 >= 80dp (D22 + 适老化底线)
        PersistentControlFooter(
            isPaused = isPaused,
            onTogglePause = { isPaused = !isPaused },
            onFinishSet = { /* 演示版：不写训练记录 */ },
            onEmergencyStop = onEmergencyStop
        )
    }
}

/**
 * 教学视频播放区（带示范进度条、节拍提示与 D21 演示数据标注）。
 */
@Composable
private fun InstructionalVideoPane(
    modifier: Modifier = Modifier,
    isPaused: Boolean
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0F172A),
            contentColor = Color.White
        ),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF334155))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 真实教学视频（随包内置的演示片段，循环播放）。
            AndroidView(
                factory = { context ->
                    VideoView(context).apply {
                        setVideoURI(
                            Uri.parse("android.resource://${context.packageName}/${R.raw.demo_exercise}")
                        )
                        setOnPreparedListener { player ->
                            player.isLooping = true
                            player.setVolume(0f, 0f)
                            if (!isPaused) start()
                        }
                    }
                },
                update = { view ->
                    if (isPaused) view.pause() else view.start()
                },
                modifier = Modifier.fillMaxSize()
            )

            // 叠层信息（标签、节拍、演示数据提示），不遮挡视频中心。
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // 顶部标签条
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "🎬 教学示范视频 【演示数据】",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    Surface(
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (isPaused) "已暂停" else "循环播放中",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isPaused) Color(0xFFFFB4AB) else Color(0xFF80D5CB),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // 中央动作图示与节奏指引
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(Color(0xFF1E293B), CircleShape)
                            .border(2.dp, Color(0xFF38BDF8), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "示范播放",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "下肢原地节律踏步 · 标准示范",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "建议幅度：屈髋 45° · 舒适节拍：64 BPM 【演示数据】",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8)
                    )
                }

                // 底部进度条
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "示范进度 01:24 / 03:00",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF94A3B8)
                        )
                        Text(
                            text = "李主任临床动作录制 【演示数据】",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF64748B)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { 0.46f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color(0xFF334155),
                    )
                }
            }
        }
    }
}

/**
 * 患者实时摄像头画面区：内嵌 CameraX 画面，叠加动作计数、节拍开关与脚部入镜提示 (D17 / D22)。
 */
@Composable
private fun PatientRealtimeCameraPane(
    modifier: Modifier = Modifier,
    previewView: PreviewView,
    armRaiseCount: Int,
    statusText: String,
    footVisible: Boolean,
    requiresFullBody: Boolean,
    metronomeEnabled: Boolean,
    onToggleMetronome: (Boolean) -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF0284C7))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 底层：CameraX 真实摄像头画面
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )

            // 叠加层 1：左上角 实时动作计数大字报 (等宽数字 + 演示数据标注)
            Surface(
                color = Color(0xDD0F172A),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8)),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Text(
                        text = "实时计数 【演示数据】",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8)
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "$armRaiseCount",
                            fontSize = 38.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF38BDF8),
                            lineHeight = 40.sp
                        )
                        Text(
                            text = " / 12 次",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
                        )
                    }
                }
            }

            // 叠加层 2：右上角 听觉节律 RAS 开关与闪烁状态
            Surface(
                color = Color(0xDD0F172A),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column {
                        Text(
                            text = "RAS 节拍",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF80D5CB)
                        )
                        Text(
                            text = "64 BPM",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    }
                    Switch(
                        checked = metronomeEnabled,
                        onCheckedChange = onToggleMetronome,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF006A63),
                            checkedTrackColor = Color(0xFF99EFE5)
                        )
                    )
                }
            }

            // 叠加层 3：底部 足部入镜提示与姿态状态条 (D17 硬性检查)
            Surface(
                color = if (requiresFullBody && !footVisible) Color(0xEEBA1A1A) else Color(0xDD0F172A),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (requiresFullBody && !footVisible) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = "脚部未入镜警告",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "⚠️ 脚部未入镜，请后退站入全身框（步态指标无法计算）",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(Color(0xFF80D5CB), CircleShape)
                        )
                        Text(
                            text = "$statusText · 患者实时端上画面",
                            color = Color(0xFFE2E8F0),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

/**
 * 常驻操作与安全控制底栏：紧急停止按钮始终常驻可见且高度 >= 80dp。
 */
@Composable
private fun PersistentControlFooter(
    isPaused: Boolean,
    onTogglePause: () -> Unit,
    onFinishSet: () -> Unit,
    onEmergencyStop: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 次级控制栏：暂停与完成本组
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ElderlyButton(
                    text = if (isPaused) "继续跟练" else "暂停",
                    onClick = onTogglePause,
                    variant = ElderlyButtonVariant.Secondary,
                    modifier = Modifier.weight(1f)
                )
                ElderlyButton(
                    text = "完成本组 (第 1/3 组)",
                    onClick = onFinishSet,
                    variant = ElderlyButtonVariant.Primary,
                    modifier = Modifier.weight(1.5f)
                )
            }

            // 核心安全红线：紧急停止按钮始终可见且高度 >= 80dp (适老化硬约束)
            ElderlyButton(
                text = "🛑 紧急停止训练",
                onClick = onEmergencyStop,
                variant = ElderlyButtonVariant.Danger,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 80.dp)
            )
        }
    }
}
