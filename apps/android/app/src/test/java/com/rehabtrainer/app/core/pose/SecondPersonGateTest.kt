package com.rehabtrainer.app.core.pose

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecondPersonGateTest {

    /** [visible] 个关键点可见，其余不可见。 */
    private fun pose(visible: Int): List<PosePoint> =
        List(PoseIndex.LANDMARK_COUNT) { index ->
            PosePoint(0.5f, 0.5f, 0f, visibility = if (index < visible) 1f else 0f)
        }

    private fun poseWithVisibility(visibility: Float): List<PosePoint> =
        List(PoseIndex.LANDMARK_COUNT) { PosePoint(0.5f, 0.5f, 0f, visibility) }

    @Test
    fun emptyFrameHasNoSecondPerson() {
        assertFalse(SecondPersonGate.isPresent(emptyList()))
    }

    @Test
    fun singlePoseHasNoSecondPerson() {
        assertFalse(SecondPersonGate.isPresent(listOf(pose(PoseIndex.LANDMARK_COUNT))))
    }

    @Test
    fun credibleSecondPoseBlocksCounting() {
        // 第二人露出头肩髋就够判定了，不需要全身入镜。
        assertTrue(
            SecondPersonGate.isPresent(
                listOf(pose(PoseIndex.LANDMARK_COUNT), pose(SecondPersonGate.MIN_VISIBLE_LANDMARKS))
            )
        )
    }

    @Test
    fun ghostSecondPoseIsIgnored() {
        // 检测置信度放宽到 0.35 后必然有幽灵骨架，可见点不足不能打断训练。
        assertFalse(
            SecondPersonGate.isPresent(
                listOf(
                    pose(PoseIndex.LANDMARK_COUNT),
                    pose(SecondPersonGate.MIN_VISIBLE_LANDMARKS - 1)
                )
            )
        )
    }

    @Test
    fun secondPoseWithNoVisibleLandmarksIsIgnored() {
        assertFalse(SecondPersonGate.isPresent(listOf(pose(PoseIndex.LANDMARK_COUNT), pose(0))))
    }

    @Test
    fun anyAdditionalPoseTriggers() {
        assertTrue(
            SecondPersonGate.isPresent(
                listOf(pose(PoseIndex.LANDMARK_COUNT), pose(0), pose(PoseIndex.LANDMARK_COUNT))
            )
        )
    }

    @Test
    fun visibilityThresholdIsInclusive() {
        assertTrue(
            SecondPersonGate.isPresent(
                listOf(
                    pose(PoseIndex.LANDMARK_COUNT),
                    poseWithVisibility(SecondPersonGate.VISIBILITY_THRESHOLD)
                )
            )
        )
    }

    @Test
    fun belowVisibilityThresholdDoesNotCount() {
        assertFalse(
            SecondPersonGate.isPresent(
                listOf(
                    pose(PoseIndex.LANDMARK_COUNT),
                    poseWithVisibility(SecondPersonGate.VISIBILITY_THRESHOLD - 0.01f)
                )
            )
        )
    }

    @Test
    fun missingVisibilityFailsClosed() {
        assertFalse(
            SecondPersonGate.isPresent(
                listOf(pose(PoseIndex.LANDMARK_COUNT), poseWithVisibility(Float.NaN))
            )
        )
    }

    @Test
    fun trackedTargetVisibilityDoesNotMatter() {
        // 第 0 副是当前跟踪目标，它不可见时走的是「未检出」分支，不该被当成第二人。
        assertFalse(SecondPersonGate.isPresent(listOf(pose(0))))
    }
}
