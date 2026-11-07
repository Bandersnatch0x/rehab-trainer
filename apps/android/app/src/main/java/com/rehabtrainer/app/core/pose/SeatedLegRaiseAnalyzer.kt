package com.rehabtrainer.app.core.pose

import kotlin.math.max
import kotlin.math.min

/**
 * 坐位抬腿分析器。
 *
 * 坐姿下踝-髋的垂直距离因人、因座椅高度差异很大，固定阈值不可用；
 * 因此先在线估计「未抬腿基线」，再按相对基线的抬升幅度计数：
 * - 基线：未抬腿时踝-髋垂直距离（按躯干高度归一化）的较大值；
 * - 抬腿：低于基线 [liftEnterRatio] 进入，回到基线 [liftExitRatio] 以内退出；
 * - 达标：本次最低点相对基线抬升至少 [minLiftRatio]；躯干明显前倾记问题。
 *
 * 阈值是工程预设，待专业签核；抬腿过程中基线保持不动，避免把抬腿本身当成新基线。
 */
class SeatedLegRaiseAnalyzer(
    private val liftEnterRatio: Float = 0.15f,
    private val liftExitRatio: Float = 0.08f,
    private val minLiftRatio: Float = 0.22f,
    private val maxLateralLean: Float = 15f,
    private val calibrationReps: Int = 0,
    knownAmplitude: Float? = null
) : ExerciseAnalyzer {

    private class LegState {
        var baseline: Float? = null
        val samples = mutableListOf<Float>()
        var lifted = false
        var minSpan = Float.MAX_VALUE
        var liftStartedMs = 0L
        var lastExitMs = 0L
    }

    private val legs = listOf(LegState(), LegState())
    private val calibration = AmplitudeCalibration(calibrationReps, knownAmplitude)
    private var repIndex = 0
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
                PoseIndex.LEFT_KNEE,
                PoseIndex.RIGHT_KNEE,
                threshold = 0.20f
            )
        ) {
            return AnalysisUpdate(count, lastRep, setOf(RepIssue.POSE_INCOMPLETE))
        }
        val torso = PoseGeometry.torsoHeight(points)
            ?: return AnalysisUpdate(count, lastRep, setOf(RepIssue.POSE_INCOMPLETE))
        val lean = PoseGeometry.lateralLeanDegrees(points, aspectRatio) ?: 0f

        // 坐位抬腿时膝比踝更靠近躯干、也更容易入镜；用膝-髋垂直距离作为抬腿信号。
        updateLeg(
            0,
            (points[PoseIndex.LEFT_KNEE].y - points[PoseIndex.LEFT_HIP].y) / torso,
            lean,
            timestampMs
        )
        updateLeg(
            1,
            (points[PoseIndex.RIGHT_KNEE].y - points[PoseIndex.RIGHT_HIP].y) / torso,
            lean,
            timestampMs
        )
        return AnalysisUpdate(count, lastRep, emptySet())
    }

    private fun updateLeg(index: Int, span: Float, lean: Float, timestampMs: Long) {
        val leg = legs[index]
        val baseline = leg.baseline
        if (baseline == null) {
            // 前 8 帧取中位作为基线，避免用单帧噪声/抬腿中的帧初始化。
            leg.samples.add(span)
            if (leg.samples.size >= BASELINE_SAMPLES) {
                leg.baseline = leg.samples.sorted()[leg.samples.size / 2]
            }
            return
        }
        val enterLift = calibration.amplitude()?.let { calibration.enterLift() } ?: liftEnterRatio
        val exitLift = calibration.amplitude()?.let { calibration.exitLift() } ?: liftExitRatio
        val minLift = calibration.amplitude()?.let { calibration.minValidLift() } ?: minLiftRatio
        if (!leg.lifted && span < baseline - enterLift) {
            if (leg.lastExitMs != 0L && timestampMs - leg.lastExitMs < REARM_MS) {
                return
            }
            leg.lifted = true
            leg.minSpan = span
            leg.liftStartedMs = timestampMs
            return
        }
        if (leg.lifted) {
            leg.minSpan = min(leg.minSpan, span)
            if (span > baseline - exitLift) {
                leg.lifted = false
                leg.lastExitMs = timestampMs
                // 抬腿持续时间过短视为抖动，不计一次；需完全回到基线附近再重新开始。
                if (leg.liftStartedMs != 0L && timestampMs - leg.liftStartedMs < MIN_LIFT_MS) {
                    return
                }
                if (span < baseline - exitLift) {
                    return
                }
                if (lastRepAtMs != 0L && timestampMs - lastRepAtMs < MIN_REP_INTERVAL_MS) {
                    return
                }
                lastRepAtMs = timestampMs
                val lift = baseline - leg.minSpan
                if (calibration.isCalibrating) {
                    calibration.record(lift)
                    return
                }
                val issues = buildSet {
                    if (lift < minLift) add(RepIssue.INSUFFICIENT_RANGE)
                    if (lean > maxLateralLean) add(RepIssue.LATERAL_LEAN)
                }
                repIndex += 1
                count += 1
                lastRep = RepResult(repIndex, issues.isEmpty(), issues)
            }
            return
        }
        // 未抬腿时基线缓慢双向自适应：明显更高立即上修，低一些则缓慢下修（防机位/坐姿漂移）。
        leg.baseline = if (span > baseline) span else max(span, baseline - BASELINE_DOWN_STEP)
    }

    override fun reset() {
        legs.forEach {
            it.baseline = null
            it.samples.clear()
            it.lifted = false
            it.minSpan = Float.MAX_VALUE
            it.liftStartedMs = 0L
            it.lastExitMs = 0L
        }
        repIndex = 0
        lastRepAtMs = 0L
        count = 0
        lastRep = null
    }

    private companion object {
        const val BASELINE_SAMPLES = 8
        const val MIN_REP_INTERVAL_MS = 500L
        const val MIN_LIFT_MS = 250L
        const val REARM_MS = 200L
    }
}