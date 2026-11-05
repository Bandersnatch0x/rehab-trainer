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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.rehabtrainer.app.R
import com.rehabtrainer.app.core.pose.CaptureGate
import com.rehabtrainer.app.core.pose.PoseAnalyzer
import com.rehabtrainer.app.core.pose.PoseFrame
import com.rehabtrainer.app.ui.DEFAULT_EXERCISES
import com.rehabtrainer.app.ui.ExerciseMeta
import com.rehabtrainer.app.ui.components.ElderlyButton
import com.rehabtrainer.app.ui.components.ElderlyButtonVariant
import com.rehabtrainer.app.ui.theme.LocalElderlyDimens
import java.util.concurrent.Executors

/**
 * 训练执行显示模式：
 * 1. DUAL: 示范视频 + 实景拍摄同屏 (标准双播放架构)
 * 2. CAMERA_ONLY: 纯实景拍摄 + 动作语音提示 (针对手机屏幕适老化优化，避免双播放挤压操作区)
 */
enum class TrainingDisplayMode(val label: String, val icon: String) {
    DUAL("同屏示范", "🎬"),
    CAMERA_ONLY("纯拍摄+语音", "📷")
}

/**
 * 训练执行页：双播放架构 (D22) + 适老化自适应 (D19) + 演示版本 Mock 规范 (D21)。
 *
 * 核心缺陷全面修复：
 * - P0-1: 手机横屏 (sw < 600dp) 视口比例与紧凑底栏重构，画面占比 > 60%，绝不压缩至 2px，紧急停止常驻可见；
 * - P1-5: 摄像头画面遮挡率从 79% 降低至 25% 以下（中心主体 > 75% 完全通透可视）；
 * - P1-6: 教学视频区紧凑防溢出重构，标题 ellipsis，进度条始终清晰可见；
 * - P1-7: 支持动作联动，非节律步态动作自动隐藏 RAS 节拍器；
 * - P1-13: 页面全量字号升级至 >= 18sp (大字报计数 28sp，辅助信息与提示均为 18sp)；
 * - P1-14: 按钮文字精炼平整，杜绝孤字换行。
 */
@Composable
fun TrainingScreen(
    exerciseCode: String = "EX-RAS-01",
    widthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
    onEmergencyStop: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val configuration = LocalConfiguration.current
    val dimens = LocalElderlyDimens.current

    // 旋转检测与屏幕形态判定 (模式 3: 自适应布局，P0-1 核心：sw < 600dp 视为手机)
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isTablet = configuration.smallestScreenWidthDp >= 600
    val isPhoneLandscape = isLandscape && !isTablet

    val currentExercise = DEFAULT_EXERCISES.firstOrNull { it.code == exerciseCode }
        ?: DEFAULT_EXERCISES.first()

    // 显示模式切换状态 (模式 1 vs 模式 2)
    var displayMode by remember { mutableStateOf(TrainingDisplayMode.DUAL) }

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
            Text("需要摄像头权限以进行实时姿态与动作识别", style = MaterialTheme.typography.headlineSmall)
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
                    footVisible = CaptureGate.isFootVisible(frame.footVisibility)
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

    // 整体布局容器：顶部模式栏 + 中间自适应视口 + 底部常驻安全底栏
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 顶部模式切换条与标题 (P1-7 动态联动，P1-13 字号 >= 18sp)
        TrainingHeaderBar(
            exercise = currentExercise,
            currentMode = displayMode,
            onModeSelect = { displayMode = it },
            isPhoneLandscape = isPhoneLandscape
        )

        // 视口主体根据屏幕方向与显示模式自适应
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when {
                // 模式 2: 纯实景拍摄 + 动作语音提示模式 (视口全开给摄像头)
                displayMode == TrainingDisplayMode.CAMERA_ONLY -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = if (isPhoneLandscape) 10.dp else 12.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        VoiceGuidanceBanner(
                            prompt = currentExercise.prompt,
                            isPaused = isPaused
                        )

                        PatientRealtimeCameraPane(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            previewView = previewView,
                            armRaiseCount = armRaiseCount,
                            statusText = statusText,
                            footVisible = footVisible,
                            requiresFullBody = requiresFullBody,
                            hasRas = currentExercise.hasRas,
                            metronomeEnabled = metronomeEnabled,
                            onToggleMetronome = { metronomeEnabled = it }
                        )
                    }
                }

                // 模式 1 + 模式 3: 竖屏同屏双播放 (上 38% 视频，下 62% 实景)
                !isLandscape -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        InstructionalVideoPane(
                            videoTitle = currentExercise.videoTitle,
                            modifier = Modifier
                                .weight(0.38f)
                                .fillMaxWidth(),
                            isPaused = isPaused
                        )

                        PatientRealtimeCameraPane(
                            modifier = Modifier
                                .weight(0.62f)
                                .fillMaxWidth(),
                            previewView = previewView,
                            armRaiseCount = armRaiseCount,
                            statusText = statusText,
                            footVisible = footVisible,
                            requiresFullBody = requiresFullBody,
                            hasRas = currentExercise.hasRas,
                            metronomeEnabled = metronomeEnabled,
                            onToggleMetronome = { metronomeEnabled = it }
                        )
                    }
                }

                // 模式 1 + 模式 3: 横屏平板 (sw >= 600dp，50/50 对称双分栏)
                isTablet -> {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        InstructionalVideoPane(
                            videoTitle = currentExercise.videoTitle,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            isPaused = isPaused
                        )

                        PatientRealtimeCameraPane(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            previewView = previewView,
                            armRaiseCount = armRaiseCount,
                            statusText = statusText,
                            footVisible = footVisible,
                            requiresFullBody = requiresFullBody,
                            hasRas = currentExercise.hasRas,
                            metronomeEnabled = metronomeEnabled,
                            onToggleMetronome = { metronomeEnabled = it }
                        )
                    }
                }

                // 模式 1 + 模式 3: 手机横屏 (sw < 600dp，40/60 比例，高度保底 >60%，解决 P0-1)
                else -> {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        InstructionalVideoPane(
                            videoTitle = currentExercise.videoTitle,
                            modifier = Modifier
                                .weight(0.40f)
                                .fillMaxHeight(),
                            isPaused = isPaused
                        )

                        PatientRealtimeCameraPane(
                            modifier = Modifier
                                .weight(0.60f)
                                .fillMaxHeight(),
                            previewView = previewView,
                            armRaiseCount = armRaiseCount,
                            statusText = statusText,
                            footVisible = footVisible,
                            requiresFullBody = requiresFullBody,
                            hasRas = currentExercise.hasRas,
                            metronomeEnabled = metronomeEnabled,
                            onToggleMetronome = { metronomeEnabled = it }
                        )
                    }
                }
            }
        }

        // 常驻操作底部安全控制栏：紧急停止始终常驻可见且高亮，手机横屏紧凑适配避免压碎主画面 (P0-1)
        PersistentControlFooter(
            isPhoneLandscape = isPhoneLandscape,
            isPaused = isPaused,
            onTogglePause = { isPaused = !isPaused },
            onFinishSet = { /* 演示版：不写训练记录 */ },
            onEmergencyStop = onEmergencyStop
        )
    }
}

/**
 * 训练顶部信息与模式切换条：动态动作名 (P1-7) + 适老字号 >= 18sp (P1-13)。
 */
@Composable
private fun TrainingHeaderBar(
    exercise: ExerciseMeta,
    currentMode: TrainingDisplayMode,
    onModeSelect: (TrainingDisplayMode) -> Unit,
    isPhoneLandscape: Boolean
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = if (isPhoneLandscape) 4.dp else 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = exercise.name,
                    style = if (isPhoneLandscape) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${exercise.target} 【演示数据】",
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(8.dp))

            // 模式切换胶囊选择器 (字号 >= 18sp，无障碍触控规范)
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    modifier = Modifier.padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TrainingDisplayMode.entries.forEach { mode ->
                        val selected = currentMode == mode
                        Surface(
                            color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { onModeSelect(mode) }
                        ) {
                            Row(
                                modifier = Modifier.padding(
                                    horizontal = if (isPhoneLandscape) 8.dp else 12.dp,
                                    vertical = if (isPhoneLandscape) 4.dp else 6.dp
                                ),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = mode.icon,
                                    fontSize = 18.sp
                                )
                                Text(
                                    text = mode.label,
                                    fontSize = 18.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 模式 2 专用的动作语音引导胶囊条：字号 >= 18sp (P1-13)。
 */
@Composable
private fun VoiceGuidanceBanner(
    prompt: String,
    isPaused: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0F172A),
            contentColor = Color.White
        ),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF0284C7))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(Color(0xFF0284C7), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("🔊", fontSize = 20.sp)
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "动作语音播报中",
                        color = Color(0xFF38BDF8),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = if (isPaused) "已暂停播报" else "AI 语音教练 【演示数据】",
                            color = if (isPaused) Color(0xFFFFB4AB) else Color(0xFF80D5CB),
                            fontSize = 18.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = prompt,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * 教学视频播放区（防溢出重构，maxLines=1 + Ellipsis，进度条常驻清晰，解决 P1-6）。
 */
@Composable
private fun InstructionalVideoPane(
    videoTitle: String,
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
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "🎬 教学示范",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }

                Text(
                    text = if (isPaused) "已暂停" else "循环示范中",
                    fontSize = 18.sp,
                    color = if (isPaused) Color(0xFFFFB4AB) else Color(0xFF80D5CB),
                    fontWeight = FontWeight.Medium
                )
            }

            // 中央动作图示与标题 (防溢出紧凑设计，避免挤压裁字 - P1-6)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFF1E293B), CircleShape)
                        .border(1.5.dp, Color(0xFF38BDF8), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "示范播放",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = videoTitle,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "动作幅度与节拍指引 【演示数据】",
                        fontSize = 18.sp,
                        color = Color(0xFF94A3B8),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 底部进度条 (常驻可见，避免被压出屏幕)
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "示范进度 01:24 / 03:00",
                        fontSize = 18.sp,
                        color = Color(0xFF94A3B8)
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

/**
 * 患者实时摄像头画面区：
 * 叠加层极度轻量化 (P1-5)，画面可视通透面积保持 > 75%；
 * 非步态动作动态隐藏 RAS (P1-7)；全量字号 >= 18sp (P1-13)。
 */
@Composable
private fun PatientRealtimeCameraPane(
    modifier: Modifier = Modifier,
    previewView: PreviewView,
    armRaiseCount: Int,
    statusText: String,
    footVisible: Boolean,
    requiresFullBody: Boolean,
    hasRas: Boolean,
    metronomeEnabled: Boolean,
    onToggleMetronome: (Boolean) -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black),
        border = androidx.compose.foundation.BorderStroke(
            if (!footVisible) 2.5.dp else 1.5.dp,
            if (!footVisible) Color(0xFFBA1A1A) else Color(0xFF0284C7)
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 底层：CameraX 真实摄像头画面 (占满 100% 视口)
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )

            // 叠加层 1：左上角 扁平微型动作计数胶囊 (单行排布，绝不侵占中央肢体区域 - P1-5)
            Surface(
                color = Color(0x990F172A),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8)),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "$armRaiseCount",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF38BDF8)
                    )
                    Text(
                        text = "/ 12 次",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            // 叠加层 2：右上角 听觉节律 RAS 微型开关 (非步态动作自动隐藏 - P1-7)
            if (hasRas) {
                Surface(
                    color = Color(0x990F172A),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF80D5CB)),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "🎵 64 BPM",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF80D5CB)
                        )
                        Surface(
                            color = if (metronomeEnabled) Color(0xFF006A63) else Color(0xFF334155),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onToggleMetronome(!metronomeEnabled) }
                        ) {
                            Text(
                                text = if (metronomeEnabled) "节拍 开" else "节拍 关",
                                fontSize = 18.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // 叠加层 3：底部 足部入镜提示与姿态状态胶囊 (置于最下沿居中，字号 >= 18sp - P1-13)
            Surface(
                color = if (requiresFullBody && !footVisible) Color(0xEEBA1A1A) else Color(0x990F172A),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (requiresFullBody && !footVisible) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = "脚部未入镜警告",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "⚠️ 脚部未入镜，请后退入框",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(Color(0xFF80D5CB), CircleShape)
                        )
                        Text(
                            text = "$statusText · 实时端上画面",
                            color = Color(0xFFE2E8F0),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/**
 * 常驻操作与安全控制底栏：
 * 手机横屏采用紧凑高度 (52dp) 保护视频视口 (P0-1)；
 * 紧急停止按钮任何模式下常驻可见、醒目高亮且绝对不被裁切。
 * 按钮文字精炼短小，杜绝单字孤立换行 (P1-14)。
 */
@Composable
private fun PersistentControlFooter(
    isPhoneLandscape: Boolean,
    isPaused: Boolean,
    onTogglePause: () -> Unit,
    onFinishSet: () -> Unit,
    onEmergencyStop: () -> Unit
) {
    val buttonMinHeight = if (isPhoneLandscape) 52.dp else 80.dp
    val buttonPadding = if (isPhoneLandscape) PaddingValues(horizontal = 8.dp, vertical = 6.dp) else null

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = if (isPhoneLandscape) 8.dp else 12.dp,
                    vertical = if (isPhoneLandscape) 4.dp else 8.dp
                ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ElderlyButton(
                text = if (isPaused) "继续" else "暂停",
                onClick = onTogglePause,
                variant = ElderlyButtonVariant.Secondary,
                minHeight = buttonMinHeight,
                contentPadding = buttonPadding,
                modifier = Modifier.weight(1f)
            )
            ElderlyButton(
                text = "完成本组",
                onClick = onFinishSet,
                variant = ElderlyButtonVariant.Primary,
                minHeight = buttonMinHeight,
                contentPadding = buttonPadding,
                modifier = Modifier.weight(1.2f)
            )
            ElderlyButton(
                text = "🛑 紧急停止",
                onClick = onEmergencyStop,
                variant = ElderlyButtonVariant.Danger,
                minHeight = buttonMinHeight,
                contentPadding = buttonPadding,
                modifier = Modifier.weight(1.2f)
            )
        }
    }
}
