package com.rehabtrainer.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.widget.Toast
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.rehabtrainer.app.core.pose.CaptureGate
import com.rehabtrainer.app.core.pose.PoseAnalyzer
import com.rehabtrainer.app.core.pose.PoseFrame
import com.rehabtrainer.app.core.training.InstructionalVideoSource
import com.rehabtrainer.app.core.training.SetProgress
import com.rehabtrainer.app.core.training.TrainingDisplayMode
import com.rehabtrainer.app.core.training.TrainingDisplayPolicy
import com.rehabtrainer.app.data.local.AppPreferences
import com.rehabtrainer.app.ui.DEFAULT_EXERCISES
import com.rehabtrainer.app.ui.audio.VoiceCoach
import kotlinx.coroutines.delay
import java.util.concurrent.Executors

/**
 * 训练执行页：双播放 / 纯拍摄+语音两种模式，D17 采集门禁，组状态机与适老化自适应。
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

    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isTablet = configuration.smallestScreenWidthDp >= 600
    val isPhoneLandscape = isLandscape && !isTablet
    val screenWidthDp = configuration.screenWidthDp

    val currentExercise = DEFAULT_EXERCISES.firstOrNull { it.code == exerciseCode }
        ?: DEFAULT_EXERCISES.first()
    val requiresFullBody = currentExercise.requiresFullBody
    val videoSource = InstructionalVideoSource(
        remoteUrl = currentExercise.videoUri,
        watermarkText = currentExercise.watermarkText,
        creditLine = currentExercise.creditLine
    )

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
            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                Text("授予摄像头权限")
            }
        }
        return
    }

    var displayMode by rememberSaveable { mutableStateOf(TrainingDisplayMode.DUAL) }
    var voiceEnabled by rememberSaveable {
        mutableStateOf(AppPreferences.isAudioGuidanceEnabled(context))
    }
    var metronomeEnabled by rememberSaveable {
        mutableStateOf(AppPreferences.isAudioGuidanceEnabled(context))
    }
    // 暂停状态须跨 Activity 重建保存：旋转后仍保持暂停，视频不得自动续播。
    var isPaused by rememberSaveable { mutableStateOf(false) }
    var ttsReady by remember { mutableStateOf(false) }
    var ttsFailed by remember { mutableStateOf(false) }

    var armRaiseCount by remember { mutableIntStateOf(0) }
    var setProgress by remember(exerciseCode) {
        mutableStateOf(SetProgress(currentExercise.sets, currentExercise.repsPerSet))
    }
    var statusText by remember { mutableStateOf("正在寻找人体站位…") }
    var footVisible by remember { mutableStateOf(true) }
    var gateBlocked by remember { mutableStateOf(false) }
    var previousGateBlocked by remember { mutableStateOf(false) }

    val displayPolicy = TrainingDisplayPolicy(
        mode = displayMode,
        isPhoneLandscape = isPhoneLandscape,
        availableWidthDp = screenWidthDp,
        isVoiceEnabled = voiceEnabled
    )

    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val voiceCoach = remember {
        VoiceCoach(context) { ready ->
            ttsReady = ready
            if (!ready) ttsFailed = true
        }
    }
    DisposableEffect(Unit) {
        onDispose { voiceCoach.release() }
    }

    val executor = remember(exerciseCode) { Executors.newSingleThreadExecutor() }
    val previewView = remember(exerciseCode) { PreviewView(context) }
    val analyzer = remember(exerciseCode) {
        PoseAnalyzer(
            context = context,
            requiresFullBody = requiresFullBody,
            onFrame = { frame ->
                mainHandler.post {
                    if (isPaused) return@post
                    when (frame) {
                        is PoseFrame.Detected -> {
                            val feetVisible = CaptureGate.isFootVisible(frame.footVisibility)
                            footVisible = feetVisible
                            gateBlocked = requiresFullBody && !feetVisible
                            if (gateBlocked) {
                                // D17：门禁期间冻结计数，姿态检测继续以等待自动恢复。
                                statusText = "脚部不在画面内"
                            } else {
                                statusText = if (requiresFullBody) "全身姿态识别正常" else "上半身姿态识别正常"
                                if (!setProgress.isComplete) {
                                    armRaiseCount = setProgress.capReps(frame.armRaiseCount)
                                }
                            }
                        }
                        PoseFrame.NoPose -> statusText = "未检测到人体，请站入画面中央"
                        is PoseFrame.Failure -> statusText = frame.message
                    }
                }
            }
        )
    }

    DisposableEffect(exerciseCode) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var cameraProvider: ProcessCameraProvider? = null
        var disposed = false
        providerFuture.addListener({
            if (disposed) return@addListener
            val provider = providerFuture.get()
            cameraProvider = provider
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(executor, analyzer)
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_FRONT_CAMERA,
                preview,
                analysis
            )
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            disposed = true
            cameraProvider?.unbindAll()
            analyzer.close()
            executor.shutdown()
        }
    }

    val shouldSpeak = displayPolicy.shouldSpeak

    LaunchedEffect(shouldSpeak, ttsReady, setProgress.isComplete) {
        if (!shouldSpeak) {
            voiceCoach.stop()
        } else if (ttsReady && !setProgress.isComplete && !isPaused) {
            voiceCoach.speak("开始${currentExercise.name}，共 ${setProgress.totalSets} 组，每组 ${setProgress.repsPerSet} 次")
            voiceCoach.speak("感到头晕或不适，请立即停止", flush = false)
        }
    }

    LaunchedEffect(isPaused) {
        analyzer.setPaused(isPaused)
        if (isPaused) voiceCoach.stop()
    }

    LaunchedEffect(
        metronomeEnabled,
        isPaused,
        setProgress.isComplete,
        currentExercise.hasRas,
        currentExercise.bpm
    ) {
        if (!metronomeEnabled || isPaused || setProgress.isComplete || !currentExercise.hasRas) {
            return@LaunchedEffect
        }
        val intervalMs = (60_000L / currentExercise.bpm).coerceAtLeast(150L)
        while (true) {
            voiceCoach.beep()
            delay(intervalMs)
        }
    }

    LaunchedEffect(gateBlocked, shouldSpeak, ttsReady) {
        if (shouldSpeak && ttsReady) {
            if (gateBlocked && !previousGateBlocked) {
                voiceCoach.speak("脚部未入镜，请后退站入全身框", flush = false)
            } else if (!gateBlocked && previousGateBlocked) {
                voiceCoach.speak("已恢复计数", flush = false)
            }
        }
        previousGateBlocked = gateBlocked
    }

    // P2-3：安全优先不做二次确认，但停止后先给可见反馈再退出训练页。
    val stopTrainingWithFeedback: () -> Unit = {
        Toast.makeText(context, "训练已停止，本次未保存记录", Toast.LENGTH_LONG).show()
        onEmergencyStop()
    }

    val finishCurrentSet: () -> Unit = {
        if (!setProgress.isComplete) {
            val finishedSet = setProgress.currentSet
            val next = setProgress.finishCurrentSet()
            setProgress = next
            if (next.isComplete) {
                statusText = "全部训练组已完成"
                if (shouldSpeak && ttsReady) {
                    voiceCoach.speak("全部 ${next.totalSets} 组已完成，训练结束")
                }
            } else {
                armRaiseCount = 0
                analyzer.resetCounter()
                statusText = "第 ${next.currentSet} 组准备开始"
                if (shouldSpeak && ttsReady) {
                    voiceCoach.speak("第 $finishedSet 组完成，休息 ${currentExercise.restSeconds} 秒")
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TrainingHeaderBar(
            exercise = currentExercise,
            currentMode = displayPolicy.effectiveMode,
            currentSet = setProgress.currentSet,
            totalSets = setProgress.totalSets,
            forcedCameraOnly = displayPolicy.isForcedCameraOnly,
            onModeSelect = { displayMode = it },
            isPhoneLandscape = isPhoneLandscape,
            showVoiceToggle = displayPolicy.effectiveMode == TrainingDisplayMode.CAMERA_ONLY && isPhoneLandscape,
            voiceEnabled = voiceEnabled,
            ttsFailed = ttsFailed,
            onToggleVoice = { voiceEnabled = it }
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when {
                !displayPolicy.showVideo -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = if (isPhoneLandscape) 10.dp else 12.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // 手机横屏高度有限，语音开关收进头部，把纵向空间全部留给摄像头。
                        if (!isPhoneLandscape) {
                            VoiceGuidanceBanner(
                                prompt = currentExercise.prompt,
                                isPaused = isPaused,
                                isComplete = setProgress.isComplete,
                                voiceEnabled = voiceEnabled,
                                ttsFailed = ttsFailed,
                                onToggleVoice = { voiceEnabled = it }
                            )
                        }

                        PatientRealtimeCameraPane(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            previewView = previewView,
                            armRaiseCount = armRaiseCount,
                            repsPerSet = setProgress.repsPerSet,
                            counterLabel = currentExercise.counterLabel,
                            largeCount = true,
                            statusText = statusText,
                            footVisible = footVisible,
                            requiresFullBody = requiresFullBody,
                            hasRas = currentExercise.hasRas,
                            bpm = currentExercise.bpm,
                            metronomeEnabled = metronomeEnabled,
                            onToggleMetronome = { metronomeEnabled = it }
                        )
                    }
                }

                !isLandscape && !isTablet -> {
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
                            isPaused = isPaused,
                            videoSource = videoSource
                        )

                        PatientRealtimeCameraPane(
                            modifier = Modifier
                                .weight(0.62f)
                                .fillMaxWidth(),
                            previewView = previewView,
                            armRaiseCount = armRaiseCount,
                            repsPerSet = setProgress.repsPerSet,
                            counterLabel = currentExercise.counterLabel,
                            statusText = statusText,
                            footVisible = footVisible,
                            requiresFullBody = requiresFullBody,
                            hasRas = currentExercise.hasRas,
                            bpm = currentExercise.bpm,
                            metronomeEnabled = metronomeEnabled,
                            onToggleMetronome = { metronomeEnabled = it }
                        )
                    }
                }

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
                            isPaused = isPaused,
                            videoSource = videoSource
                        )

                        PatientRealtimeCameraPane(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            previewView = previewView,
                            armRaiseCount = armRaiseCount,
                            repsPerSet = setProgress.repsPerSet,
                            counterLabel = currentExercise.counterLabel,
                            statusText = statusText,
                            footVisible = footVisible,
                            requiresFullBody = requiresFullBody,
                            hasRas = currentExercise.hasRas,
                            bpm = currentExercise.bpm,
                            metronomeEnabled = metronomeEnabled,
                            onToggleMetronome = { metronomeEnabled = it }
                        )
                    }
                }

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
                            isPaused = isPaused,
                            videoSource = videoSource
                        )

                        PatientRealtimeCameraPane(
                            modifier = Modifier
                                .weight(0.60f)
                                .fillMaxHeight(),
                            previewView = previewView,
                            armRaiseCount = armRaiseCount,
                            repsPerSet = setProgress.repsPerSet,
                            counterLabel = currentExercise.counterLabel,
                            statusText = statusText,
                            footVisible = footVisible,
                            requiresFullBody = requiresFullBody,
                            hasRas = currentExercise.hasRas,
                            bpm = currentExercise.bpm,
                            metronomeEnabled = metronomeEnabled,
                            onToggleMetronome = { metronomeEnabled = it }
                        )
                    }
                }
            }
        }

        PersistentControlFooter(
            isPhoneLandscape = isPhoneLandscape,
            isPaused = isPaused,
            setProgress = setProgress,
            onTogglePause = { isPaused = !isPaused },
            onFinishSet = finishCurrentSet,
            onEmergencyStop = stopTrainingWithFeedback
        )
    }
}
