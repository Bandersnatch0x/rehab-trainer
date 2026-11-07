package com.rehabtrainer.app.core.pose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 机位横滚补偿：合成关键点序列，验证未校正时误报侧倾、回正后正常计数。
 *
 * 坐标：y 向下；肩 y=0.30、髋 y=0.60 → 躯干高度 0.30；站立时踝 y=1.00。
 * 旋转按图像坐标系（x 右、y 下）执行，正角为屏幕上的顺时针。
 */
class CameraRollCompensationTest {

    private fun stand(): List<PosePoint> = MutableList(PoseIndex.LANDMARK_COUNT) {
        PosePoint(0.5f, 0.5f)
    }.apply {
        set(PoseIndex.LEFT_EYE, PosePoint(0.47f, 0.20f))
        set(PoseIndex.RIGHT_EYE, PosePoint(0.53f, 0.20f))
        set(PoseIndex.LEFT_EAR, PosePoint(0.44f, 0.22f))
        set(PoseIndex.RIGHT_EAR, PosePoint(0.56f, 0.22f))
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

    private fun liftedLeftLeg(base: List<PosePoint>, ankleY: Float = 0.70f): List<PosePoint> =
        base.mapIndexed { index, point ->
            if (index == PoseIndex.LEFT_ANKLE) point.copy(y = ankleY) else point
        }

    private fun rotate(points: List<PosePoint>, degrees: Float): List<PosePoint> {
        val radians = Math.toRadians(degrees.toDouble())
        val cosAngle = cos(radians).toFloat()
        val sinAngle = sin(radians).toFloat()
        val pivotX = 0.5f
        val pivotY = 0.5f
        return points.map { point ->
            val dx = point.x - pivotX
            val dy = point.y - pivotY
            point.copy(
                x = pivotX + dx * cosAngle - dy * sinAngle,
                y = pivotY + dx * sinAngle + dy * cosAngle
            )
        }
    }

    private fun marchRep(
        analyzer: MarchAnalyzer,
        upright: List<PosePoint>,
        lifted: List<PosePoint>
    ): AnalysisUpdate {
        analyzer.update(upright, timestampMs = 0)
        analyzer.update(lifted, timestampMs = 1_000)
        return analyzer.update(upright, timestampMs = 2_000)
    }

    private fun distance(first: PosePoint, second: PosePoint): Float {
        val dx = first.x - second.x
        val dy = first.y - second.y
        return sqrt(dx * dx + dy * dy)
    }

    // ---------- 缺陷复现与校正 ----------

    @Test
    fun rolledUprightMarchIsReportedAsLateralLean() {
        // 阈值 12°：10° 的刚体旋转不足以触发侧倾，用 15° 复现机位倾斜造成的误报。
        val upright = rotate(stand(), -15f)
        val lifted = rotate(liftedLeftLeg(stand()), -15f)
        val analyzer = MarchAnalyzer(calibrationReps = 0, maxLateralLean = 12f)

        val update = marchRep(analyzer, upright, lifted)

        assertEquals(1, update.count)
        assertFalse(update.lastRep!!.valid)
        assertTrue(update.lastRep!!.issues.contains(RepIssue.LATERAL_LEAN))
    }

    @Test
    fun derotatedMarchCountsAndClearsLateralLean() {
        val upright = rotate(stand(), -15f)
        val lifted = rotate(liftedLeftLeg(stand()), -15f)
        val roll = PoseGeometry.cameraRollRadians(upright)!!
        val analyzer = MarchAnalyzer(calibrationReps = 0, maxLateralLean = 12f)

        val update = marchRep(
            analyzer,
            PoseGeometry.derotate(upright, roll),
            PoseGeometry.derotate(lifted, roll)
        )

        assertEquals(1, update.count)
        assertTrue(update.lastRep!!.valid)
        assertFalse(update.lastRep!!.issues.contains(RepIssue.LATERAL_LEAN))
    }

    // ---------- 横滚角测量 ----------

    @Test
    fun cameraRollRadiansMeasuresTenDegreeTilt() {
        assertNull(PoseGeometry.cameraRollRadians(stand()))

        val roll = PoseGeometry.cameraRollRadians(rotate(stand(), -10f))
        assertTrue(roll != null)
        val degrees = Math.toDegrees(roll!!.toDouble()).toFloat()
        assertEquals(10f, abs(degrees), 0.5f)
    }

    @Test
    fun cameraRollRadiansIgnoresReferencePointOrder() {
        val rolled = rotate(stand(), -10f)
        val swapped = rolled.mapIndexed { index, point ->
            when (index) {
                PoseIndex.LEFT_EYE -> rolled[PoseIndex.RIGHT_EYE]
                PoseIndex.RIGHT_EYE -> rolled[PoseIndex.LEFT_EYE]
                else -> point
            }
        }

        assertEquals(
            PoseGeometry.cameraRollRadians(rolled)!!,
            PoseGeometry.cameraRollRadians(swapped)!!,
            1e-4f
        )
    }

    @Test
    fun cameraRollRadiansReturnsNullWhenReferencePointsAreHidden() {
        val hidden = rotate(stand(), -10f).mapIndexed { index, point ->
            if (index == PoseIndex.LEFT_EYE ||
                index == PoseIndex.RIGHT_EYE ||
                index == PoseIndex.LEFT_EAR ||
                index == PoseIndex.RIGHT_EAR
            ) {
                point.copy(visibility = 0f)
            } else {
                point
            }
        }

        assertNull(PoseGeometry.cameraRollRadians(hidden))
    }

    @Test
    fun cameraRollRadiansFallsBackToEarsWhenEyesAreHidden() {
        val hiddenEyes = rotate(stand(), -10f).mapIndexed { index, point ->
            if (index == PoseIndex.LEFT_EYE || index == PoseIndex.RIGHT_EYE) {
                point.copy(visibility = 0f)
            } else {
                point
            }
        }

        val roll = PoseGeometry.cameraRollRadians(hiddenEyes)
        assertTrue(roll != null)
        val degrees = Math.toDegrees(roll!!.toDouble()).toFloat()
        assertEquals(10f, abs(degrees), 0.5f)
    }

    @Test
    fun cameraRollRadiansReturnsNullBeyondUpperLimit() {
        assertNull(PoseGeometry.cameraRollRadians(rotate(stand(), 35f)))
    }

    // ---------- 校正的等距性质 ----------

    @Test
    fun derotatePreservesDistanceZAndVisibility() {
        val rolled = rotate(stand(), -10f)
        val roll = PoseGeometry.cameraRollRadians(rolled)!!
        val corrected = PoseGeometry.derotate(rolled, roll)

        assertEquals(
            distance(rolled[PoseIndex.LEFT_SHOULDER], rolled[PoseIndex.LEFT_HIP]),
            distance(corrected[PoseIndex.LEFT_SHOULDER], corrected[PoseIndex.LEFT_HIP]),
            1e-4f
        )
        rolled.forEachIndexed { index, point ->
            assertEquals(point.z, corrected[index].z, 0f)
            assertEquals(point.visibility, corrected[index].visibility, 0f)
        }
    }
}
