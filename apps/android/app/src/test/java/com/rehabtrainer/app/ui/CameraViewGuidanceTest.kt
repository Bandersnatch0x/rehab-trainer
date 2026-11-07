package com.rehabtrainer.app.ui

import com.rehabtrainer.app.core.pose.CameraView
import com.rehabtrainer.app.core.pose.ExerciseKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 机位引导：矢状面动作在正面 2D 里测不出幅度（票 0021 的限制），
 * 所以要按动作类型给出站位提示；正面动作保持原样。
 */
class CameraViewGuidanceTest {

    @Test
    fun `矢状面动作要求侧面机位`() {
        assertEquals(CameraView.SAGITTAL, ExerciseKind.MARCH.cameraView)
        assertEquals(CameraView.SAGITTAL, ExerciseKind.SEATED_LEG_RAISE.cameraView)
    }

    @Test
    fun `蹲起取 45 度折中`() {
        assertEquals(CameraView.FORTY_FIVE, ExerciseKind.SQUAT.cameraView)
    }

    @Test
    fun `正面动作不给额外引导`() {
        listOf(ExerciseKind.ARM_RAISE, ExerciseKind.POSTURE, ExerciseKind.STRETCH).forEach {
            assertEquals(it.name, CameraView.FRONTAL, it.cameraView)
            assertNull(it.name, it.cameraView.hint)
        }
    }

    @Test
    fun `非正面机位的提示短到能放进状态行`() {
        val guided = ExerciseKind.entries.filter { it.cameraView != CameraView.FRONTAL }
        assertEquals(3, guided.size)
        guided.forEach {
            val hint = it.cameraView.hint
            assertNotNull("${it.name} 缺少机位提示", hint)
            assertTrue("${it.name} 的提示过长：$hint", hint!!.length <= 16)
        }
    }

    @Test
    fun `标签启发式把坐位抬腿和微蹲指向对应机位`() {
        assertEquals(
            ExerciseKind.SEATED_LEG_RAISE,
            kindFor("strength-maintenance", "坐位抬腿")
        )
        assertEquals(ExerciseKind.SQUAT, kindFor("strength-maintenance", "靠墙微蹲"))
        assertEquals(ExerciseKind.MARCH, kindFor("rhythmic-gait", "原地踏步"))
    }
}
