package com.rehabtrainer.app.core.pose

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import java.util.concurrent.atomic.AtomicLong

private const val TAG = "PoseAnalyzer"
private const val LOG_INTERVAL_MS = 1_000L
private const val GATE_RESET_MS = 5_000L

/** 多人同框时冻结计数的提示；与脚部门禁并列，同为「当前不可计数」的原因。 */
private const val SECOND_PERSON_HINT = "画面内有多人：请确保只有患者入镜"

/** 一帧姿态识别的输出。 */
sealed interface PoseFrame {
    data class Detected(
        val repCount: Int,
        val lastRep: RepResult?,
        val currentIssues: Set<RepIssue>,
        val footVisibility: Float,
        /** 画面内还有第二个人：计数已被冻结。与 [framingHint] 分开，因为后者包含不冻结的提示。 */
        val multiPersonPresent: Boolean = false,
        val framingHint: String? = null,
        val calibrationRemaining: Int = 0,
        /** 校准完成后的个人幅度；调用方据此持久化，下次训练免校准。 */
        val calibratedAmplitude: Float? = null
    ) : PoseFrame

    data object NoPose : PoseFrame

    data class Failure(val message: String) : PoseFrame
}

/**
 * CameraX [ImageAnalysis.Analyzer]：帧 → MediaPipe Pose Landmarker（LIVE_STREAM）→ 上肢计数。
 *
 * 推理结果在 MediaPipe 内部线程回调，[onFrame] 可能在非主线程执行；
 * 调用方负责切回主线程更新 UI。
 *
 * D17：全身类条目在脚部可见度不足时清零并停止累计；
 * 姿态检测继续运行，以便脚部重新入镜后自动恢复计数。
 */
class PoseAnalyzer(
    context: Context,
    modelAssetPath: String = "pose_landmarker_lite.task",
    private val requiresFullBody: Boolean = false,
    exerciseKind: ExerciseKind = ExerciseKind.ARM_RAISE,
    knownAmplitude: Float? = null,
    private val onFrame: (PoseFrame) -> Unit
) : ImageAnalysis.Analyzer {

    private val analyzer = createExerciseAnalyzer(exerciseKind, knownAmplitude = knownAmplitude)

    @Volatile
    private var lastUpdate = AnalysisUpdate(0, null)

    @Volatile
    private var lastFrameTimestampMs = 0L

    private var lastDetectTimestampMs = 0L
    private var lastLogAtMs = 0L
    private var lastRateAtMs = 0L
    private var gateBlockedSinceMs = 0L
    private var noPoseSinceMs = 0L

    /**
     * 每秒日志里的实际帧率：相机送来多少帧、MediaPipe 回了多少次结果。
     * `setNumPoses(2)` 的代价只能这么量——分析线程与回调线程各自累加，故用原子计数。
     */
    private val framesReceived = AtomicLong()
    private val resultsHandled = AtomicLong()

    @Volatile
    private var isPaused = false

    /**
     * MediaPipe 送进去的那张（已转正的）位图的宽高比。
     * 归一化坐标各向异性，角度量必须按它还原；检测回调在另一个线程读，故 @Volatile。
     */
    @Volatile
    private var frameAspect = 1f

    /** 手动暂停期间停止累计，恢复后从零开始，避免暂停时动作被补记。 */
    fun setPaused(paused: Boolean) {
        isPaused = paused
        if (paused) {
            analyzer.reset()
            lastUpdate = AnalysisUpdate(0, null)
        }
    }

    /** 完成一组后由 UI 调用，下一组计数从零开始。 */
    fun resetCounter() {
        analyzer.reset()
        lastUpdate = AnalysisUpdate(0, null)
    }

    private val landmarker: PoseLandmarker = run {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath(modelAssetPath)
            .build()
        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            // 只要 2 副：第 2 副仅用于判定「画面内有多人」，不参与计数。
            // 代价是每帧多跑一副骨架的关键点模型，低端机帧率需真机确认。
            .setNumPoses(2)
            // 适老化场景光线/机位不理想，放宽阈值提高检出率；误检由动作状态机与可见度门槛兜底。
            .setMinPoseDetectionConfidence(0.35f)
            .setMinPosePresenceConfidence(0.35f)
            .setMinTrackingConfidence(0.35f)
            .setResultListener { result, _ -> handleResult(result) }
            .setErrorListener { error ->
                onFrame(PoseFrame.Failure(error.message ?: "姿态识别失败"))
            }
            .build()
        PoseLandmarker.createFromOptions(context, options)
    }

    override fun analyze(imageProxy: ImageProxy) {
        // MediaPipe 要求 timestamp 为单调递增毫秒；用 elapsedRealtime 而不是 imageProxy 的微秒值。
        framesReceived.incrementAndGet()
        val now = SystemClock.elapsedRealtime()
        lastDetectTimestampMs =
            if (now > lastDetectTimestampMs) now else lastDetectTimestampMs + 1
        lastFrameTimestampMs = lastDetectTimestampMs
        try {
            val bitmap = imageProxy.toBitmap()
            val upright = rotateIfNeeded(bitmap, imageProxy.imageInfo.rotationDegrees)
            frameAspect = PoseGeometry.aspectRatioOf(upright.width, upright.height)
            val mpImage = BitmapImageBuilder(upright).build()
            landmarker.detectAsync(mpImage, lastDetectTimestampMs)
        } catch (error: RuntimeException) {
            onFrame(PoseFrame.Failure(error.message ?: "姿态识别失败"))
        } finally {
            imageProxy.close()
        }
    }

    fun close() {
        landmarker.close()
    }

    private fun handleResult(result: PoseLandmarkerResult) {
        resultsHandled.incrementAndGet()
        // 只保留关键点齐全的骨架：缺点的骨架算不出角度，出现即当未检出。
        val poses = result.landmarks()
            .filter { pose -> pose.size >= PoseIndex.LANDMARK_COUNT }
            .map { pose ->
                pose.map { landmark ->
                    PosePoint(
                        x = landmark.x(),
                        y = landmark.y(),
                        z = landmark.z(),
                        // 取 visibility 与 presence 的较大值：遮挡但仍在画面内的关键点常表现为 visibility 低、presence 高。
                        visibility = maxOf(
                            landmark.visibility().orElse(0f),
                            landmark.presence().orElse(0f)
                        )
                    )
                }
            }
        var points = poses.firstOrNull()
        if (points == null) {
            logThrottled { "no pose detected " + rates() }
            // 与门禁遮挡同样处理：长时间检不到人后重置，避免回来第一帧把旧 holdStart 补成一次保持。
            noPoseSinceMs =
                if (noPoseSinceMs == 0L) lastFrameTimestampMs else noPoseSinceMs
            if (lastFrameTimestampMs - noPoseSinceMs > GATE_RESET_MS) {
                analyzer.reset()
                lastUpdate = AnalysisUpdate(0, null)
            }
            onFrame(PoseFrame.NoPose)
            return
        }
        noPoseSinceMs = 0L
        // 第 2 副骨架只用于门禁：护理员入镜时跟踪目标会切走，这一次计数不可信。
        val secondPersonPresent = SecondPersonGate.isPresent(poses)
        val cameraRoll = PoseGeometry.cameraRollRadians(points, frameAspect)
        if (cameraRoll != null) {
            // 机位横滚会让整副骨架倾斜，先按双眼/双耳参考线回正，避免下游误报侧倾。
            points = PoseGeometry.derotate(points, cameraRoll, frameAspect)
        }
        val footVisibility = listOf(
            PoseIndex.LEFT_ANKLE,
            PoseIndex.RIGHT_ANKLE,
            PoseIndex.LEFT_HEEL,
            PoseIndex.RIGHT_HEEL,
            PoseIndex.LEFT_FOOT_INDEX,
            PoseIndex.RIGHT_FOOT_INDEX
        ).minOf { index -> points[index].visibility }

        if (isPaused) {
            onFrame(
                PoseFrame.Detected(
                    repCount = lastUpdate.count,
                    lastRep = lastUpdate.lastRep,
                    currentIssues = lastUpdate.currentIssues,
                    footVisibility = footVisibility,
                    multiPersonPresent = secondPersonPresent,
                    calibrationRemaining = analyzer.calibrationRemaining
                )
            )
            return
        }

        if (!CaptureGate.shouldCount(requiresFullBody, footVisibility) || secondPersonPresent) {
            // 门禁期间冻结而不立即清零：短暂出框不打断保持计时/基线；长时间遮挡才重置。
            gateBlockedSinceMs =
                if (gateBlockedSinceMs == 0L) lastFrameTimestampMs else gateBlockedSinceMs
            if (lastFrameTimestampMs - gateBlockedSinceMs > GATE_RESET_MS) {
                analyzer.reset()
                lastUpdate = AnalysisUpdate(0, null)
            }
            onFrame(
                PoseFrame.Detected(
                    repCount = lastUpdate.count,
                    lastRep = lastUpdate.lastRep,
                    currentIssues = lastUpdate.currentIssues,
                    footVisibility = footVisibility,
                    multiPersonPresent = secondPersonPresent,
                    framingHint = if (secondPersonPresent) SECOND_PERSON_HINT else null,
                    calibrationRemaining = analyzer.calibrationRemaining
                )
            )
            return
        }
        gateBlockedSinceMs = 0L

        val shoulderWidth = kotlin.math.abs(
            points[PoseIndex.LEFT_SHOULDER].x - points[PoseIndex.RIGHT_SHOULDER].x
        )
        val framingHint = when {
            shoulderWidth > 0.55f -> "距离太近：请后退，让上半身与手臂都在画面内"
            shoulderWidth < 0.08f -> "距离太远：请靠近一些或把手机放近一点"
            else -> null
        }
        logThrottled {
            // 仅日志用的几何量放进节流块，实时路径不为每秒一次的字符串付计算成本。
            val torsoForLog = PoseGeometry.torsoHeight(points)
            val wristRaise = if (torsoForLog != null) {
                val shoulderY = PoseGeometry.midY(points, PoseIndex.LEFT_SHOULDER, PoseIndex.RIGHT_SHOULDER)
                maxOf(
                    (shoulderY - points[PoseIndex.LEFT_WRIST].y) / torsoForLog,
                    (shoulderY - points[PoseIndex.RIGHT_WRIST].y) / torsoForLog
                )
            } else {
                0f
            }
            val hipAnkle = if (torsoForLog != null) {
                (PoseGeometry.midY(points, PoseIndex.LEFT_ANKLE, PoseIndex.RIGHT_ANKLE) -
                    PoseGeometry.midY(points, PoseIndex.LEFT_HIP, PoseIndex.RIGHT_HIP)) / torsoForLog
            } else {
                0f
            }
            "pose: shoulder=%.2f wrist=%.2f hip=%.2f ankle=%.2f width=%.2f wristRaise=%.2f hipAnkle=%.2f secondPerson=%b cameraRoll=%.1f".format(
                minOf(points[PoseIndex.LEFT_SHOULDER].visibility, points[PoseIndex.RIGHT_SHOULDER].visibility),
                minOf(points[PoseIndex.LEFT_WRIST].visibility, points[PoseIndex.RIGHT_WRIST].visibility),
                minOf(points[PoseIndex.LEFT_HIP].visibility, points[PoseIndex.RIGHT_HIP].visibility),
                minOf(points[PoseIndex.LEFT_ANKLE].visibility, points[PoseIndex.RIGHT_ANKLE].visibility),
                shoulderWidth,
                wristRaise,
                hipAnkle,
                secondPersonPresent,
                Math.toDegrees((cameraRoll ?: 0f).toDouble())
            ) + " " + rates()
        }
        lastUpdate = analyzer.update(points, lastFrameTimestampMs, frameAspect)
        onFrame(
            PoseFrame.Detected(
                repCount = lastUpdate.count,
                lastRep = lastUpdate.lastRep,
                currentIssues = lastUpdate.currentIssues,
                footVisibility = footVisibility,
                framingHint = framingHint,
                calibrationRemaining = analyzer.calibrationRemaining,
                calibratedAmplitude = analyzer.calibratedAmplitude
            )
        )
    }

    private fun logThrottled(message: () -> String) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastLogAtMs < LOG_INTERVAL_MS) return
        lastLogAtMs = now
        Log.d(TAG, message())
    }

    /** 结算上一窗口的帧率并清零；只在真的要打日志时调用，否则这段计数会被丢掉。 */
    private fun rates(): String {
        val now = SystemClock.elapsedRealtime()
        val elapsed = (now - lastRateAtMs).coerceAtLeast(1) / 1000.0
        lastRateAtMs = now
        return "fps=%.1f detect=%.1f".format(
            framesReceived.getAndSet(0) / elapsed,
            resultsHandled.getAndSet(0) / elapsed
        )
    }

    private fun rotateIfNeeded(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
