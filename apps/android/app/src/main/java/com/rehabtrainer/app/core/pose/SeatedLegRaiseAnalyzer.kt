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
    private val maxTrunkLean: Float = 15f
) : ExerciseAnalyzer {

    private class LegState {
        var baseline: Float? = null
        var lifted = false
        var minSpan = Float.MAX_VALUE
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
                threshold = 0.5f
            )
        ) {
            return AnalysisUpdate(count, lastRep, setOf(RepIssue.POSE_INCOMPLETE))
        }
        val torso = PoseGeometry.torsoHeight(points)
            ?: return AnalysisUpdate(count, lastRep, setOf(RepIssue.POSE_INCOMPLETE))
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
        return AnalysisUpdate(count, lastRep, emptySet())
    }

    private fun updateLeg(index: Int, span: Float, lean: Float) {
        val leg = legs[index]
        val baseline = leg.baseline
        if (baseline == null) {
            leg.baseline = span
            return
        }
        if (!leg.lifted && span < baseline - liftEnterRatio) {
            leg.lifted = true
            leg.minSpan = span
            return
        }
        if (leg.lifted) {
            leg.minSpan = min(leg.minSpan, span)
            if (span > baseline - liftExitRatio) {
                leg.lifted = false
                val lift = baseline - leg.minSpan
                val issues = buildSet {
                    if (lift < minLiftRatio) add(RepIssue.INSUFFICIENT_RANGE)
                    if (lean > maxTrunkLean) add(RepIssue.TRUNK_LEAN)
                }
                repIndex += 1
                count += 1
                lastRep = RepResult(repIndex, issues.isEmpty(), issues)
            }
            return
        }
        // 未抬腿时缓慢上修基线，适应座椅/机位变化；只在明显更高时更新。
        leg.baseline = max(baseline, span)
    }

    override fun reset() {
        legs.forEach {
            it.baseline = null
            it.lifted = false
            it.minSpan = Float.MAX_VALUE
        }
        repIndex = 0
        count = 0
        lastRep = null
    }
}