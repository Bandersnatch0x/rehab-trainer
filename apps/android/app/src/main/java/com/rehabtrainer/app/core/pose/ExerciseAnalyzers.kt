package com.rehabtrainer.app.core.pose

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 规则法动作分析器（文献/工程预设，待临床签核；见 docs/clinical/literature-presets.md）。
 *
 * 只使用正面摄像头可见的 2D 信号：
 * - 抬举：腕相对肩的垂直偏移；
 * - 踏步：单侧踝相对同侧髋的垂直距离（抬腿时缩短）；
 * - 微蹲：髋相对踝的垂直距离（下蹲时缩短）与膝/踝横向偏移。
 *
 * 阈值是保守默认值，不做诊断；单测用合成关键点序列验证状态机与问题码。
 */

private const val MIN_VISIBILITY = 0.5f

class ArmRaiseAnalyzer(
    private val raiseThreshold: Float = -0.30f,
    private val lowerThreshold: Float = -0.10f,
    private val minRaiseAmount: Float = 0.35f,
    private val maxTrunkLean: Float = 12f
) : ExerciseAnalyzer {

    private var raised = false
    private var peakAmount = 0f
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
                PoseIndex.LEFT_HIP,
                PoseIndex.RIGHT_HIP,
                PoseIndex.RIGHT_WRIST,
                threshold = MIN_VISIBILITY
            )
        ) {
            return AnalysisUpdate(count, lastRep)
        }
        val torso = PoseGeometry.torsoHeight(points) ?: return AnalysisUpdate(count, lastRep)
        val shoulderY = PoseGeometry.midY(
            points,
            PoseIndex.LEFT_SHOULDER,
            PoseIndex.RIGHT_SHOULDER
        )
        val offset = (points[PoseIndex.RIGHT_WRIST].y - shoulderY) / torso
        val lean = PoseGeometry.trunkLeanDegrees(points) ?: 0f

        if (!raised && offset < raiseThreshold) {
            raised = true
            peakAmount = -offset
        } else if (raised) {
            peakAmount = max(peakAmount, -offset)
            if (offset > lowerThreshold) {
                raised = false
                val issues = buildSet {
                    if (peakAmount < minRaiseAmount) add(RepIssue.INSUFFICIENT_RANGE)
                    if (lean > maxTrunkLean) add(RepIssue.TRUNK_LEAN)
                }
                repIndex += 1
                count += 1
                lastRep = RepResult(repIndex, issues.isEmpty(), issues)
            }
        }
        return AnalysisUpdate(count, lastRep)
    }

    override fun reset() {
        raised = false
        peakAmount = 0f
        repIndex = 0
        count = 0
        lastRep = null
    }
}

class MarchAnalyzer(
    private val enterSpan: Float = 1.05f,
    private val exitSpan: Float = 1.20f,
    private val minLiftSpan: Float = 0.95f,
    private val maxTrunkLean: Float = 12f
) : ExerciseAnalyzer {

    private class LegState {
        var lifted = false
        var peakSpan = Float.MAX_VALUE
    }

    private val legs = listOf(LegState(), LegState())
    private var repIndex = 0

    override var count: Int = 0
        private set
    override var lastRep: RepResult? = null
        private set

    override fun update(points: List<PosePoint>, timestampMs: Long): AnalysisUpdate {
        if (points.size < PoseIndex.LANDMARK_COUNT ||
            !PoseGeometry.visible(
                points,
                PoseIndex.LEFT_HIP,
                PoseIndex.RIGHT_HIP,
                PoseIndex.LEFT_ANKLE,
                PoseIndex.RIGHT_ANKLE,
                threshold = MIN_VISIBILITY
            )
        ) {
            return AnalysisUpdate(count, lastRep)
        }
        val torso = PoseGeometry.torsoHeight(points) ?: return AnalysisUpdate(count, lastRep)
        val lean = PoseGeometry.trunkLeanDegrees(points) ?: 0f
        updateLeg(
            0,
            (points[PoseIndex.LEFT_ANKLE].y - points[PoseIndex.LEFT_HIP].y) / torso,
            lean
        )
        updateLeg(
            1,
            (points[PoseIndex.RIGHT_ANKLE].y - points[PoseIndex.RIGHT_HIP].y) / torso,
            lean
        )
        return AnalysisUpdate(count, lastRep)
    }

    private fun updateLeg(index: Int, span: Float, lean: Float) {
        val leg = legs[index]
        if (!leg.lifted && span < enterSpan) {
            leg.lifted = true
            leg.peakSpan = span
        } else if (leg.lifted) {
            leg.peakSpan = min(leg.peakSpan, span)
            if (span > exitSpan) {
                leg.lifted = false
                val issues = buildSet {
                    if (leg.peakSpan > minLiftSpan) add(RepIssue.INSUFFICIENT_RANGE)
                    if (lean > maxTrunkLean) add(RepIssue.TRUNK_LEAN)
                }
                repIndex += 1
                count += 1
                lastRep = RepResult(repIndex, issues.isEmpty(), issues)
            }
        }
    }

    override fun reset() {
        legs.forEach {
            it.lifted = false
            it.peakSpan = Float.MAX_VALUE
        }
        repIndex = 0
        count = 0
        lastRep = null
    }
}

class SquatAnalyzer(
    private val enterSpan: Float = 1.24f,
    private val exitSpan: Float = 1.30f,
    private val validMinSpan: Float = 1.00f,
    private val validMaxSpan: Float = 1.18f,
    private val maxTrunkLean: Float = 20f,
    private val maxValgusRatio: Float = 0.12f,
    private val maxHipTiltRatio: Float = 0.10f
) : ExerciseAnalyzer {

    private var inSquat = false
    private var minSpan = Float.MAX_VALUE
    private var bottomIssues: Set<RepIssue> = emptySet()
    private var repIndex = 0

    override var count: Int = 0
        private set
    override var lastRep: RepResult? = null
        private set

    override fun update(points: List<PosePoint>, timestampMs: Long): AnalysisUpdate {
        if (points.size < PoseIndex.LANDMARK_COUNT ||
            !PoseGeometry.visible(
                points,
                PoseIndex.LEFT_HIP,
                PoseIndex.RIGHT_HIP,
                PoseIndex.LEFT_KNEE,
                PoseIndex.RIGHT_KNEE,
                PoseIndex.LEFT_ANKLE,
                PoseIndex.RIGHT_ANKLE,
                threshold = MIN_VISIBILITY
            )
        ) {
            return AnalysisUpdate(count, lastRep)
        }
        val torso = PoseGeometry.torsoHeight(points) ?: return AnalysisUpdate(count, lastRep)
        val span = (
            PoseGeometry.midY(points, PoseIndex.LEFT_ANKLE, PoseIndex.RIGHT_ANKLE) -
                PoseGeometry.midY(points, PoseIndex.LEFT_HIP, PoseIndex.RIGHT_HIP)
            ) / torso
        val lean = PoseGeometry.trunkLeanDegrees(points) ?: 0f

        if (!inSquat && span < enterSpan) {
            inSquat = true
            minSpan = span
            bottomIssues = bottomIssues(points, torso, lean)
        } else if (inSquat) {
            if (span < minSpan) {
                minSpan = span
                bottomIssues = bottomIssues(points, torso, lean)
            }
            if (span > exitSpan) {
                inSquat = false
                val issues = bottomIssues.toMutableSet()
                if (minSpan > validMaxSpan) addDepthIssue(issues, RepIssue.INSUFFICIENT_RANGE)
                if (minSpan < validMinSpan) addDepthIssue(issues, RepIssue.EXCESSIVE_DEPTH)
                repIndex += 1
                count += 1
                lastRep = RepResult(repIndex, issues.isEmpty(), issues)
            }
        }
        return AnalysisUpdate(count, lastRep)
    }

    private fun addDepthIssue(issues: MutableSet<RepIssue>, issue: RepIssue) {
        issues.add(issue)
    }

    private fun bottomIssues(
        points: List<PosePoint>,
        torso: Float,
        lean: Float
    ): Set<RepIssue> = buildSet {
        if (lean > maxTrunkLean) add(RepIssue.TRUNK_LEAN)
        if (abs(points[PoseIndex.LEFT_KNEE].x - points[PoseIndex.LEFT_ANKLE].x) >
            maxValgusRatio * torso ||
            abs(points[PoseIndex.RIGHT_KNEE].x - points[PoseIndex.RIGHT_ANKLE].x) >
            maxValgusRatio * torso
        ) {
            add(RepIssue.KNEE_VALGUS)
        }
        if (abs(points[PoseIndex.LEFT_HIP].y - points[PoseIndex.RIGHT_HIP].y) >
            maxHipTiltRatio * torso
        ) {
            add(RepIssue.ASYMMETRY)
        }
    }

    override fun reset() {
        inSquat = false
        minSpan = Float.MAX_VALUE
        bottomIssues = emptySet()
        repIndex = 0
        count = 0
        lastRep = null
    }
}