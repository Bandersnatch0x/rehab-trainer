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
import kotlin.math.tan

/**
 * 机位横滚补偿。
 *
 * **基准真值必须建在像素空间**：MediaPipe 的归一化坐标是各向异性的（x 除以帧宽、y 除以帧高），
 * 在归一化空间里「旋转 15°」在像素空间是剪切，只在正方形帧上才是 15°。
 * 所以本文件先按像素造骨架、再除以 (宽, 高) 归一化，最后才比较角度。
 *
 * 骨架：眼距 0.10·H、肩宽 0.18·H、躯干 0.30·H，居中于画面；y 向下，正角为屏幕顺时针。
 */
class CameraRollCompensationTest {

    private data class Frame(val width: Int, val height: Int) {
        val aspect: Float get() = PoseGeometry.aspectRatioOf(width, height)
        val cx: Float get() = width / 2f
        val cy: Float get() = height / 2f
    }

    private val portrait = Frame(720, 1280)
    private val landscape = Frame(1280, 720)
    private val square = Frame(1000, 1000)
    private val frames get() = listOf(portrait, landscape, square)

    /** 像素空间：站立骨架，[leanDeg] 是躯干相对垂直的物理倾角。 */
    private fun standPixels(frame: Frame, leanDeg: Float = 0f): Map<Int, Pair<Float, Float>> {
        val lean = Math.toRadians(leanDeg.toDouble())
        val rise = 0.30f * frame.height
        // 髋中点在画面中心下方，肩中点由髋中点沿倾斜方向向上推 rise。
        val hipY = frame.cy + 0.15f * frame.height
        val shoulderY = (hipY - rise * cos(lean)).toFloat()
        val shoulderX = (frame.cx + rise * sin(lean)).toFloat()
        val halfShoulder = 0.09f * frame.height
        val halfEye = 0.05f * frame.height
        val eyeY = shoulderY - 0.08f * frame.height
        fun at(x: Float, y: Float) = x to y
        return mapOf(
            PoseIndex.LEFT_EYE to at(shoulderX - halfEye, eyeY),
            PoseIndex.RIGHT_EYE to at(shoulderX + halfEye, eyeY),
            PoseIndex.LEFT_EAR to at(shoulderX - halfEye * 1.4f, eyeY + 0.02f * frame.height),
            PoseIndex.RIGHT_EAR to at(shoulderX + halfEye * 1.4f, eyeY + 0.02f * frame.height),
            PoseIndex.LEFT_SHOULDER to at(shoulderX - halfShoulder, shoulderY),
            PoseIndex.RIGHT_SHOULDER to at(shoulderX + halfShoulder, shoulderY),
            PoseIndex.LEFT_HIP to at(frame.cx - halfShoulder, hipY),
            PoseIndex.RIGHT_HIP to at(frame.cx + halfShoulder, hipY),
            PoseIndex.LEFT_KNEE to at(frame.cx - halfShoulder, hipY + 0.20f * frame.height),
            PoseIndex.RIGHT_KNEE to at(frame.cx + halfShoulder, hipY + 0.20f * frame.height),
            PoseIndex.LEFT_ANKLE to at(frame.cx - halfShoulder, frame.cy + 0.45f * frame.height),
            PoseIndex.RIGHT_ANKLE to at(frame.cx + halfShoulder, frame.cy + 0.45f * frame.height),
            PoseIndex.RIGHT_WRIST to at(frame.cx + halfShoulder, frame.cy + 0.30f * frame.height)
        )
    }

    /** 像素空间绕画面中心刚体旋转 [rollDeg]，再归一化成 MediaPipe 的坐标。 */
    private fun rolled(frame: Frame, rollDeg: Float, leanDeg: Float = 0f): List<PosePoint> {
        val roll = Math.toRadians(rollDeg.toDouble())
        val cosRoll = cos(roll).toFloat()
        val sinRoll = sin(roll).toFloat()
        val pixels = standPixels(frame, leanDeg)
        return MutableList(PoseIndex.LANDMARK_COUNT) { PosePoint(0.5f, 0.5f) }.apply {
            pixels.forEach { (index, xy) ->
                val dx = xy.first - frame.cx
                val dy = xy.second - frame.cy
                set(
                    index,
                    PosePoint(
                        x = (frame.cx + dx * cosRoll - dy * sinRoll) / frame.width,
                        y = (frame.cy + dx * sinRoll + dy * cosRoll) / frame.height
                    )
                )
            }
        }
    }

    /** 抬左腿：踝上移 [liftRatio] 个画面高度（归一化 y 单位）。 */
    private fun liftedLeftLeg(base: List<PosePoint>, liftRatio: Float = 0.30f): List<PosePoint> = base.mapIndexed { index, point ->
        if (index == PoseIndex.LEFT_ANKLE) point.copy(y = point.y - liftRatio) else point
    }

    private fun marchRep(
        analyzer: MarchAnalyzer,
        aspect: Float,
        upright: List<PosePoint>,
        lifted: List<PosePoint>
    ): AnalysisUpdate {
        analyzer.update(upright, timestampMs = 0, aspectRatio = aspect)
        analyzer.update(lifted, timestampMs = 1_000, aspectRatio = aspect)
        return analyzer.update(upright, timestampMs = 2_000, aspectRatio = aspect)
    }

    /** 像素空间（y 归一化）距离；归一化空间里的欧氏距离是各向异性的，不能直接比。 */
    private fun isometricDistance(frame: Frame, first: PosePoint, second: PosePoint): Float {
        val dx = (first.x - second.x) * frame.aspect
        val dy = first.y - second.y
        return sqrt(dx * dx + dy * dy)
    }

    // ---------- 横滚角测量：任何宽高比都必须等于真实横滚 ----------

    @Test
    fun cameraRollEqualsTrueRollInEveryAspectRatio() {
        frames.forEach { frame ->
            for (trueRoll in listOf(1f, 5f, 10f, 15f, 19f)) {
                val roll = PoseGeometry.cameraRollRadians(rolled(frame, -trueRoll), frame.aspect)
                val degrees = Math.toDegrees(roll!!.toDouble()).toFloat()
                assertEquals("${frame.width}x${frame.height} roll=$trueRoll", trueRoll, abs(degrees), 0.01f)
            }
        }
    }

    @Test
    fun cameraRollReturnsNullForUprightFrame() {
        frames.forEach { frame ->
            assertNull(PoseGeometry.cameraRollRadians(rolled(frame, 0f), frame.aspect))
        }
    }

    // ---------- 缺陷复现：横滚会被读成侧倾 ----------

    @Test
    fun rollIsMeasuredAsLateralLeanBeforeCorrection() {
        frames.forEach { frame ->
            val lean = PoseGeometry.lateralLeanDegrees(rolled(frame, -15f), frame.aspect)!!
            // 回正前，抬起来的 15° 横滚就是测到的 15° 侧倾。
            assertEquals("${frame.width}x${frame.height}", 15f, lean, 0.05f)
        }
    }

    @Test
    fun ignoringAspectRatioSkewsTheAngleOnNonSquareFrames() {
        // 回归护栏：忽略宽高比时角度是 atan(tan(roll)·H/W)，只有正方形帧才碰巧正确。
        val trueRoll = 15f
        for (frame in listOf(portrait, landscape)) {
            val lean = PoseGeometry.lateralLeanDegrees(rolled(frame, -trueRoll), 1f)!!
            val skew = abs(lean - trueRoll)
            assertTrue(
                "${frame.width}x${frame.height} 应偏 ${tan(Math.toRadians(trueRoll.toDouble()))} 倍，实测 $lean",
                skew > 3f
            )
        }
        assertEquals(
            15f,
            PoseGeometry.lateralLeanDegrees(rolled(square, -trueRoll), 1f)!!,
            0.05f
        )
    }

    // ---------- 校正：回正后侧倾归零，计数恢复 ----------

    @Test
    fun derotatedMarchCountsAndClearsLateralLean() {
        frames.forEach { frame ->
            val analyzer = MarchAnalyzer(calibrationReps = 0, maxLateralLean = 12f)
            val upright = rolled(frame, -15f)
            val lifted = liftedLeftLeg(upright)
            val roll = PoseGeometry.cameraRollRadians(upright, frame.aspect)!!

            val update = marchRep(
                analyzer,
                frame.aspect,
                PoseGeometry.derotate(upright, roll, frame.aspect),
                PoseGeometry.derotate(lifted, roll, frame.aspect)
            )

            assertEquals("${frame.width}x${frame.height}", 1, update.count)
            assertTrue(update.lastRep!!.valid)
            assertFalse(update.lastRep!!.issues.contains(RepIssue.LATERAL_LEAN))
        }
    }

    @Test
    fun rolledMarchIsReportedAsLateralLeanWithoutCorrection() {
        // 横屏 15° 横滚 → 未校正侧倾 15° > 12° 阈值，患者被记为不达标。
        val analyzer = MarchAnalyzer(calibrationReps = 0, maxLateralLean = 12f)
        val upright = rolled(landscape, -15f)

        val update = marchRep(analyzer, landscape.aspect, upright, liftedLeftLeg(upright))

        assertEquals(1, update.count)
        assertFalse(update.lastRep!!.valid)
        assertTrue(update.lastRep!!.issues.contains(RepIssue.LATERAL_LEAN))
    }

    @Test
    fun derotateRestoresTheTrueTrunkLean() {
        // 真实侧倾 10° + 机位横滚 20°，回正后必须回到 10°，与宽高比无关。
        frames.forEach { frame ->
            val points = rolled(frame, -19f, leanDeg = 10f)
            val roll = PoseGeometry.cameraRollRadians(points, frame.aspect)!!
            val corrected = PoseGeometry.derotate(points, roll, frame.aspect)
            assertEquals(
                "${frame.width}x${frame.height}",
                10f,
                PoseGeometry.lateralLeanDegrees(corrected, frame.aspect)!!,
                0.05f
            )
        }
    }

    // ---------- 参考线与边界 ----------

    @Test
    fun cameraRollIgnoresReferencePointOrder() {
        frames.forEach { frame ->
            val rolledPoints = rolled(frame, -10f)
            val swapped = rolledPoints.mapIndexed { index, point ->
                when (index) {
                    PoseIndex.LEFT_EYE -> rolledPoints[PoseIndex.RIGHT_EYE]
                    PoseIndex.RIGHT_EYE -> rolledPoints[PoseIndex.LEFT_EYE]
                    else -> point
                }
            }
            assertEquals(
                PoseGeometry.cameraRollRadians(rolledPoints, frame.aspect)!!,
                PoseGeometry.cameraRollRadians(swapped, frame.aspect)!!,
                1e-5f
            )
        }
    }

    @Test
    fun cameraRollReturnsNullWhenReferencePointsAreHidden() {
        val hidden = rolled(portrait, -10f).mapIndexed { index, point ->
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
        assertNull(PoseGeometry.cameraRollRadians(hidden, portrait.aspect))
    }

    @Test
    fun cameraRollFallsBackToEarsWhenEyesAreHidden() {
        val hiddenEyes = rolled(portrait, -10f).mapIndexed { index, point ->
            if (index == PoseIndex.LEFT_EYE || index == PoseIndex.RIGHT_EYE) {
                point.copy(visibility = 0f)
            } else {
                point
            }
        }
        val roll = PoseGeometry.cameraRollRadians(hiddenEyes, portrait.aspect)
        assertEquals(10f, abs(Math.toDegrees(roll!!.toDouble()).toFloat()), 0.01f)
    }

    @Test
    fun cameraRollReturnsNullBeyondUpperLimit() {
        frames.forEach { frame ->
            assertNull(PoseGeometry.cameraRollRadians(rolled(frame, 35f), frame.aspect))
        }
    }

    // ---------- 校正的等距性质 ----------

    @Test
    fun derotatePreservesPixelDistanceZAndVisibility() {
        frames.forEach { frame ->
            val rolledPoints = rolled(frame, -10f)
            val roll = PoseGeometry.cameraRollRadians(rolledPoints, frame.aspect)!!
            val corrected = PoseGeometry.derotate(rolledPoints, roll, frame.aspect)

            assertEquals(
                isometricDistance(
                    frame,
                    rolledPoints[PoseIndex.LEFT_SHOULDER],
                    rolledPoints[PoseIndex.LEFT_HIP]
                ),
                isometricDistance(
                    frame,
                    corrected[PoseIndex.LEFT_SHOULDER],
                    corrected[PoseIndex.LEFT_HIP]
                ),
                1e-4f
            )
            rolledPoints.forEachIndexed { index, point ->
                assertEquals(point.z, corrected[index].z, 0f)
                assertEquals(point.visibility, corrected[index].visibility, 0f)
            }
        }
    }
}
