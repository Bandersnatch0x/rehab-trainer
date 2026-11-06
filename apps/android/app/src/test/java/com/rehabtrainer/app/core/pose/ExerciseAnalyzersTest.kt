package com.rehabtrainer.app.core.pose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 规则法动作分析器单测：全部使用合成关键点，不依赖真机或 MediaPipe。
 *
 * 坐标：y 向下；肩 y=0.30、髋 y=0.60 → 躯干高度 0.30；站立时踝 y=1.00。
 */
class ExerciseAnalyzersTest {

    private fun stand(): MutableList<PosePoint> = MutableList(PoseIndex.LANDMARK_COUNT) {
        PosePoint(0.5f, 0.5f)
    }.apply {
        set(PoseIndex.LEFT_SHOULDER, PosePoint(0.45f, 0.30f))
        set(PoseIndex.RIGHT_SHOULDER, PosePoint(0.55f, 0.30f))
        set(PoseIndex.LEFT_HIP, PosePoint(0.45f, 0.60f))
        set(PoseIndex.RIGHT_HIP, PosePoint(0.55f, 0.60f))
        set(PoseIndex.LEFT_KNEE, PosePoint(0.45f, 0.80f))
        set(PoseIndex.RIGHT_KNEE, PosePoint(0.55f, 0.80f))
        set(PoseIndex.LEFT_ANKLE, PosePoint(0.45f, 1.00f))
        set(PoseIndex.RIGHT_ANKLE, PosePoint(0.55f, 1.00f))
        set(PoseIndex.RIGHT_WRIST, PosePoint(0.55f, 0.70f))
    }

    private fun raisedArm(amount: Float = 0.40f): MutableList<PosePoint> = stand().apply {
        set(PoseIndex.RIGHT_WRIST, PosePoint(0.55f, 0.30f - amount * 0.30f))
    }

    private fun leaned(base: MutableList<PosePoint>, offsetX: Float = 0.20f): MutableList<PosePoint> =
        base.apply {
            val shoulderY = base[PoseIndex.LEFT_SHOULDER].y
            set(PoseIndex.LEFT_SHOULDER, PosePoint(0.45f + offsetX, shoulderY))
            set(PoseIndex.RIGHT_SHOULDER, PosePoint(0.55f + offsetX, shoulderY))
        }

    // ---------- 抬举 ----------

    @Test
    fun armRaiseCountsOnlyAfterReturning() {
        val analyzer = ArmRaiseAnalyzer()
        analyzer.update(raisedArm())
        assertEquals(0, analyzer.count)

        val update = analyzer.update(stand())
        assertEquals(1, update.count)
        assertNotNull(update.lastRep)
        assertTrue(update.lastRep!!.valid)
    }

    @Test
    fun armRaiseJitterBetweenThresholdsDoesNotDoubleCount() {
        val analyzer = ArmRaiseAnalyzer()
        analyzer.update(raisedArm())
        analyzer.update(stand())
        analyzer.update(raisedArm(0.15f))
        analyzer.update(stand())

        assertEquals(1, analyzer.count)
    }

    @Test
    fun insufficientArmRaiseIsFlagged() {
        val analyzer = ArmRaiseAnalyzer()
        analyzer.update(raisedArm(amount = 0.32f))
        val update = analyzer.update(stand())

        assertEquals(1, update.count)
        assertTrue(update.lastRep!!.issues.contains(RepIssue.INSUFFICIENT_RANGE))
    }

    @Test
    fun armRaiseWithTrunkLeanIsFlagged() {
        val analyzer = ArmRaiseAnalyzer()
        analyzer.update(leaned(raisedArm()))
        val update = analyzer.update(leaned(stand()))

        assertTrue(update.lastRep!!.issues.contains(RepIssue.TRUNK_LEAN))
    }

    // ---------- 原地踏步 ----------

    private fun liftedLeg(left: Boolean, ankleY: Float): MutableList<PosePoint> = stand().apply {
        if (left) {
            set(PoseIndex.LEFT_ANKLE, PosePoint(0.45f, ankleY))
        } else {
            set(PoseIndex.RIGHT_ANKLE, PosePoint(0.55f, ankleY))
        }
    }

    @Test
    fun marchCountsAlternatingSteps() {
        val analyzer = MarchAnalyzer()
        analyzer.update(liftedLeg(left = true, ankleY = 0.70f))
        assertEquals(1, analyzer.update(stand()).count)

        analyzer.update(liftedLeg(left = false, ankleY = 0.70f))
        val update = analyzer.update(stand())

        assertEquals(2, update.count)
        assertTrue(update.lastRep!!.valid)
    }

    @Test
    fun shallowMarchIsFlagged() {
        val analyzer = MarchAnalyzer()
        analyzer.update(liftedLeg(left = true, ankleY = 0.90f))
        val update = analyzer.update(stand())

        assertEquals(1, update.count)
        assertTrue(update.lastRep!!.issues.contains(RepIssue.INSUFFICIENT_RANGE))
    }

    @Test
    fun marchTrunkLeanIsFlagged() {
        val analyzer = MarchAnalyzer()
        analyzer.update(leaned(liftedLeg(left = true, ankleY = 0.70f)))
        val update = analyzer.update(leaned(stand()))

        assertTrue(update.lastRep!!.issues.contains(RepIssue.TRUNK_LEAN))
    }

    // ---------- 靠墙微蹲 ----------

    private fun squat(
        hipY: Float = 0.66f,
        leftKneeX: Float = 0.45f,
        rightKneeX: Float = 0.55f,
        leftHipY: Float = hipY,
        rightHipY: Float = hipY
    ): MutableList<PosePoint> = stand().apply {
        // 下蹲时肩随髋同步下降，保持躯干高度不变（真实动作如此）。
        val midHipY = (leftHipY + rightHipY) / 2f
        val shoulderY = 0.30f + (midHipY - 0.60f)
        set(PoseIndex.LEFT_SHOULDER, PosePoint(0.45f, shoulderY))
        set(PoseIndex.RIGHT_SHOULDER, PosePoint(0.55f, shoulderY))
        set(PoseIndex.LEFT_HIP, PosePoint(0.45f, leftHipY))
        set(PoseIndex.RIGHT_HIP, PosePoint(0.55f, rightHipY))
        set(PoseIndex.LEFT_KNEE, PosePoint(leftKneeX, 0.80f))
        set(PoseIndex.RIGHT_KNEE, PosePoint(rightKneeX, 0.80f))
    }

    @Test
    fun squatCountsAfterStandingBackUp() {
        val analyzer = SquatAnalyzer()
        analyzer.update(squat())
        assertEquals(0, analyzer.count)

        val update = analyzer.update(stand())
        assertEquals(1, update.count)
        assertTrue(update.lastRep!!.valid)
    }

    @Test
    fun shallowSquatIsFlagged() {
        val analyzer = SquatAnalyzer()
        analyzer.update(squat(hipY = 0.63f))
        val update = analyzer.update(stand())

        assertEquals(1, update.count)
        assertTrue(update.lastRep!!.issues.contains(RepIssue.INSUFFICIENT_RANGE))
    }

    @Test
    fun excessiveSquatDepthIsFlagged() {
        val analyzer = SquatAnalyzer()
        analyzer.update(squat(hipY = 0.72f))
        val update = analyzer.update(stand())

        assertEquals(1, update.count)
        assertTrue(update.lastRep!!.issues.contains(RepIssue.EXCESSIVE_DEPTH))
    }

    @Test
    fun kneeValgusIsFlagged() {
        val analyzer = SquatAnalyzer()
        analyzer.update(squat(leftKneeX = 0.50f, rightKneeX = 0.50f))
        val update = analyzer.update(stand())

        assertTrue(update.lastRep!!.issues.contains(RepIssue.KNEE_VALGUS))
    }

    @Test
    fun hipAsymmetryIsFlagged() {
        val analyzer = SquatAnalyzer()
        analyzer.update(squat(leftHipY = 0.64f, rightHipY = 0.68f))
        val update = analyzer.update(stand())

        assertTrue(update.lastRep!!.issues.contains(RepIssue.ASYMMETRY))
    }

    @Test
    fun squatTrunkLeanIsFlagged() {
        val analyzer = SquatAnalyzer()
        analyzer.update(leaned(squat()))
        val update = analyzer.update(leaned(stand()))

        assertTrue(update.lastRep!!.issues.contains(RepIssue.TRUNK_LEAN))
    }

    // ---------- 通用 ----------

    @Test
    fun resetClearsCountAndPendingState() {
        val analyzer = SquatAnalyzer()
        analyzer.update(squat())
        analyzer.update(stand())
        assertEquals(1, analyzer.count)

        analyzer.reset()

        assertEquals(0, analyzer.count)
        assertNull(analyzer.lastRep)
        analyzer.update(squat())
        assertEquals(1, analyzer.update(stand()).count)
    }

    @Test
    fun lowVisibilityDoesNotAdvanceStateMachine() {
        val analyzer = SquatAnalyzer()
        val hidden = squat().apply {
            set(PoseIndex.LEFT_ANKLE, PosePoint(0.45f, 1.00f, visibility = 0.1f))
        }
        analyzer.update(hidden)
        assertEquals(0, analyzer.update(stand()).count)
    }

    @Test
    fun factoryCreatesMatchingAnalyzer() {
        assertTrue(createExerciseAnalyzer(ExerciseKind.ARM_RAISE) is ArmRaiseAnalyzer)
        assertTrue(createExerciseAnalyzer(ExerciseKind.MARCH) is MarchAnalyzer)
        assertTrue(createExerciseAnalyzer(ExerciseKind.SQUAT) is SquatAnalyzer)
    }
}