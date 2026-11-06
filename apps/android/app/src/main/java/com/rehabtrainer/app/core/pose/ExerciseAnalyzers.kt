package com.rehabtrainer.app.core.pose

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 规则法动作分析器（文献/工程预设，待临床签核；见 docs/clinical/literature-presets.md）。
 *
 * 坐标约定：MediaPipe 正面 2D，y 向下；x/y 归一化单位不同，跨维比较仅用于同量纲的相对判据。
 * 站立类动作的阈值以「自身站立基线」为参照，避免不同身高/腿长/机位距离导致阈值失效。
 */

private const val MIN_VISIBILITY = 0.20f

class ArmRaiseAnalyzer(
    private val raiseThreshold: Float = -0.30f,
    private val lowerThreshold: Float = -0.10f,
    private val minRaiseAmount: Float = 0.35f,
    private val maxLateralLean: Float = 12f
) : ExerciseAnalyzer {

    private var raised = false
    private var peakAmount = 0f
    private var raisedSide = 0 // 1=左, 2=右, 0=未抬
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
                threshold = MIN_VISIBILITY
            )
        ) {
            return AnalysisUpdate(count, lastRep, setOf(RepIssue.POSE_INCOMPLETE))
        }
        val torso = PoseGeometry.torsoHeight(points) ?: return AnalysisUpdate(count, lastRep, setOf(RepIssue.POSE_INCOMPLETE))
        val shoulderY = PoseGeometry.midY(
            points,
            PoseIndex.LEFT_SHOULDER,
            PoseIndex.RIGHT_SHOULDER
        )
        val leftVisible = points[PoseIndex.LEFT_WRIST].visibility >= MIN_VISIBILITY
        val rightVisible = points[PoseIndex.RIGHT_WRIST].visibility >= MIN_VISIBILITY
        if (!leftVisible && !rightVisible) {
            return AnalysisUpdate(count, lastRep, setOf(RepIssue.POSE_INCOMPLETE))
        }
        val leftRaise = if (leftVisible) (shoulderY - points[PoseIndex.LEFT_WRIST].y) / torso else Float.NEGATIVE_INFINITY
        val rightRaise = if (rightVisible) (shoulderY - points[PoseIndex.RIGHT_WRIST].y) / torso else Float.NEGATIVE_INFINITY
        val lean = PoseGeometry.lateralLeanDegrees(points) ?: 0f

        if (!raised) {
            // 左右任一腕上举都计数：选择当前抬起更高的一侧，后续以该侧是否放下作为结束判据。
            val side = when {
                leftVisible && rightVisible -> if (leftRaise >= rightRaise) 1 else 2
                leftVisible -> 1
                rightVisible -> 2
                else -> 0
            }
            if (side != 0) {
                val amount = if (side == 1) leftRaise else rightRaise
                if (-amount < raiseThreshold) {
                    raised = true
                    raisedSide = side
                    peakAmount = amount
                }
            }
        } else {
            val amount = if (raisedSide == 1) leftRaise else rightRaise
            if (amount.isFinite()) {
                peakAmount = max(peakAmount, amount)
                if (-amount > lowerThreshold) {
                    raised = false
                    val issues = buildSet {
                        if (peakAmount < minRaiseAmount) add(RepIssue.INSUFFICIENT_RANGE)
                        if (lean > maxLateralLean) add(RepIssue.LATERAL_LEAN)
                    }
                    repIndex += 1
                    count += 1
                    lastRep = RepResult(repIndex, issues.isEmpty(), issues)
                }
            }
        }
        return AnalysisUpdate(count, lastRep)
    }

    override fun reset() {
        raised = false
        peakAmount = 0f
        raisedSide = 0
        repIndex = 0
        count = 0
        lastRep = null
    }
}

class MarchAnalyzer(
    private val enterRatio: Float = 0.80f,
    private val exitRatio: Float = 0.90f,
    private val minLiftRatio: Float = 0.65f,
    private val maxLateralLean: Float = 12f
) : ExerciseAnalyzer {

    private class LegState {
        var baseline: Float? = null
        var lifted = false
        var peakSpan = Float.MAX_VALUE
    }

    private val legs = listOf(LegState(), LegState())
    private var repIndex = 0
    private var lastStepLeg = -1
    private var lastRepAtMs = 0L

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
            return AnalysisUpdate(count, lastRep, setOf(RepIssue.POSE_INCOMPLETE))
        }
        val torso = PoseGeometry.torsoHeight(points) ?: return AnalysisUpdate(count, lastRep, setOf(RepIssue.POSE_INCOMPLETE))
        val lean = PoseGeometry.lateralLeanDegrees(points) ?: 0f
        updateLeg(
            0,
            (points[PoseIndex.LEFT_ANKLE].y - points[PoseIndex.LEFT_HIP].y) / torso,
            lean,
            timestampMs
        )
        updateLeg(
            1,
            (points[PoseIndex.RIGHT_ANKLE].y - points[PoseIndex.RIGHT_HIP].y) / torso,
            lean,
            timestampMs
        )
        return AnalysisUpdate(count, lastRep)
    }

    private fun updateLeg(index: Int, span: Float, lean: Float, timestampMs: Long) {
        val leg = legs[index]
        val baseline = leg.baseline
        if (baseline == null) {
            leg.baseline = span
            return
        }
        if (!leg.lifted && span < baseline * enterRatio) {
            leg.lifted = true
            leg.peakSpan = span
            return
        }
        if (leg.lifted) {
            leg.peakSpan = min(leg.peakSpan, span)
            if (span > baseline * exitRatio) {
                leg.lifted = false
                // 交替约束：对侧腿仍在抬起时不计数，避免把双腿同时动作当成两步。
                if (!legs[1 - index].lifted) {
                    if (lastRepAtMs != 0L && timestampMs - lastRepAtMs < MIN_REP_INTERVAL_MS) {
                        return
                    }
                    lastRepAtMs = timestampMs
                    val issues = buildSet {
                        if (leg.peakSpan > baseline * minLiftRatio) add(RepIssue.INSUFFICIENT_RANGE)
                        if (lean > maxLateralLean) add(RepIssue.LATERAL_LEAN)
                    }
                    repIndex += 1
                    count += 1
                    lastStepLeg = index
                    lastRep = RepResult(repIndex, issues.isEmpty(), issues)
                }
            }
            return
        }
        // 站立期缓慢下修基线（机位/姿态漂移），只朝一个方向缓慢变化，避免被抬腿抬高。
        leg.baseline = max(span, baseline - BASELINE_DOWN_STEP)
    }

    override fun reset() {
        legs.forEach {
            it.baseline = null
            it.lifted = false
            it.peakSpan = Float.MAX_VALUE
        }
        repIndex = 0
        lastStepLeg = -1
        lastRepAtMs = 0L
        count = 0
        lastRep = null
    }
}

class SquatAnalyzer(
    private val enterRatio: Float = 0.98f,
    private val exitRatio: Float = 0.995f,
    private val validDepthRatio: Float = 0.97f,
    private val maxDepthRatio: Float = 0.82f,
    private val maxLateralLean: Float = 20f,
    private val maxValgusRatio: Float = 0.12f,
    private val maxHipTiltRatio: Float = 0.10f
) : ExerciseAnalyzer {

    private var baseline: Float? = null
    private var inSquat = false
    private var lastRepAtMs = 0L
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
            return AnalysisUpdate(count, lastRep, setOf(RepIssue.POSE_INCOMPLETE))
        }
        val torso = PoseGeometry.torsoHeight(points) ?: return AnalysisUpdate(count, lastRep, setOf(RepIssue.POSE_INCOMPLETE))
        val span = (
            PoseGeometry.midY(points, PoseIndex.LEFT_ANKLE, PoseIndex.RIGHT_ANKLE) -
                PoseGeometry.midY(points, PoseIndex.LEFT_HIP, PoseIndex.RIGHT_HIP)
            ) / torso
        val lean = PoseGeometry.lateralLeanDegrees(points) ?: 0f

        val base = baseline
        if (base == null) {
            baseline = span
            return AnalysisUpdate(count, lastRep)
        }

        if (!inSquat && span < base * enterRatio) {
            inSquat = true
            minSpan = span
            bottomIssues = bottomIssues(points, torso, lean)
        } else if (inSquat) {
            if (span < minSpan) {
                minSpan = span
                bottomIssues = bottomIssues(points, torso, lean)
            }
            if (span > base * exitRatio) {
                inSquat = false
                if (lastRepAtMs != 0L && timestampMs - lastRepAtMs < MIN_REP_INTERVAL_MS) {
                    return AnalysisUpdate(count, lastRep)
                }
                lastRepAtMs = timestampMs
                val issues = bottomIssues.toMutableSet()
                if (minSpan > base * validDepthRatio) issues.add(RepIssue.INSUFFICIENT_RANGE)
                if (minSpan < base * maxDepthRatio) issues.add(RepIssue.EXCESSIVE_DEPTH)
                repIndex += 1
                count += 1
                lastRep = RepResult(repIndex, issues.isEmpty(), issues)
            }
        } else {
            // 站立期缓慢下修基线，适应机位/姿态变化。
            baseline = max(span, base - BASELINE_DOWN_STEP)
        }
        return AnalysisUpdate(count, lastRep)
    }

    private fun bottomIssues(points: List<PosePoint>, torso: Float, lean: Float): Set<RepIssue> =
        buildSet {
            if (lean > maxLateralLean) add(RepIssue.LATERAL_LEAN)
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
        baseline = null
        inSquat = false
        lastRepAtMs = 0L
        minSpan = Float.MAX_VALUE
        bottomIssues = emptySet()
        repIndex = 0
        count = 0
        lastRep = null
    }
}

/** 站立基线每帧允许的最大下修量；过大基线会被抬腿本身拉低。 */
internal const val BASELINE_DOWN_STEP: Float = 0.003f

/** 两次计数之间的最小间隔，过滤抖动造成的连击。 */
internal const val MIN_REP_INTERVAL_MS: Long = 400L