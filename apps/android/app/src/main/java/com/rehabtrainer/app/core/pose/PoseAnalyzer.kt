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
import kotlin.math.abs

/** 一帧姿态识别的输出。 */
sealed interface PoseFrame {
    data class Detected(
        val wristOffset: Float,
        val armRaiseCount: Int,
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
    private val onFrame: (PoseFrame) -> Unit
) : ImageAnalysis.Analyzer {

    private val counter = ArmRaiseCounter()

    @Volatile
    private var isPaused = false

    /** 手动暂停期间停止累计，恢复后从零开始，避免暂停时动作被补记。 */
    fun setPaused(paused: Boolean) {
        isPaused = paused
        if (paused) counter.reset()
    }

    /** 完成一组后由 UI 调用，下一组计数从零开始。 */
    fun resetCounter() {
        counter.reset()
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
        if (landmarks == null || landmarks.size < 33) {
            onFrame(PoseFrame.NoPose)
            return
        }

        // 11/12 肩，23/24 髋，16 右腕，27–32 踝/脚跟/脚尖。
        val shoulderY = (landmarks[11].y() + landmarks[12].y()) / 2f
        val hipY = (landmarks[23].y() + landmarks[24].y()) / 2f
        val torso = abs(hipY - shoulderY)
        // visibility() 是 Optional<Float>：模型未给出可见度时按不可见处理（D17 门槛 fail-closed）。
        val footVisibility = listOf(27, 28, 29, 30, 31, 32)
            .minOf { index -> landmarks[index].visibility().orElse(0f) }

        if (torso <= 1e-4f) {
            onFrame(PoseFrame.NoPose)
            return
        }

        val wristOffset = (landmarks[16].y() - shoulderY) / torso

        if (isPaused) {
            onFrame(PoseFrame.Detected(wristOffset, counter.count, footVisibility))
            return
        }

        if (!CaptureGate.shouldCount(requiresFullBody, footVisibility)) {
            counter.reset()
            onFrame(PoseFrame.Detected(wristOffset, 0, footVisibility))
            return
        }

        val count = counter.update(wristOffset)
        onFrame(PoseFrame.Detected(wristOffset, count, footVisibility))
    }

    private fun rotateIfNeeded(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
