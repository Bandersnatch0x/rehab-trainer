package com.rehabtrainer.app.core.pose

import kotlin.math.abs

/**
 * 站立位脊柱对齐的规则法分析器。
 *
 * 以正面摄像头可测的四个指标判定「保持达标」：
 * - 左右肩高度差、左右髋高度差；
 * - 肩中点相对髋中点的侧向偏移；
 * - 头部（鼻尖）相对肩中点的侧向偏移。
 *
 * 连续保持 [holdMs] 毫秒记一次「保持次数」；一旦偏离需重新保持，
 * 避免持续站立被重复计数。阈值是工程/文献预设，待专业签核。
 */
class PostureAnalyzer(
    private val maxShoulderTiltRatio: Float = 0.06f,
    private val maxHipTiltRatio: Float = 0.06f,
    private val maxLateralLeanRatio: Float = 0.08f,
    private val maxHeadOffsetRatio: Float = 0.10f,
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
                PoseIndex.NOSE,
                PoseIndex.LEFT_SHOULDER,
                PoseIndex.RIGHT_SHOULDER,
                PoseIndex.LEFT_HIP,
                PoseIndex.RIGHT_HIP,
                threshold = 0.5f
            )
        ) {
            holdStartMs = null
            countedForCurrentHold = false
            return AnalysisUpdate(count, lastRep, setOf(RepIssue.POSE_INCOMPLETE))
        }
        val torso = PoseGeometry.torsoHeight(points)
            ?: return AnalysisUpdate(count, lastRep, setOf(RepIssue.POSE_INCOMPLETE))

        val shoulderTilt = abs(
            points[PoseIndex.LEFT_SHOULDER].y - points[PoseIndex.RIGHT_SHOULDER].y
        ) / torso
        val hipTilt = abs(
            points[PoseIndex.LEFT_HIP].y - points[PoseIndex.RIGHT_HIP].y
        ) / torso
        val shoulderMidX = PoseGeometry.midX(
            points,
            PoseIndex.LEFT_SHOULDER,
            PoseIndex.RIGHT_SHOULDER
        )
        val hipMidX = PoseGeometry.midX(points, PoseIndex.LEFT_HIP, PoseIndex.RIGHT_HIP)
        val lateralLean = abs(shoulderMidX - hipMidX) / torso
        val headOffset = abs(points[PoseIndex.NOSE].x - shoulderMidX) / torso

        val issues = buildSet {
            if (shoulderTilt > maxShoulderTiltRatio) add(RepIssue.SHOULDER_UNEVEN)
            if (hipTilt > maxHipTiltRatio) add(RepIssue.HIP_UNEVEN)
            if (lateralLean > maxLateralLeanRatio) add(RepIssue.LATERAL_LEAN)
            if (headOffset > maxHeadOffsetRatio) add(RepIssue.HEAD_OFFSET)
        }

        if (issues.isNotEmpty()) {
            holdStartMs = null
            countedForCurrentHold = false
            return AnalysisUpdate(count, lastRep, issues)
        }

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

    override fun reset() {
        holdStartMs = null
        countedForCurrentHold = false
        repIndex = 0
        count = 0
        lastRep = null
    }
}