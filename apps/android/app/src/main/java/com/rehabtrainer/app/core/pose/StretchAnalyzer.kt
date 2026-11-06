package com.rehabtrainer.app.core.pose

import kotlin.math.abs

/**
 * 双上肢伸展保持分析器（肩颈牵伸、坐位躯干伸展等）。
 *
 * 这类动作不是「右臂抬举」的重复计数，而是双上肢上举后的保持：
 * - 用肩宽归一化，避免坐姿/半身构图下躯干高度不可靠；
 * - 要求双腕同时高于肩、肘部接近伸直、双肩大致水平；
 * - 连续保持 [holdMs] 毫秒记一次「伸展保持次数」，放下或偏离后需重新保持。
 *
 * 阈值是工程/文献预设，待专业签核；单测覆盖幅度、肘屈、左右不对称与保持计时。
 */
class StretchAnalyzer(
    private val minRaiseRatio: Float = 0.45f,
    // 2D 投影下肘角会被前伸/后收压小；放宽到 110° 仍能识别明显屈肘，避免正常牵伸被判失败。
    private val minElbowAngle: Float = 110f,
    private val maxShoulderTiltRatio: Float = 0.25f,
    private val maxRaiseAsymmetry: Float = 0.75f,
    private val holdMs: Long = 3_000L,
    // 抬手/调整过程中偶发丢帧不立刻清零，避免保持计时被瞬时抖动打断。
    private val gapToleranceMs: Long = 700L
) : ExerciseAnalyzer {

    private var holdStartMs: Long? = null
    private var lastValidMs = 0L
    private var countedForCurrentHold = false
    private var repIndex = 0

    override var count: Int = 0
        private set
    override var lastRep: RepResult? = null
        private set

    override fun update(points: List<PosePoint>, timestampMs: Long): AnalysisUpdate {
        if (points.size < PoseIndex.LANDMARK_COUNT ||
            !PoseGeometry.visible(
                points,
                PoseIndex.LEFT_SHOULDER,
                PoseIndex.RIGHT_SHOULDER,
                PoseIndex.LEFT_ELBOW,
                PoseIndex.RIGHT_ELBOW,
                PoseIndex.LEFT_WRIST,
                PoseIndex.RIGHT_WRIST,
                threshold = 0.20f
            )
        ) {
            onInvalidFrame(timestampMs)
            return AnalysisUpdate(count, lastRep, setOf(RepIssue.POSE_INCOMPLETE))
        }

        val width = abs(
            points[PoseIndex.LEFT_SHOULDER].x - points[PoseIndex.RIGHT_SHOULDER].x
        )
        if (width < 1e-3f) {
            resetHold()
            return AnalysisUpdate(count, lastRep, setOf(RepIssue.POSE_INCOMPLETE))
        }
        val shoulderY = PoseGeometry.midY(
            points,
            PoseIndex.LEFT_SHOULDER,
            PoseIndex.RIGHT_SHOULDER
        )
        val leftRaise = (shoulderY - points[PoseIndex.LEFT_WRIST].y) / width
        val rightRaise = (shoulderY - points[PoseIndex.RIGHT_WRIST].y) / width
        val leftElbow = PoseGeometry.angleDegrees(
            points[PoseIndex.LEFT_SHOULDER],
            points[PoseIndex.LEFT_ELBOW],
            points[PoseIndex.LEFT_WRIST]
        ) ?: 0f
        val rightElbow = PoseGeometry.angleDegrees(
            points[PoseIndex.RIGHT_SHOULDER],
            points[PoseIndex.RIGHT_ELBOW],
            points[PoseIndex.RIGHT_WRIST]
        ) ?: 0f
        val shoulderTilt = abs(
            points[PoseIndex.LEFT_SHOULDER].y - points[PoseIndex.RIGHT_SHOULDER].y
        ) / width

        val issues = buildSet {
            if (leftRaise < minRaiseRatio || rightRaise < minRaiseRatio) {
                add(RepIssue.INSUFFICIENT_RANGE)
            }
            if (leftElbow < minElbowAngle || rightElbow < minElbowAngle) {
                add(RepIssue.ELBOW_BENT)
            }
            if (shoulderTilt > maxShoulderTiltRatio) add(RepIssue.SHOULDER_UNEVEN)
            if (abs(leftRaise - rightRaise) > maxRaiseAsymmetry) add(RepIssue.ASYMMETRY)
        }

        if (issues.isNotEmpty()) {
            // 双臂已明显放下（而非瞬时抖动）时立即结束本次保持，允许下一次重新计数。
            val clearlyDown = leftRaise < 0.05f && rightRaise < 0.05f
            if (clearlyDown) resetHold() else onInvalidFrame(timestampMs)
            return AnalysisUpdate(count, lastRep, issues)
        }
        lastValidMs = timestampMs

        if (holdStartMs == null) {
            holdStartMs = timestampMs
        }
        val heldMs = timestampMs - (holdStartMs ?: timestampMs)
        if (!countedForCurrentHold && heldMs >= holdMs) {
            countedForCurrentHold = true
            repIndex += 1
            count += 1
            lastRep = RepResult(repIndex, valid = true, issues = emptySet())
        }
        return AnalysisUpdate(count, lastRep, emptySet())
    }

    private fun onInvalidFrame(timestampMs: Long) {
        if (holdStartMs != null && timestampMs - lastValidMs <= gapToleranceMs) {
            return
        }
        resetHold()
    }

    private fun resetHold() {
        holdStartMs = null
        lastValidMs = 0L
        countedForCurrentHold = false
    }

    override fun reset() {
        resetHold()
        repIndex = 0
        count = 0
        lastRep = null
    }
}