package com.rehabtrainer.app.core.pose

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 规则法动作分析器（文献/工程预设，待临床签核）。
 *
 * 站立类动作阈值以自身站立基线为参照；可选的前几次「校准动作」只测量不计数，
 * 用患者实际幅度设定进入/退出/达标阈值（见 [AmplitudeCalibration]）。
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
    override val calibrationRemaining: Int get() = 0

    override fun update(
        points: List<PosePoint>,
        timestampMs: Long,
        aspectRatio: Float
    ): AnalysisUpdate {
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
        val torso = PoseGeometry.torsoHeight(points)
            ?: return AnalysisUpdate(count, lastRep, setOf(RepIssue.POSE_INCOMPLETE))
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
        val leftRaise =
            if (leftVisible) (shoulderY - points[PoseIndex.LEFT_WRIST].y) / torso else Float.NEGATIVE_INFINITY
        val rightRaise =
            if (rightVisible) (shoulderY - points[PoseIndex.RIGHT_WRIST].y) / torso else Float.NEGATIVE_INFINITY
        val lean = PoseGeometry.lateralLeanDegrees(points, aspectRatio) ?: 0f

        if (!raised) {
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
    private val maxLateralLean: Float = 12f,
    private val calibrationReps: Int = 0,
    knownAmplitude: Float? = null
) : ExerciseAnalyzer {

    private class LegState {
        var baseline: Float? = null
        var lifted = false
        var peakSpan = Float.MAX_VALUE
        var liftStartedMs = 0L
        var lastExitMs = 0L
    }

    private val legs = listOf(LegState(), LegState())
    private val calibration = AmplitudeCalibration(calibrationReps, knownAmplitude)
    private var repIndex = 0
    private var lastStepLeg = -1
    private var lastRepAtMs = 0L

    override var count: Int = 0
        private set
    override var lastRep: RepResult? = null
        private set
    override val calibrationRemaining: Int get() = calibration.remaining
    override val calibratedAmplitude: Float? get() = calibration.amplitude()

    override fun update(
        points: List<PosePoint>,
        timestampMs: Long,
        aspectRatio: Float
    ): AnalysisUpdate {
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
        val torso = PoseGeometry.torsoHeight(points)
            ?: return AnalysisUpdate(count, lastRep, setOf(RepIssue.POSE_INCOMPLETE))
        val lean = PoseGeometry.lateralLeanDegrees(points, aspectRatio) ?: 0f
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

    private fun enterLift(baseline: Float): Float =
        calibration.amplitude()?.let { calibration.enterLift() } ?: (baseline * (1f - enterRatio))

    private fun exitLift(baseline: Float): Float =
        calibration.amplitude()?.let { calibration.exitLift() } ?: (baseline * (1f - exitRatio))

    private fun validLift(baseline: Float): Float =
        calibration.amplitude()?.let { calibration.minValidLift() } ?: (baseline * (1f - minLiftRatio))

    private fun updateLeg(index: Int, span: Float, lean: Float, timestampMs: Long) {
        val leg = legs[index]
        val baseline = leg.baseline
        if (baseline == null) {
            leg.baseline = span
            return
        }
        if (!leg.lifted && span < baseline - enterLift(baseline)) {
            if (leg.lastExitMs != 0L && timestampMs - leg.lastExitMs < REARM_MS) {
                return
            }
            leg.lifted = true
            leg.peakSpan = span
            leg.liftStartedMs = timestampMs
            return
        }
        if (leg.lifted) {
            leg.peakSpan = min(leg.peakSpan, span)
            if (span > baseline - exitLift(baseline)) {
                leg.lifted = false
                leg.lastExitMs = timestampMs
                if (leg.liftStartedMs != 0L && timestampMs - leg.liftStartedMs < MIN_LIFT_MS) {
                    return
                }
                if (legs[1 - index].lifted) {
                    return
                }
                val lift = baseline - leg.peakSpan
                if (calibration.isCalibrating) {
                    calibration.record(lift)
                    return
                }
                if (lastRepAtMs != 0L && timestampMs - lastRepAtMs < MIN_REP_INTERVAL_MS) {
                    return
                }
                lastRepAtMs = timestampMs
                val issues = buildSet {
                    if (lift < validLift(baseline)) add(RepIssue.INSUFFICIENT_RANGE)
                    if (lean > maxLateralLean) add(RepIssue.LATERAL_LEAN)
                }
                repIndex += 1
                count += 1
                lastStepLeg = index
                lastRep = RepResult(repIndex, issues.isEmpty(), issues)
            }
            return
        }
        leg.baseline = max(span, baseline - BASELINE_DOWN_STEP)
    }

    override fun reset() {
        legs.forEach {
            it.baseline = null
            it.lifted = false
            it.peakSpan = Float.MAX_VALUE
            it.liftStartedMs = 0L
            it.lastExitMs = 0L
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
    private val maxHipTiltRatio: Float = 0.10f,
    private val calibrationReps: Int = 0,
    knownAmplitude: Float? = null
) : ExerciseAnalyzer {

    private var baseline: Float? = null
    private var inSquat = false
    private var lastRepAtMs = 0L
    private var squatStartedMs = 0L
    private var lastExitMs = 0L
    private var minSpan = Float.MAX_VALUE
    private var bottomIssues: Set<RepIssue> = emptySet()
    private var repIndex = 0
    private val calibration = AmplitudeCalibration(calibrationReps, knownAmplitude)

    override var count: Int = 0
        private set
    override var lastRep: RepResult? = null
        private set
    override val calibrationRemaining: Int get() = calibration.remaining
    override val calibratedAmplitude: Float? get() = calibration.amplitude()

    override fun update(
        points: List<PosePoint>,
        timestampMs: Long,
        aspectRatio: Float
    ): AnalysisUpdate {
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
        val torso = PoseGeometry.torsoHeight(points)
            ?: return AnalysisUpdate(count, lastRep, setOf(RepIssue.POSE_INCOMPLETE))
        val span = (
            PoseGeometry.midY(points, PoseIndex.LEFT_ANKLE, PoseIndex.RIGHT_ANKLE) -
                PoseGeometry.midY(points, PoseIndex.LEFT_HIP, PoseIndex.RIGHT_HIP)
            ) / torso
        val lean = PoseGeometry.lateralLeanDegrees(points, aspectRatio) ?: 0f

        val base = baseline
        if (base == null) {
            baseline = span
            return AnalysisUpdate(count, lastRep)
        }
        val enterLift = calibration.amplitude()?.let { calibration.enterLift() }
            ?: (base * (1f - enterRatio))
        val exitLift = calibration.amplitude()?.let { calibration.exitLift() }
            ?: (base * (1f - exitRatio))
        val minDepth = calibration.amplitude()?.let { calibration.minValidLift() }
            ?: (base * (1f - validDepthRatio))

        if (!inSquat && span < base - enterLift) {
            if (lastExitMs != 0L && timestampMs - lastExitMs < REARM_MS) {
                return AnalysisUpdate(count, lastRep)
            }
            inSquat = true
            squatStartedMs = timestampMs
            minSpan = span
            bottomIssues = bottomIssues(points, torso, lean)
        } else if (inSquat) {
            if (span < minSpan) {
                minSpan = span
                bottomIssues = bottomIssues(points, torso, lean)
            }
            if (span > base - exitLift) {
                inSquat = false
                lastExitMs = timestampMs
                if (squatStartedMs != 0L && timestampMs - squatStartedMs < MIN_LIFT_MS) {
                    return AnalysisUpdate(count, lastRep)
                }
                val depth = base - minSpan
                if (calibration.isCalibrating) {
                    calibration.record(depth)
                    return AnalysisUpdate(count, lastRep)
                }
                if (lastRepAtMs != 0L && timestampMs - lastRepAtMs < MIN_REP_INTERVAL_MS) {
                    return AnalysisUpdate(count, lastRep)
                }
                lastRepAtMs = timestampMs
                val issues = bottomIssues.toMutableSet()
                if (depth < minDepth) issues.add(RepIssue.INSUFFICIENT_RANGE)
                if (depth > base * (1f - maxDepthRatio)) issues.add(RepIssue.EXCESSIVE_DEPTH)
                repIndex += 1
                count += 1
                lastRep = RepResult(repIndex, issues.isEmpty(), issues)
            }
        } else {
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
        squatStartedMs = 0L
        lastExitMs = 0L
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

/** 单次动作的最短持续时间与重新武装间隔，过滤瞬时抖动。 */
internal const val MIN_LIFT_MS: Long = 250L
internal const val REARM_MS: Long = 200L