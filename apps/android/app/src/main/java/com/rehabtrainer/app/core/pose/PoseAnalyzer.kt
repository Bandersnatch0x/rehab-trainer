package com.rehabtrainer.app.core.pose

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

/** 一帧姿态识别的输出。 */
sealed interface PoseFrame {
    data class Detected(
        val repCount: Int,
        val lastRep: RepResult?,
        val footVisibility: Float
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
            .setMinPoseDetectionConfidence(0.5f)
            .setMinPosePresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setResultListener { result, _ -> handleResult(result) }
            .setErrorListener { error ->
                onFrame(PoseFrame.Failure(error.message ?: "姿态识别失败"))
            }
            .build()
        PoseLandmarker.createFromOptions(context, options)
    }

    override fun analyze(imageProxy: ImageProxy) {
        try {
            val bitmap = imageProxy.toBitmap()
            val upright = rotateIfNeeded(bitmap, imageProxy.imageInfo.rotationDegrees)
            val mpImage = BitmapImageBuilder(upright).build()
            landmarker.detectAsync(mpImage, imageProxy.imageInfo.timestamp / 1_000)
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
            onFrame(PoseFrame.NoPose)
            return
        }

        val points = landmarks.map {
            PosePoint(
                x = it.x(),
                y = it.y(),
                z = it.z(),
                // visibility() 是 Optional<Float>：模型未给出可见度时按不可见处理（D17 门槛 fail-closed）。
                visibility = it.visibility().orElse(0f)
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
            onFrame(PoseFrame.Detected(lastUpdate.count, lastUpdate.lastRep, footVisibility))
            return
        }

        if (!CaptureGate.shouldCount(requiresFullBody, footVisibility)) {
            analyzer.reset()
            lastUpdate = AnalysisUpdate(0, null)
            onFrame(PoseFrame.Detected(0, null, footVisibility))
            return
        }

        lastUpdate = analyzer.update(points)
        onFrame(
            PoseFrame.Detected(
                repCount = lastUpdate.count,
                lastRep = lastUpdate.lastRep,
                footVisibility = footVisibility
            )
        )
    }

    private fun rotateIfNeeded(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
