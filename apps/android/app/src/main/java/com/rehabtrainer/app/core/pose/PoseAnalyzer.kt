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

private const val TAG = "PoseAnalyzer"
private const val LOG_INTERVAL_MS = 1_000L
private const val GATE_RESET_MS = 5_000L

/** 一帧姿态识别的输出。 */
sealed interface PoseFrame {
    data class Detected(
        val repCount: Int,
        val lastRep: RepResult?,
        val currentIssues: Set<RepIssue>,
        val footVisibility: Float,
        val framingHint: String? = null,
        val calibrationRemaining: Int = 0
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
    private val onFrame: (PoseFrame) -> Unit
) : ImageAnalysis.Analyzer {

    private val analyzer = createExerciseAnalyzer(exerciseKind)

    @Volatile
    private var lastUpdate = AnalysisUpdate(0, null)

    @Volatile
    private var lastFrameTimestampMs = 0L

    private var lastDetectTimestampMs = 0L
    private var lastLogAtMs = 0L
    private var gateBlockedSinceMs = 0L

    @Volatile
    private var isPaused = false

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
            .setNumPoses(1)
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
        val now = SystemClock.elapsedRealtime()
        lastDetectTimestampMs =
            if (now > lastDetectTimestampMs) now else lastDetectTimestampMs + 1
        lastFrameTimestampMs = lastDetectTimestampMs
        try {
            val bitmap = imageProxy.toBitmap()
            val upright = rotateIfNeeded(bitmap, imageProxy.imageInfo.rotationDegrees)
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
        val landmarks = result.landmarks().firstOrNull()
        if (landmarks == null || landmarks.size < PoseIndex.LANDMARK_COUNT) {
            logThrottled("no pose detected")
            onFrame(PoseFrame.NoPose)
            return
        }

        val points = landmarks.map {
            PosePoint(
                x = it.x(),
                y = it.y(),
                z = it.z(),
                // 取 visibility 与 presence 的较大值：遮挡但仍在画面内的关键点常表现为 visibility 低、presence 高。
                visibility = maxOf(it.visibility().orElse(0f), it.presence().orElse(0f))
            )
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
                    lastUpdate.count,
                    lastUpdate.lastRep,
                    lastUpdate.currentIssues,
                    footVisibility,
                    calibrationRemaining = analyzer.calibrationRemaining
                )
            )
            return
        }

        if (!CaptureGate.shouldCount(requiresFullBody, footVisibility)) {
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
        logThrottled(
            "pose: shoulder=%.2f wrist=%.2f hip=%.2f ankle=%.2f width=%.2f wristRaise=%.2f hipAnkle=%.2f".format(
                minOf(points[PoseIndex.LEFT_SHOULDER].visibility, points[PoseIndex.RIGHT_SHOULDER].visibility),
                minOf(points[PoseIndex.LEFT_WRIST].visibility, points[PoseIndex.RIGHT_WRIST].visibility),
                minOf(points[PoseIndex.LEFT_HIP].visibility, points[PoseIndex.RIGHT_HIP].visibility),
                minOf(points[PoseIndex.LEFT_ANKLE].visibility, points[PoseIndex.RIGHT_ANKLE].visibility),
                shoulderWidth,
                wristRaise,
                hipAnkle
            )
        )
        lastUpdate = analyzer.update(points, lastFrameTimestampMs)
        onFrame(
            PoseFrame.Detected(
                repCount = lastUpdate.count,
                lastRep = lastUpdate.lastRep,
                currentIssues = lastUpdate.currentIssues,
                footVisibility = footVisibility,
                framingHint = framingHint,
                calibrationRemaining = analyzer.calibrationRemaining
            )
        )
    }

    private fun logThrottled(message: String) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastLogAtMs < LOG_INTERVAL_MS) return
        lastLogAtMs = now
        Log.d(TAG, message)
    }

    private fun rotateIfNeeded(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
