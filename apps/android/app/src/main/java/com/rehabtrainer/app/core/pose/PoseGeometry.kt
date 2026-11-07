package com.rehabtrainer.app.core.pose

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 与平台无关的关键点快照；由 [PoseAnalyzer] 从 MediaPipe 结果转换。
 * 坐标系与 MediaPipe 一致：x/y 为 0–1 归一化，y 向下。
 *
 * 注意：归一化是**各向异性**的（x 除以帧宽、y 除以帧高），
 * 所以在里面算角度前必须先按 [PoseGeometry.aspectRatioOf] 把 x 还原成与 y 同一尺度，
 * 否则只有在正方形帧上才等于物理角度。
 */
data class PosePoint(
    val x: Float,
    val y: Float,
    val z: Float = 0f,
    val visibility: Float = 1f
)

/** MediaPipe Pose 的 33 点索引中本模块用到的部分。 */
object PoseIndex {
    const val NOSE = 0
    const val LEFT_EYE = 2
    const val RIGHT_EYE = 5
    const val LEFT_EAR = 7
    const val RIGHT_EAR = 8
    const val LEFT_SHOULDER = 11
    const val RIGHT_SHOULDER = 12
    const val LEFT_ELBOW = 13
    const val RIGHT_ELBOW = 14
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

    private const val REFERENCE_VISIBILITY = 0.5f
    private const val DEGREES_TO_RADIANS: Float = 0.017453292f
    private const val MIN_ROLL_RADIANS: Float = 0.5f * DEGREES_TO_RADIANS
    private const val MAX_ROLL_RADIANS: Float = 20f * DEGREES_TO_RADIANS

    /**
     * 帧宽高比（像素宽 / 像素高）。归一化坐标里 x 与 y 的单位不同（x 除以宽、y 除以高），
     * 乘上它就把 x 还原成与 y 同尺度；角度函数全部经过它，阈值才是真正的物理角度。
     * 非有限或非正数按正方形（1.0）处理。
     */
    fun aspectRatioOf(width: Int, height: Int): Float =
        if (width > 0 && height > 0) width.toFloat() / height else 1f

    /** 以 [b] 为顶点、a-b-c 的夹角（度）；向量退化时返回 null。 */
    fun angleDegrees(a: PosePoint, b: PosePoint, c: PosePoint, aspectRatio: Float = 1f): Float? {
        val abx = (a.x - b.x) * aspectRatio
        val aby = a.y - b.y
        val cbx = (c.x - b.x) * aspectRatio
        val cby = c.y - b.y
        val magnitude = sqrt(abx * abx + aby * aby) * sqrt(cbx * cbx + cby * cby)
        if (magnitude < 1e-6f) return null
        val cosine = ((abx * cbx + aby * cby) / magnitude).coerceIn(-1f, 1f)
        return Math.toDegrees(acos(cosine).toDouble()).toFloat()
    }

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

    /** 肩中点相对髋中点的侧向倾斜角（度），0 表示躯干垂直；正面 2D 无法测真正的前后倾。 */
    fun lateralLeanDegrees(points: List<PosePoint>, aspectRatio: Float = 1f): Float? {
        val dx = (midX(points, PoseIndex.LEFT_SHOULDER, PoseIndex.RIGHT_SHOULDER) -
            midX(points, PoseIndex.LEFT_HIP, PoseIndex.RIGHT_HIP)) * aspectRatio
        val dy = midY(points, PoseIndex.LEFT_SHOULDER, PoseIndex.RIGHT_SHOULDER) -
            midY(points, PoseIndex.LEFT_HIP, PoseIndex.RIGHT_HIP)
        if (abs(dy) < 1e-6f) {
            return null
        }
        return Math.toDegrees(atan2(abs(dx), abs(dy)).toDouble()).toFloat()
    }

    fun visible(points: List<PosePoint>, vararg indices: Int, threshold: Float = 0.5f): Boolean =
        indices.all { points[it].visibility >= threshold }

    /**
     * 相机相对重力的横滚角（弧度）：优先双眼连线，不可见时退化为双耳连线。
     * 参考线水平时为 0；|roll| < 0.5° 视为噪声，|roll| > 20° 视为躺姿或极端机位，均返回 null。
     */
    fun cameraRollRadians(points: List<PosePoint>, aspectRatio: Float = 1f): Float? {
        val reference = referenceLine(points) ?: return null
        val first = points[reference.first]
        val second = points[reference.second]
        val raw = atan2(
            (second.y - first.y).toDouble(),
            ((second.x - first.x) * aspectRatio).toDouble()
        )
        // 左右眼/耳的索引顺序取决于成像是否镜像；把方向折到 (-90°, 90°]，水平线即 0°。
        val normalized = when {
            raw > Math.PI / 2 -> raw - Math.PI
            raw < -Math.PI / 2 -> raw + Math.PI
            else -> raw
        }
        val roll = normalized.toFloat()
        return when {
            abs(roll) < MIN_ROLL_RADIANS -> null
            abs(roll) > MAX_ROLL_RADIANS -> null
            else -> roll
        }
    }

    /**
     * 以参考线两点中点为轴心，把全部关键点绕 z 轴旋转 -[rollRadians]；z 与 visibility 不变。
     *
     * 必须在**像素尺度**上旋转：归一化空间里 x、y 单位不同，直接旋转等于剪切，
     * 转的是"看起来的角度"而不是物理角度。这里先把 x 乘 [aspectRatio] 还原成 y 的尺度，
     * 转完再除回去，返回的仍是 MediaPipe 的归一化坐标。
     */
    fun derotate(points: List<PosePoint>, rollRadians: Float, aspectRatio: Float = 1f): List<PosePoint> {
        val reference = referenceLine(points) ?: return points
        val pivotX = (points[reference.first].x + points[reference.second].x) / 2f
        val pivotY = (points[reference.first].y + points[reference.second].y) / 2f
        val angle = -rollRadians
        val cosAngle = cos(angle)
        val sinAngle = sin(angle)
        return points.map { point ->
            val dxPx = (point.x - pivotX) * aspectRatio
            val dyPx = point.y - pivotY
            point.copy(
                x = pivotX + (dxPx * cosAngle - dyPx * sinAngle) / aspectRatio,
                y = pivotY + dxPx * sinAngle + dyPx * cosAngle
            )
        }
    }

    private fun referenceLine(points: List<PosePoint>): Pair<Int, Int>? = when {
        visible(points, PoseIndex.LEFT_EYE, PoseIndex.RIGHT_EYE, threshold = REFERENCE_VISIBILITY) ->
            PoseIndex.LEFT_EYE to PoseIndex.RIGHT_EYE
        visible(points, PoseIndex.LEFT_EAR, PoseIndex.RIGHT_EAR, threshold = REFERENCE_VISIBILITY) ->
            PoseIndex.LEFT_EAR to PoseIndex.RIGHT_EAR
        else -> null
    }
}