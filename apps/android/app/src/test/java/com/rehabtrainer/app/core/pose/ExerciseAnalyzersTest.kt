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

        assertTrue(update.lastRep!!.issues.contains(RepIssue.LATERAL_LEAN))
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
        analyzer.update(stand(), timestampMs = 0)
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
        analyzer.update(stand(), timestampMs = 0)
        analyzer.update(liftedLeg(left = true, ankleY = 0.90f))
        val update = analyzer.update(stand())

        assertEquals(1, update.count)
        assertTrue(update.lastRep!!.issues.contains(RepIssue.INSUFFICIENT_RANGE))
    }

    @Test
    fun marchTrunkLeanIsFlagged() {
        val analyzer = MarchAnalyzer()
        analyzer.update(stand(), timestampMs = 0)
        analyzer.update(leaned(liftedLeg(left = true, ankleY = 0.70f)))
        val update = analyzer.update(leaned(stand()))

        assertTrue(update.lastRep!!.issues.contains(RepIssue.LATERAL_LEAN))
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
        analyzer.update(stand(), timestampMs = 0)
        analyzer.update(squat())
        assertEquals(0, analyzer.count)

        val update = analyzer.update(stand())
        assertEquals(1, update.count)
        assertTrue(update.lastRep!!.valid)
    }

    @Test
    fun shallowSquatIsFlagged() {
        val analyzer = SquatAnalyzer()
        analyzer.update(stand(), timestampMs = 0)
        analyzer.update(squat(hipY = 0.61f))
        val update = analyzer.update(stand())

        assertEquals(1, update.count)
        assertTrue(update.lastRep!!.issues.contains(RepIssue.INSUFFICIENT_RANGE))
    }

    @Test
    fun excessiveSquatDepthIsFlagged() {
        val analyzer = SquatAnalyzer()
        analyzer.update(stand(), timestampMs = 0)
        analyzer.update(squat(hipY = 0.72f))
        val update = analyzer.update(stand())

        assertEquals(1, update.count)
        assertTrue(update.lastRep!!.issues.contains(RepIssue.EXCESSIVE_DEPTH))
    }

    @Test
    fun kneeValgusIsFlagged() {
        val analyzer = SquatAnalyzer()
        analyzer.update(stand(), timestampMs = 0)
        analyzer.update(squat(leftKneeX = 0.50f, rightKneeX = 0.50f))
        val update = analyzer.update(stand())

        assertTrue(update.lastRep!!.issues.contains(RepIssue.KNEE_VALGUS))
    }

    @Test
    fun hipAsymmetryIsFlagged() {
        val analyzer = SquatAnalyzer()
        analyzer.update(stand(), timestampMs = 0)
        analyzer.update(squat(leftHipY = 0.64f, rightHipY = 0.68f))
        val update = analyzer.update(stand())

        assertTrue(update.lastRep!!.issues.contains(RepIssue.ASYMMETRY))
    }

    @Test
    fun squatTrunkLeanIsFlagged() {
        val analyzer = SquatAnalyzer()
        analyzer.update(stand(), timestampMs = 0)
        analyzer.update(leaned(squat()))
        val update = analyzer.update(leaned(stand()))

        assertTrue(update.lastRep!!.issues.contains(RepIssue.LATERAL_LEAN))
    }

    // ---------- 通用 ----------

    @Test
    fun resetClearsCountAndPendingState() {
        val analyzer = SquatAnalyzer()
        analyzer.update(stand(), timestampMs = 0)
        analyzer.update(squat())
        analyzer.update(stand())
        assertEquals(1, analyzer.count)

        analyzer.reset()

        assertEquals(0, analyzer.count)
        assertNull(analyzer.lastRep)
        analyzer.update(stand(), timestampMs = 1_000)
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

    // ---------- 姿势矫正 ----------

    @Test
    fun postureCountsAfterHoldingAlignedForThreeSeconds() {
        val analyzer = PostureAnalyzer()
        val aligned = stand()

        analyzer.update(aligned, timestampMs = 0)
        analyzer.update(aligned, timestampMs = 1_500)
        assertEquals(0, analyzer.count)

        val update = analyzer.update(aligned, timestampMs = 3_000)
        assertEquals(1, update.count)
        assertTrue(update.lastRep!!.valid)
        assertTrue(update.currentIssues.isEmpty())

        // 持续保持不重复计数。
        analyzer.update(aligned, timestampMs = 9_000)
        assertEquals(1, analyzer.count)
    }

    @Test
    fun postureShoulderTiltPreventsCountAndIsReportedLive() {
        val analyzer = PostureAnalyzer()
        val uneven = stand().apply {
            set(PoseIndex.LEFT_SHOULDER, PosePoint(0.45f, 0.36f))
        }

        val update = analyzer.update(uneven, timestampMs = 0)
        analyzer.update(uneven, timestampMs = 3_000)

        assertEquals(0, analyzer.count)
        assertTrue(update.currentIssues.contains(RepIssue.SHOULDER_UNEVEN))
    }

    @Test
    fun postureRequiresReAlignAfterBreakingTheHold() {
        val analyzer = PostureAnalyzer()
        val aligned = stand()
        val uneven = stand().apply {
            set(PoseIndex.LEFT_SHOULDER, PosePoint(0.45f, 0.36f))
        }

        analyzer.update(aligned, timestampMs = 0)
        analyzer.update(aligned, timestampMs = 2_000)
        analyzer.update(uneven, timestampMs = 2_500)
        analyzer.update(aligned, timestampMs = 3_000)
        assertEquals(0, analyzer.count)

        assertEquals(1, analyzer.update(aligned, timestampMs = 6_000).count)
    }

    @Test
    fun postureHeadOffsetAndLateralLeanAreReported() {
        val analyzer = PostureAnalyzer()
        val headOff = stand().apply {
            set(PoseIndex.NOSE, PosePoint(0.70f, 0.20f))
        }
        assertTrue(
            analyzer.update(headOff, timestampMs = 0).currentIssues
                .contains(RepIssue.HEAD_OFFSET)
        )

        val leaned = stand().apply {
            set(PoseIndex.LEFT_SHOULDER, PosePoint(0.55f, 0.30f))
            set(PoseIndex.RIGHT_SHOULDER, PosePoint(0.65f, 0.30f))
        }
        assertTrue(
            analyzer.update(leaned, timestampMs = 10).currentIssues
                .contains(RepIssue.LATERAL_LEAN)
        )
    }

    @Test
    fun postureResetClearsHoldAndCount() {
        val analyzer = PostureAnalyzer()
        analyzer.update(stand(), timestampMs = 0)
        analyzer.update(stand(), timestampMs = 3_000)
        assertEquals(1, analyzer.count)

        analyzer.reset()

        assertEquals(0, analyzer.count)
        assertNull(analyzer.lastRep)
        analyzer.update(stand(), timestampMs = 4_000)
        assertEquals(1, analyzer.update(stand(), timestampMs = 7_000).count)
    }

    @Test
    fun factoryCreatesPostureAnalyzer() {
        assertTrue(createExerciseAnalyzer(ExerciseKind.POSTURE) is PostureAnalyzer)
    }

    // ---------- 双上肢伸展保持（肩颈牵伸） ----------

    private fun stretch(
        leftWrist: PosePoint = PosePoint(0.45f, 0.05f),
        rightWrist: PosePoint = PosePoint(0.55f, 0.05f),
        leftElbow: PosePoint = PosePoint(0.45f, 0.15f),
        rightElbow: PosePoint = PosePoint(0.55f, 0.15f),
        leftShoulderY: Float = 0.30f
    ): MutableList<PosePoint> = stand().apply {
        set(PoseIndex.LEFT_SHOULDER, PosePoint(0.45f, leftShoulderY))
        set(PoseIndex.LEFT_ELBOW, leftElbow)
        set(PoseIndex.RIGHT_ELBOW, rightElbow)
        set(PoseIndex.LEFT_WRIST, leftWrist)
        set(PoseIndex.RIGHT_WRIST, rightWrist)
    }

    @Test
    fun stretchCountsAfterHoldingBothArmsUp() {
        val analyzer = StretchAnalyzer()
        analyzer.update(stretch(), timestampMs = 0)
        analyzer.update(stretch(), timestampMs = 1_500)
        assertEquals(0, analyzer.count)

        val update = analyzer.update(stretch(), timestampMs = 3_000)
        assertEquals(1, update.count)
        assertTrue(update.lastRep!!.valid)
        assertTrue(update.currentIssues.isEmpty())

        analyzer.update(stretch(), timestampMs = 9_000)
        assertEquals(1, analyzer.count)
    }

    @Test
    fun oneArmLowIsInsufficient() {
        val analyzer = StretchAnalyzer()
        val update = analyzer.update(
            stretch(rightWrist = PosePoint(0.55f, 0.32f)),
            timestampMs = 0
        )
        assertTrue(update.currentIssues.contains(RepIssue.INSUFFICIENT_RANGE))
        analyzer.update(stretch(rightWrist = PosePoint(0.55f, 0.32f)), timestampMs = 3_000)
        assertEquals(0, analyzer.count)
    }

    @Test
    fun bentElbowIsReported() {
        val analyzer = StretchAnalyzer()
        val update = analyzer.update(
            stretch(leftWrist = PosePoint(0.60f, 0.15f)),
            timestampMs = 0
        )
        assertTrue(update.currentIssues.contains(RepIssue.ELBOW_BENT))
    }

    @Test
    fun asymmetricRaiseIsReported() {
        val analyzer = StretchAnalyzer()
        val update = analyzer.update(
            stretch(rightWrist = PosePoint(0.55f, 0.22f)),
            timestampMs = 0
        )
        assertTrue(update.currentIssues.contains(RepIssue.ASYMMETRY))
    }

    @Test
    fun unevenShouldersAreReported() {
        val analyzer = StretchAnalyzer()
        val update = analyzer.update(stretch(leftShoulderY = 0.35f), timestampMs = 0)
        assertTrue(update.currentIssues.contains(RepIssue.SHOULDER_UNEVEN))
    }

    @Test
    fun stretchRequiresReHoldAfterLoweringArms() {
        val analyzer = StretchAnalyzer()
        analyzer.update(stretch(), timestampMs = 0)
        analyzer.update(stretch(rightWrist = PosePoint(0.55f, 0.32f)), timestampMs = 2_000)
        analyzer.update(stretch(), timestampMs = 3_000)
        assertEquals(0, analyzer.count)
        assertEquals(1, analyzer.update(stretch(), timestampMs = 6_000).count)
    }

    @Test
    fun stretchResetClearsHold() {
        val analyzer = StretchAnalyzer()
        analyzer.update(stretch(), timestampMs = 0)
        analyzer.update(stretch(), timestampMs = 3_000)
        assertEquals(1, analyzer.count)

        analyzer.reset()

        assertEquals(0, analyzer.count)
        assertNull(analyzer.lastRep)
        analyzer.update(stretch(), timestampMs = 4_000)
        assertEquals(1, analyzer.update(stretch(), timestampMs = 7_000).count)
    }

    @Test
    fun factoryCreatesStretchAnalyzer() {
        assertTrue(createExerciseAnalyzer(ExerciseKind.STRETCH) is StretchAnalyzer)
    }

    // ---------- 坐位抬腿 ----------

    private fun seatedLeg(leftAnkleY: Float = 1.00f, rightAnkleY: Float = 1.00f) =
        stand().apply {
            set(PoseIndex.LEFT_ANKLE, PosePoint(0.45f, leftAnkleY))
            set(PoseIndex.RIGHT_ANKLE, PosePoint(0.55f, rightAnkleY))
        }

    @Test
    fun seatedLegRaiseCountsAgainstBaseline() {
        val analyzer = SeatedLegRaiseAnalyzer()
        analyzer.update(seatedLeg(), timestampMs = 0) // establish baseline

        analyzer.update(seatedLeg(leftAnkleY = 0.55f), timestampMs = 500)
        val update = analyzer.update(seatedLeg(), timestampMs = 1_000)

        assertEquals(1, update.count)
        assertTrue(update.lastRep!!.valid)
    }

    @Test
    fun shallowSeatedLiftIsFlagged() {
        val analyzer = SeatedLegRaiseAnalyzer()
        analyzer.update(seatedLeg(), timestampMs = 0)
        analyzer.update(seatedLeg(leftAnkleY = 0.95f), timestampMs = 500)
        val update = analyzer.update(seatedLeg(), timestampMs = 1_000)

        assertEquals(1, update.count)
        assertTrue(update.lastRep!!.issues.contains(RepIssue.INSUFFICIENT_RANGE))
    }

    @Test
    fun seatedLegRaiseTrunkLeanIsFlagged() {
        val analyzer = SeatedLegRaiseAnalyzer()
        analyzer.update(seatedLeg(), timestampMs = 0)
        analyzer.update(leaned(seatedLeg(leftAnkleY = 0.55f)), timestampMs = 500)
        val update = analyzer.update(leaned(seatedLeg()), timestampMs = 1_000)

        assertTrue(update.lastRep!!.issues.contains(RepIssue.LATERAL_LEAN))
    }

    @Test
    fun seatedLegRaiseResetClearsBaseline() {
        val analyzer = SeatedLegRaiseAnalyzer()
        analyzer.update(seatedLeg(), timestampMs = 0)
        analyzer.update(seatedLeg(leftAnkleY = 0.55f), timestampMs = 500)
        assertEquals(1, analyzer.update(seatedLeg(), timestampMs = 1_000).count)

        analyzer.reset()
        assertEquals(0, analyzer.count)
        assertNull(analyzer.lastRep)
        analyzer.update(seatedLeg(), timestampMs = 2_000)
        analyzer.update(seatedLeg(leftAnkleY = 0.55f), timestampMs = 2_500)
        assertEquals(1, analyzer.update(seatedLeg(), timestampMs = 3_000).count)
    }

    @Test
    fun factoryCreatesSeatedLegRaiseAnalyzer() {
        assertTrue(
            createExerciseAnalyzer(ExerciseKind.SEATED_LEG_RAISE) is SeatedLegRaiseAnalyzer
        )
    }

    @Test
    fun postureToleratesBriefKeypointLossDuringHold() {
        val analyzer = PostureAnalyzer()
        analyzer.update(stand(), timestampMs = 0)
        analyzer.update(stand(), timestampMs = 2_500)
        val hidden = stand().apply {
            set(PoseIndex.LEFT_HIP, PosePoint(0.45f, 0.60f, visibility = 0.05f))
        }
        analyzer.update(hidden, timestampMs = 2_600)
        analyzer.update(stand(), timestampMs = 2_800)
        val update = analyzer.update(stand(), timestampMs = 3_100)

        assertEquals(1, update.count)
    }

    @Test
    fun marchDoesNotDoubleCountWhenBothLegsLiftTogether() {
        val analyzer = MarchAnalyzer()
        analyzer.update(stand(), timestampMs = 0)
        analyzer.update(seatedLeg(leftAnkleY = 0.70f, rightAnkleY = 0.70f), timestampMs = 500)
        val update = analyzer.update(stand(), timestampMs = 1_000)

        // 双腿同时抬落只算一步，避免把下蹲/并腿当成两次踏步。
        assertEquals(1, update.count)
    }

    @Test
    fun stretchJustAboveVisibilityThresholdIsNotKeypointIncomplete() {
        val analyzer = StretchAnalyzer()
        val frame = stretch().apply {
            set(PoseIndex.LEFT_WRIST, PosePoint(0.45f, 0.05f, visibility = 0.21f))
        }
        val update = analyzer.update(frame, timestampMs = 0)

        assertTrue(!update.currentIssues.contains(RepIssue.POSE_INCOMPLETE))
    }

    @Test
    fun squatUsesStandingBaselineNotAbsoluteRatio() {
        // 不同身高比例：把整体骨架纵向压缩后，基线跟随变化，仍能完成一次计数。
        val short = stand().map { point ->
            PosePoint(point.x, 0.5f + (point.y - 0.5f) * 0.8f, point.z, point.visibility)
        }
        val analyzer = SquatAnalyzer()
        analyzer.update(short, timestampMs = 0)
        val squatted = short.map { point ->
            PosePoint(point.x, if (point.y > 0.55f) 0.5f + (point.y - 0.5f) * 0.72f else point.y, point.z, point.visibility)
        }
        analyzer.update(squatted, timestampMs = 500)
        assertEquals(1, analyzer.update(short, timestampMs = 1_000).count)
    }
}
