package com.rehabtrainer.app.core.pose

import kotlin.math.abs
import kotlin.math.atan2

/**
 * 与平台无关的关键点快照；由 [PoseAnalyzer] 从 MediaPipe 结果转换。
 * 坐标系与 MediaPipe 一致：x/y 为 0–1 归一化，y 向下。
 */
data class PosePoint(
    val x: Float,
    val y: Float,
    val z: Float = 0f,
    val visibility: Float = 1f
)

/** MediaPipe Pose 的 33 点索引中本模块用到的部分。 */
object PoseIndex {
    const val LEFT_SHOULDER = 11
    const val RIGHT_SHOULDER = 12
    const val LEFT_WRIST = 15
    const val RIGHT_WRIST = 16
    const val LEFT_HIP = 23
    const val RIGHT_HIP = 24
    const val LEFT_KNEE = 25
    const val RIGHT_KNEE = 26
    const val LEFT_ANKLE = 27
    const val RIGHT_ANKLE = 28
    const val LEFT_HEEL = 29
    const val RIGHT_HEEL = 30
    const val LEFT_FOOT_INDEX = 31
    const val RIGHT_FOOT_INDEX = 32
    const val LANDMARK_COUNT = 33
}

internal object PoseGeometry {

    fun midY(points: List<PosePoint>, first: Int, second: Int): Float =
        (points[first].y + points[second].y) / 2f

    fun midX(points: List<PosePoint>, first: Int, second: Int): Float =
        (points[first].x + points[second].x) / 2f

    /** 肩中点到髋中点的垂直距离；用于把像素比例归一化。 */
    fun torsoHeight(points: List<PosePoint>): Float? {
        val height = abs(
            midY(points, PoseIndex.LEFT_HIP, PoseIndex.RIGHT_HIP) -
                midY(points, PoseIndex.LEFT_SHOULDER, PoseIndex.RIGHT_SHOULDER)
        )
        return if (height > 1e-4f) height else null
    }

    /** 肩中点相对髋中点的前倾角（度），0 表示躯干垂直。 */
    fun trunkLeanDegrees(points: List<PosePoint>): Float? {
        val dx = midX(points, PoseIndex.LEFT_SHOULDER, PoseIndex.RIGHT_SHOULDER) -
            midX(points, PoseIndex.LEFT_HIP, PoseIndex.RIGHT_HIP)
        val dy = midY(points, PoseIndex.LEFT_SHOULDER, PoseIndex.RIGHT_SHOULDER) -
            midY(points, PoseIndex.LEFT_HIP, PoseIndex.RIGHT_HIP)
        if (abs(dy) < 1e-6f) {
            return null
        }
        return Math.toDegrees(atan2(abs(dx), abs(dy)).toDouble()).toFloat()
    }

    fun visible(points: List<PosePoint>, vararg indices: Int, threshold: Float = 0.5f): Boolean =
        indices.all { points[it].visibility >= threshold }
}