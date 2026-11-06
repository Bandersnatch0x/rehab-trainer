package com.rehabtrainer.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.util.Size
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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.rehabtrainer.app.core.pose.ExerciseKind
import com.rehabtrainer.app.core.pose.PoseAnalyzer
import com.rehabtrainer.app.core.pose.PoseFrame
import com.rehabtrainer.app.core.pose.RepIssue
import com.rehabtrainer.app.core.pose.RepResult
import com.rehabtrainer.app.core.training.InstructionalVideoSource
import com.rehabtrainer.app.core.training.ModelFingerprint
import com.rehabtrainer.app.core.training.SetProgress
import com.rehabtrainer.app.core.training.TrainingDisplayMode
import com.rehabtrainer.app.core.training.TrainingDisplayPolicy
import com.rehabtrainer.app.data.local.AppPreferences
import com.rehabtrainer.app.data.offline.ClientRecordIds
import com.rehabtrainer.app.data.session.SessionRecordBuilder
import com.rehabtrainer.app.data.session.AppData
import com.rehabtrainer.app.data.session.SetOutcome
import com.rehabtrainer.app.ui.ExerciseRegistry
import com.rehabtrainer.app.ui.audio.VoiceCoach
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.concurrent.Executors

private const val REP_HINT_TTL_MS = 2_500L

/**
 * 训练执行页：双播放 / 纯拍摄+语音两种模式，D17 采集门禁，组状态机与适老化自适应。
 */
private fun issueText(issue: RepIssue): String = when (issue) {
    RepIssue.INSUFFICIENT_RANGE -> "幅度不足"
    RepIssue.EXCESSIVE_DEPTH -> "下蹲过深"
    RepIssue.KNEE_VALGUS -> "膝盖内扣"
    RepIssue.ASYMMETRY -> "左右不对称"
    RepIssue.SHOULDER_UNEVEN -> "肩部不平"
    RepIssue.HIP_UNEVEN -> "髋部不平"
    RepIssue.HEAD_OFFSET -> "头部偏移"
    RepIssue.LATERAL_LEAN -> "躯干侧倾"
    RepIssue.ELBOW_BENT -> "肘部未伸展"
    RepIssue.POSE_INCOMPLETE -> "关键点不足，请调整机位"
}

private fun describeRep(result: RepResult): String =
    if (result.valid) "本次达标" else result.issues.joinToString("；") { issueText(it) }

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
    val largeFont = configuration.fontScale >= 1.3f

    val currentExercise = ExerciseRegistry.exercises.firstOrNull { it.code == exerciseCode }
        ?: ExerciseRegistry.exercises.first()
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
    // 画中画主/小：默认主画面是实时拍摄、小浮窗是教学视频；点按小浮窗或「切换画面」交换。
    // 用 rememberSaveable 让旋转后保持用户选择。
    var videoIsMain by rememberSaveable { mutableStateOf(false) }
    var ttsReady by remember { mutableStateOf(false) }
    var ttsFailed by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    var repCount by remember { mutableIntStateOf(0) }
    var repHint by remember { mutableStateOf<String?>(null) }
    var setValidFrames by remember { mutableIntStateOf(0) }
    var setTotalFrames by remember { mutableIntStateOf(0) }
    var setInvalidReps by remember { mutableIntStateOf(0) }
    var setInterruptions by remember { mutableIntStateOf(0) }
    var lastRepIndex by remember { mutableIntStateOf(0) }
    var repHintUntilMs by remember { mutableStateOf(0L) }
    var setProgress by remember(exerciseCode) {
        mutableStateOf(SetProgress(currentExercise.sets, currentExercise.repsPerSet))
    }
    var statusText by remember { mutableStateOf("正在寻找人体站位…") }
    var footVisible by remember { mutableStateOf(true) }
    var gateBlocked by remember { mutableStateOf(false) }
    var previousGateBlocked by remember { mutableStateOf(false) }

    // 大字号下优先实景 + 语音：示范视频会挤占摄像头高度，导致叠加信息重叠。
    val layoutMode = if (largeFont) TrainingDisplayMode.CAMERA_ONLY else displayMode
    val displayPolicy = TrainingDisplayPolicy(
        mode = layoutMode,
        isPhoneLandscape = isPhoneLandscape,
        availableWidthDp = screenWidthDp,
        isVoiceEnabled = voiceEnabled
    )

    // 小浮窗宽度：手机约半屏，平板限制上限，避免小窗过大盖住主画面。
    val pipWidth = (screenWidthDp * 0.52f)
        .coerceIn(140f, if (isTablet) 280f else 200f)
        .dp
    // 服务端未下发本条动作视频时，播放的是通用演示片段，界面须如实标注。
    val usesGenericDemoVideo = currentExercise.videoUri.isNullOrBlank()

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
            exerciseKind = currentExercise.kind,
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
                                statusText = if (largeFont) "请调整站位" else "脚部不在画面内"
                            } else {
                                statusText = frame.framingHint ?: when {
                                    largeFont -> "识别正常"
                                    else -> ""
                                }
                                setTotalFrames += 1
                                if (feetVisible && !gateBlocked) {
                                    setValidFrames += 1
                                }
                                if (!setProgress.isComplete) {
                                    repCount = setProgress.capReps(frame.repCount)
                                }
                                val nowMs = System.currentTimeMillis()
                                val latestRep = frame.lastRep
                                if (latestRep != null && latestRep.index != lastRepIndex) {
                                    lastRepIndex = latestRep.index
                                    repHintUntilMs = nowMs + REP_HINT_TTL_MS
                                    if (!latestRep.valid) {
                                        setInvalidReps += 1
                                    }
                                }
                                repHint = when {
                                    frame.calibrationRemaining > 0 ->
                                        "校准中：还需完成 ${frame.calibrationRemaining} 次动作"
                                    frame.currentIssues.isNotEmpty() ->
                                        "调整：" + frame.currentIssues.joinToString("；") { issueText(it) }
                                    latestRep != null && nowMs <= repHintUntilMs -> describeRep(latestRep)
                                    currentExercise.kind == ExerciseKind.POSTURE ||
                                        currentExercise.kind == ExerciseKind.STRETCH -> "保持中…"
                                    else -> null
                                }
                            }
                        }
                        PoseFrame.NoPose -> statusText = if (largeFont) "请站入画面中央" else "未检测到人体：请让上半身完整入镜并避免背光"
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
                // 720p 提升远距离/小目标的人体检出率；Lite 模型在此分辨率下仍可实时。
                .setTargetResolution(Size(1280, 720))
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
        if (isPaused) {
            setInterruptions += 1
            voiceCoach.stop()
        }
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

    val submitSetRecord: (Int) -> Unit = { completedReps ->
        val patientId = ExerciseRegistry.patientId
        val prescriptionId = ExerciseRegistry.prescriptionId
        if (patientId != null && prescriptionId != null) {
            val outcome = SetOutcome(
                exerciseCode = currentExercise.code,
                setIndex = setProgress.currentSet,
                totalSets = setProgress.totalSets,
                completionCount = completedReps,
                movementErrorCount = setInvalidReps,
                interruptionCount = setInterruptions,
                validFrameRatio = SessionRecordBuilder.validFrameRatio(setValidFrames, setTotalFrames),
                recordedAtIso = Instant.now().toString(),
                clientRecordId = ClientRecordIds.newId()
            )
            val record = SessionRecordBuilder.build(
                patientId = patientId,
                prescriptionId = prescriptionId,
                outcome = outcome,
                modelSha256 = ModelFingerprint.sha256(context)
            )
            scope.launch { AppData.submit(record) }
        }
        setValidFrames = 0
        setTotalFrames = 0
        setInvalidReps = 0
        setInterruptions = 0
    }

    val finishCurrentSet: () -> Unit = {
        if (!setProgress.isComplete) {
            val finishedSet = setProgress.currentSet
            submitSetRecord(repCount)
            val next = setProgress.finishCurrentSet()
            setProgress = next
            if (next.isComplete) {
                statusText = "全部训练组已完成"
                if (shouldSpeak && ttsReady) {
                    voiceCoach.speak("全部 ${next.totalSets} 组已完成，训练结束")
                }
            } else {
                repCount = 0
                repHint = null
                analyzer.resetCounter()
                statusText = "第 ${next.currentSet} 组准备开始"
                if (shouldSpeak && ttsReady) {
                    voiceCoach.speak("第 $finishedSet 组完成，休息 ${currentExercise.restSeconds} 秒")
                }
            }
        }
    }

    // 达到本组目标次数后自动进入下一组，避免患者还要点一次「完成本组」。
    LaunchedEffect(repCount, setProgress.currentSet, setProgress.isComplete) {
        if (setProgress.isTargetReached(repCount)) {
            finishCurrentSet()
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
            compactLabels = largeFont,
            showModeSelector = !largeFont,
            showVoiceToggle = displayPolicy.effectiveMode == TrainingDisplayMode.CAMERA_ONLY && isPhoneLandscape,
            voiceEnabled = voiceEnabled,
            ttsFailed = ttsFailed,
            onToggleVoice = { voiceEnabled = it },
            showSwapButton = displayPolicy.showVideo,
            onSwapMain = { videoIsMain = !videoIsMain }
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
                        // 手机横屏或大字模式下高度有限，语音开关收进头部，把纵向空间全部留给摄像头。
                        if (!isPhoneLandscape && !largeFont) {
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
                            repCount = repCount,
                            repHint = repHint,
                            largeFont = largeFont,
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

                else -> {
                    // DUAL：一个主画面铺满 + 右下角圆角小浮窗，点按小窗或头部「切换画面」交换主/小。
                    TrainingPipLayout(
                        videoIsMain = videoIsMain,
                        onSwap = { videoIsMain = !videoIsMain },
                        pipWidth = pipWidth,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(6.dp),
                        videoSlot = { slotModifier, compact ->
                            InstructionalVideoPane(
                                videoTitle = currentExercise.videoTitle,
                                isPaused = isPaused,
                                isGenericDemo = usesGenericDemoVideo,
                                exerciseName = currentExercise.name,
                                modifier = slotModifier,
                                videoSource = videoSource,
                                compact = compact
                            )
                        },
                        cameraSlot = { slotModifier, compact ->
                            PatientRealtimeCameraPane(
                                modifier = slotModifier,
                                previewView = previewView,
                                repCount = repCount,
                                repHint = repHint,
                                largeFont = largeFont,
                                repsPerSet = setProgress.repsPerSet,
                                counterLabel = currentExercise.counterLabel,
                                statusText = statusText,
                                footVisible = footVisible,
                                requiresFullBody = requiresFullBody,
                                hasRas = currentExercise.hasRas,
                                bpm = currentExercise.bpm,
                                metronomeEnabled = metronomeEnabled,
                                onToggleMetronome = { metronomeEnabled = it },
                                compact = compact
                            )
                        }
                    )
                }
            }
        }

        PersistentControlFooter(
            isPhoneLandscape = isPhoneLandscape,
            isPaused = isPaused,
            largeFont = largeFont,
            setProgress = setProgress,
            onTogglePause = { isPaused = !isPaused },
            onFinishSet = finishCurrentSet,
            onEmergencyStop = stopTrainingWithFeedback
        )
    }
}
